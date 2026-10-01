### Overview
Application configuration files and build setup.

### application.yaml (src/main/resources/)
```yaml
server:
  servlet:
    context-path: /api
  error:
    include-message: never   # exception text never leaves the server; GlobalExceptionHandler logs it

spring:
  application:
    name: Z_Lib4gz_Backend
  # Local secrets (git-ignored); see .env.example. Real environment variables take precedence.
  config:
    import: optional:file:.env[.properties]

  datasource:
    url: jdbc:postgresql://db.${SUPABASE_PROJECT_REF}.supabase.co:5432/postgres?sslmode=require
    username: postgres
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  # Flyway owns the schema (src/main/resources/db/migration). It runs before Hibernate starts.
  flyway:
    baseline-on-migrate: true
    baseline-version: 1

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false


application:
  security:
    jwt:
      secret-key: ${JWT_SECRET_KEY}
      expiration: 86400000 # a day
      refresh-token:
        expiration: 604800000 # 7 days
    google:
      # OAuth Web client id (public). Google ID tokens must carry it as their audience.
      client-id: ${GOOGLE_CLIENT_ID:}
  # Which provider sends mail, and what every provider needs. Only the credentials live under the
  # provider's own name. Switching provider is a new secret, a new MAIL_PROVIDER, and a redeploy.
  mail:
    provider: ${MAIL_PROVIDER:log}     # log | brevo. Unset means codes are printed to the console.
    from:
      name: ${MAIL_FROM_NAME:Lib4GZ}
      address: ${MAIL_FROM_ADDRESS:}   # must be a sender verified at the provider
    timeout-ms: 10000                  # connect and response timeout for every provider
    brevo:
      api-key: ${BREVO_API_KEY:}
  registration:
    pending-ttl-ms: 900000      # 15 min
  otp:
    ttl-ms: 600000              # 10 min
    max-attempts: 5
    resend-cooldown-ms: 60000
```

### Environment variables

| Variable | Required | Used for |
|----------|----------|----------|
| `SUPABASE_PROJECT_REF` | yes | Datasource host |
| `SUPABASE_DB_PASSWORD` | yes | Datasource password |
| `JWT_SECRET_KEY` | yes | JWT signing key; also the pepper for one-time code hashes. No default any more: unset fails the boot |
| `MAIL_PROVIDER` | no (defaults to `log`) | Which `EmailSender` bean exists: `log` or `brevo`. `log` writes the mail, one-time code and all, to the application log and sends nothing |
| `MAIL_FROM_NAME` | no (defaults to `Lib4GZ`) | Display name of the sender |
| `MAIL_FROM_ADDRESS` | for any sending provider | Sender address; must be verified at the provider. `library4gz.com` is authenticated at Brevo (Brevo code, two DKIM CNAMEs, DMARC), so any address on it is accepted; `deploy/deploy.sh` defaults to `no-reply@library4gz.com`. Blank with `MAIL_PROVIDER=brevo` fails the boot |
| `BREVO_API_KEY` | with `MAIL_PROVIDER=brevo` | Brevo API key (its console, SMTP & API → API keys), sent as the `api-key` header. Not the SMTP key. Blank with `MAIL_PROVIDER=brevo` fails the boot |
| `GOOGLE_CLIENT_ID` | for Google sign-in | Expected `aud` of Google ID tokens. Public value: `7006272624-f8n2024mnhstsk6cmtmfss1a1j4ffaip.apps.googleusercontent.com`. Unset or blank → every Google token is rejected with 401 |

On Cloud Run only the five secrets come from Google Secret Manager (`deploy/deploy.sh` `--set-secrets`): `SUPABASE_PROJECT_REF`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET_KEY`, `BREVO_API_KEY`, `GOOGLE_CLIENT_ID`. `MAIL_PROVIDER`, `MAIL_FROM_NAME` and `MAIL_FROM_ADDRESS` hold nothing secret and are passed as plain environment variables. `deploy/setup-secrets.sh` only adds versions, so create a new secret first, e.g. `gcloud secrets create BREVO_API_KEY --replication-policy=automatic --project=library4gz`.

### Mail (`common/notification/`)

Two layers, one folder. Nothing outside `common/notification/` talks to a provider.

**Wording — `NotifierService.kt`.** `NotifierService` owns what every message says. It has one method per business event, never a generic `notify()`:

| Method | Subject |
|--------|---------|
| `sendRegistrationOtp(email, code, ttlMinutes)` | `Your Lib4GZ sign-up code` |
| `sendEmailChanged(oldEmail, newEmail)` | `Your Lib4GZ email was changed` |

The code body is the subject line with the code, then "The code expires in N minutes." `OtpService` no longer sends anything — `issue(email, purpose)` stores the hash and returns the plain code, the caller hands it to the notifier, and `OtpService.ttlMinutes` supplies the N.

**Transport — `email/EmailSender.kt`.** `interface EmailSender { fun send(to: String, subject: String, body: String) }`, and nothing else: it never learns who asked or why. A provider that cannot deliver throws `ResponseStatusException(503, "Could not send email")`.

The two messages are sent differently on purpose. `sendRegistrationOtp` is called by `RegistrationServiceImpl.startEmail` **inside** its `transactionTemplate.execute` block, so a delivery failure rolls the stored code back: a code nobody received must not exist. `sendEmailChanged` is called by `GoogleAuthServiceImpl.connect` **after** the commit and wrapped in a `try/catch` that only logs: the account change already happened, and an unreachable old address must not undo it.

**Choosing a provider — `application.mail.provider`.** Each provider is a `@Component` guarded by a condition on that property, so exactly one `EmailSender` bean is ever in the context and no `@Qualifier` is needed anywhere. Switching provider is a configuration change and a redeploy, not a code edit. A real provider opts in by name with `@ConditionalOnProperty(havingValue = "<name>")`. `LoggingEmailSender` uses `@ConditionalOnExpression` instead, so that a **blank** `MAIL_PROVIDER` counts as `log`: `application.yaml` always defines the property, so `@ConditionalOnProperty`'s `matchIfMissing` could never fire, and a blank value would otherwise match no provider and leave the context with no `EmailSender`.

| `application.mail.provider` | Class | Behaviour |
|---|---|---|
| `log` (also when `MAIL_PROVIDER` is unset or blank) | `email/LoggingEmailSender.kt` | Logs `To`, `Subject` and the body at INFO, plus one WARN at startup. With `./gradlew bootRun` and no `MAIL_PROVIDER`, the one-time codes appear in the console |
| `brevo` | `email/brevo/BrevoEmailSender.kt` | Sends over Brevo's HTTP API (below) |

`MailProvider` (`email/MailProperties.kt`) also names `RESEND` and `GMAIL`. Neither has a class or a YAML block yet; they are placeholders, and selecting one leaves the context with no `EmailSender`. The enum exists so that a typo such as `MAIL_PROVIDER=brvo` fails at property binding, naming the property and the bad value, instead of surfacing as "no bean of type EmailSender".

**Binding and validation.** `MailProperties` (`application.mail`) and `BrevoProperties` (`application.mail.brevo`) are registered by `@ConfigurationPropertiesScan` on `Lib4gzApplication`; Kotlin constructor binding with default values only works for classes registered that way, never for a `@Component`. Every sending provider validates in its constructor through `MailProperties.requireConfigured(...)` and throws a single `IllegalStateException` naming every missing variable at once, e.g.

```
application.mail.provider=brevo needs: BREVO_API_KEY, MAIL_FROM_ADDRESS
```

The boot fails with that one line instead of returning a 503 on somebody's first sign-up. The `log` provider validates nothing.

**The Brevo call.** `POST https://api.brevo.com/v3/smtp/email`, header `api-key`, `Content-Type: application/json`, body `{ sender: { name, email }, to: [{ email }], subject, textContent }`. A success is `201` and its `messageId` is logged at INFO — that is what to search for in the Brevo dashboard. Any other status, connection error or timeout is logged at ERROR with the status and Brevo's own `message` field (never the key) and rethrown as `ResponseStatusException(503, "Could not send email")` — the same contract the OTP callers already relied on, so the rollback behaviour is unchanged. `application.mail.timeout-ms` (10 s) is applied as a reactor-netty connect timeout, a response timeout, and a hard deadline on the blocking call, so a stuck provider cannot hold the OTP transaction open.

There is no SMTP anywhere any more: `spring.mail`, `JavaMailSender` and the `spring-boot-starter-mail` dependency are all gone.

### One-time codes and registration (`application.otp.*`, `application.registration.*`)

| Property | Default | Meaning |
|----------|---------|---------|
| `application.otp.ttl-ms` | `600000` (10 min) | Code lifetime (`OtpServiceImpl`) |
| `application.otp.max-attempts` | `5` | Wrong guesses before the code is invalidated |
| `application.otp.resend-cooldown-ms` | `60000` (60 s) | Minimum time between two codes for the same email and purpose; `OtpSentResponse.cooldownSeconds` = this / 1000 |
| `application.registration.pending-ttl-ms` | `900000` (15 min) | Lifetime of a `pending_registrations` row (`RegistrationServiceImpl`, `GoogleAuthServiceImpl`) |

Expired `email_otps` and `pending_registrations` rows are removed by `RegistrationCleanupJob` every 10 minutes (hard-coded `fixedDelay = 600_000`).

#### History of `OtpPurpose` values

`OtpPurpose.EMAIL_CHANGE` was renamed `EMAIL_VERIFICATION` (V2), and `EMAIL_VERIFICATION` was removed
again (V3) when the profile stopped confirming emails by code: the email is now the connected Google
account's address (`POST /v1/auth/google:connect`). Only `REGISTER` issues codes today; `LOGIN` and
`PASSWORD_RESET` are reserved.

The value is stored as text in `email_otps.purpose`, guarded by the check constraint
`email_otps_purpose_check` that lists the values. Each migration deletes the rows on the value it
removes (codes live 10 minutes) and recreates the constraint. They run by themselves on the first boot
of the release; no manual SQL is needed, and both are safe on a database where the constraint was
already changed by hand. `V3__google_connect.sql` also drops `users.pending_email`.

### Schema migrations (`spring.flyway.*`, `db/migration/`)

Flyway owns the schema. On boot it applies every pending `src/main/resources/db/migration/V<n>__<what>.sql`
in order and records each in the `flyway_schema_history` table, then Hibernate starts with
`ddl-auto: validate` and refuses to boot if an entity no longer matches its table. Several Cloud Run
instances starting at once are safe: Flyway takes a Postgres lock, so only one applies migrations.

- **`V1__baseline.sql`** is the schema `ddl-auto: update` had produced when Flyway was introduced
  (identical columns and constraint names). With `baseline-on-migrate: true`, a database that already
  has tables but no history (Supabase) is recorded as version 1 without running it; an empty database
  runs it.
- **Every entity change needs a new migration** in the same change: a column added, renamed, retyped
  or dropped, and also **any value added to or renamed in an `@Enumerated(EnumType.STRING)` enum** —
  each such column has a `<table>_<column>_check` constraint listing the allowed values, and `validate`
  does not compare it. V2 is the pattern to copy.
- Never edit a migration that has been deployed; Flyway checksums them and refuses to start. Add a
  new one instead.
- `./gradlew test` touches no database. `Lib4gzApplicationTests` pins `spring.flyway.enabled=false`,
  `ddl-auto=none` and `hibernate.boot.allow_jdbc_metadata_access=false`, so the context test checks
  wiring only. Without that it would read the same `.env` the app does and apply the migrations —
  including the destructive parts of V2 and V3 — to Supabase during an ordinary build.
- Checking a migration is opt-in, against a throwaway Postgres:
  `MIGRATION_CHECK=true MIGRATION_SCHEMA_CHECK=validate SPRING_DATASOURCE_URL=… ./gradlew test --rerun-tasks`.
  Flyway applies everything pending and Hibernate then validates the entities against the result.

### Google sign-in (`application.security.google.client-id`)
Read by `NimbusGoogleTokenVerifier` (`common/config/security/GoogleTokenVerifier.kt`). See [security.md](security.md#google-id-token-verification). The OAuth client must be a **Web application** client with the frontend origins listed as Authorized JavaScript origins; the client secret is not used.

### application-dev.yaml (src/main/resources/)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydatabase
    username: myuser
    password: mypassword
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### build.gradle.kts
```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // NimbusJwtDecoder, used to verify Google ID tokens
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // https://mvnrepository.com/artifact/org.springframework.data/spring-data-jpa
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    // Schema migrations: src/main/resources/db/migration
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // TypeID: UUIDv7 generation for type-prefixed IDs
    implementation("com.github.f4b6a3:uuid-creator:6.0.0")

    // https://mvnrepository.com/artifact/org.projectlombok/lombok
    implementation("org.projectlombok:lombok:1.18.42")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### Application Entry Point (Lib4gzApplication.kt)
```kotlin
package com.example.lib4gz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan   // MailProperties, BrevoProperties
class Lib4gzApplication

fun main(args: Array<String>) {
    runApplication<Lib4gzApplication>(*args)
}
```

### Utilities

#### IdGenerator (`common/utils/IdGenerator.kt`)
- Package: `com.example.lib4gz.common.utils`
- class IdGenerator with companion object
- fun generate(prefix: String = "lb"): String - generates Base64-encoded UUID with prefix
- NOTE: This utility exists but is NOT used anywhere in the current codebase. All entities use UUID directly.

### Development Setup
1. Start PostgreSQL:
```bash
docker run -d --name lib4gz-postgres \
  -e POSTGRES_DB=mydatabase \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_PASSWORD=mypassword \
  -p 5432:5432 \
  postgres:15-alpine
```

2. Run application:
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

3. Access API at: http://localhost:8080/api
