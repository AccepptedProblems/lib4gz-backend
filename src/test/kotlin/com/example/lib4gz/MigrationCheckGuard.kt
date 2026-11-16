package com.example.lib4gz

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.net.URI
import javax.sql.DataSource

/**
 * The migration check (`MIGRATION_CHECK=true`) applies every pending migration, and some delete rows.
 * This refuses to migrate unless the datasource Spring resolved, whatever set it, is on this machine.
 * Without the override `.env` names the real database.
 */
@TestConfiguration(proxyBeanMethods = false)
class MigrationCheckGuard {

    @Bean
    fun localOnlyMigration() = FlywayMigrationStrategy { flyway ->
        check(isLocal(urlOf(flyway.configuration.dataSource))) {
            "The migration check runs only against a database on localhost or 127.0.0.1, and the " +
                "datasource is elsewhere. Set SPRING_DATASOURCE_URL to a throwaway Postgres."
        }
        flyway.migrate()
    }

    companion object {
        /** The URL the pool will connect to; read from the pool's settings, so no connection is opened. */
        fun urlOf(dataSource: DataSource): String? = when (dataSource) {
            is HikariDataSource -> dataSource.jdbcUrl
            else -> dataSource.connection.use { it.metaData.url }
        }

        fun isLocal(url: String?): Boolean {
            val host = url?.removePrefix("jdbc:")?.let { runCatching { URI(it).host }.getOrNull() }
            return host == "localhost" || host == "127.0.0.1"
        }
    }
}
