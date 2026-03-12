### Overview
Application configuration files and build setup.

### application.yaml (src/main/resources/)
```yaml
server:
  port: 8080
  servlet:
    context-path: /api
  error:
    include-message: always

spring:
  application:
    name: Z_Lib4gz_Backend

application:
  security:
    jwt:
      secret-key: 404E635266556A586E3272357F4428472B4B6250645367566B5970
      expiration: 86400000       # 24 hours
      refresh-token:
        expiration: 604800000    # 7 days
```

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

@SpringBootApplication
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
