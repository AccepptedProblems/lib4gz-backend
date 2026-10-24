package com.example.lib4gz

import com.example.lib4gz.auth.job.RegistrationCleanupJob
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Boots the whole context: bean wiring, `security.json`, the mail provider, the scheduler.
 *
 * It deliberately touches no database. This test reads the same `application.yaml` (and `.env`) the
 * app does, so with Flyway enabled `./gradlew test` would apply migrations to whatever database
 * those name — usually Supabase — and V2/V3 delete rows and drop a column. Schema work is opted into
 * explicitly, against a throwaway Postgres:
 *
 * ```
 * docker run -d --name pg -e POSTGRES_PASSWORD=pg -p 55432:5432 postgres:17
 * MIGRATION_CHECK=true MIGRATION_SCHEMA_CHECK=validate \
 *   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/postgres \
 *   SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=pg ./gradlew test --rerun-tasks
 * ```
 *
 * That applies every migration and then lets Hibernate validate the entities against the result,
 * which is the check to run before shipping a new migration.
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=\${MIGRATION_CHECK:false}",
        "spring.jpa.hibernate.ddl-auto=\${MIGRATION_SCHEMA_CHECK:none}",
        // Explicit dialect in application.yaml, so no connection is needed to discover one.
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    ]
)
class Lib4gzApplicationTests {

	// The real job sweeps at startup: it would open a connection and delete expired rows in that database.
	@MockitoBean
	private lateinit var registrationCleanupJob: RegistrationCleanupJob

	@Test
	fun contextLoads() {
	}

}
