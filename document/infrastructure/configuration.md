### Overview
Application configuration files and build setup.

### application.yaml (src/main/resources/)
```yaml
server:
  servlet:
    context-path: /api
  error:
    include-message: always

spring:
  application:
    name: Z_Lib4gz_Backend

  datasource:
    url: jdbc:postgresql://db.${SUPABASE_PROJECT_REF}.supabase.co:5432/postgres?sslmode=require
    username: postgres
    password: ${SUPABASE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false

  # Gmail SMTP with an App Password. Leave MAIL_USERNAME unset locally: mail is then logged, not sent.
  # Cloud Run blocks outbound port 25; 587 (STARTTLS) is open.
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

application:
  security:
    jwt:
      secret-key: ${JWT_SECRET_KEY:404E635266556A586E3272357F4428472B4B6250645367566B5970}
      expiration: 86400000 # a day
      refresh-token:
        expiration: 604800000 # 7 days
    google:
      # OAuth Web client id (public). Google ID tokens must carry it as their audience.
      client-id: ${GOOGLE_CLIENT_ID:}
  mail:
    from: ${MAIL_USERNAME:no-reply@lib4gz.local}
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
| `JWT_SECRET_KEY` | no (dev default) | JWT signing key; also the pepper for one-time code hashes |
| `MAIL_USERNAME` | no | Gmail address that sends one-time codes (`spring.mail.username` and `application.mail.from`). Unset or blank → codes are logged, not sent |
| `MAIL_PASSWORD` | no | 16-character Gmail App Password (requires 2-Step Verification on that account) |
| `GOOGLE_CLIENT_ID` | for Google sign-in | Expected `aud` of Google ID tokens. Public value: `7006272624-f8n2024mnhstsk6cmtmfss1a1j4ffaip.apps.googleusercontent.com`. Unset or blank → every Google token is rejected with 401 |

On Cloud Run all six come from Google Secret Manager (`deploy/deploy.sh` `--set-secrets`). `deploy/setup-secrets.sh` only adds versions, so create a new secret first, e.g. `gcloud secrets create MAIL_USERNAME --replication-policy=automatic --project=library4gz`.

### Mail (`common/mail/EmailSender.kt`, `common/config/mail/MailConfig.kt`)
- `interface EmailSender { fun send(to: String, subject: String, body: String) }`
- `MailConfig` declares one `@Bean emailSender(mailSender: JavaMailSender, spring.mail.username, application.mail.from)`:
  - `spring.mail.username` blank → `LoggingEmailSender`: logs `To`, `Subject`, and body at INFO. With `./gradlew bootRun` and no `MAIL_USERNAME`, the one-time codes appear in the console.
  - otherwise → `SmtpEmailSender(JavaMailSender, from)`: sends a `SimpleMailMessage`; a `MailException` is logged and rethrown as `ResponseStatusException(503, "Could not send email")`. It is sent inside the OTP transaction, so a failure also rolls back the stored code (and a pending email change).
- Gmail requires `from` to be the authenticating account; `application.mail.from` therefore defaults to `MAIL_USERNAME`.
- `JavaMailSender` connects only on send, so the application context starts without mail credentials.

### One-time codes and registration (`application.otp.*`, `application.registration.*`)

| Property | Default | Meaning |
|----------|---------|---------|
| `application.otp.ttl-ms` | `600000` (10 min) | Code lifetime (`OtpServiceImpl`) |
| `application.otp.max-attempts` | `5` | Wrong guesses before the code is invalidated |
| `application.otp.resend-cooldown-ms` | `60000` (60 s) | Minimum time between two codes for the same email and purpose; `OtpSentResponse.cooldownSeconds` = this / 1000 |
| `application.registration.pending-ttl-ms` | `900000` (15 min) | Lifetime of a `pending_registrations` row (`RegistrationServiceImpl`, `GoogleAuthServiceImpl`) |

Expired `email_otps` and `pending_registrations` rows are removed by `RegistrationCleanupJob` every 10 minutes (hard-coded `fixedDelay = 600_000`).

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
      ddl-auto: update
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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // NimbusJwtDecoder, used to verify Google ID tokens
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
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
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling   // RegistrationCleanupJob
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
