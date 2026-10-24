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

	// The API reference, generated from the controllers: /api/v3/api-docs and /api/swagger-ui.html.
	// Off unless API_DOCS_ENABLED=true (see application.yaml).
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

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

// CLAUDE.md files sit beside what they describe (db/migration); they are not shipped in the jar.
tasks.processResources {
	exclude("**/*.md")
}
