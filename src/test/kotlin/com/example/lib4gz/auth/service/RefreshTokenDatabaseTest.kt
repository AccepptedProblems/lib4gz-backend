package com.example.lib4gz.auth.service

import com.example.lib4gz.MigrationCheckGuard
import com.example.lib4gz.auth.job.RefreshTokenCleanupJob
import com.example.lib4gz.auth.job.RegistrationCleanupJob
import com.example.lib4gz.auth.model.entity.RefreshToken
import com.example.lib4gz.auth.repo.RefreshTokenRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * The refresh token SQL against a real Postgres. Runs only inside the migration check, and only when
 * the resolved datasource is on localhost: it writes rows. See `Lib4gzApplicationTests`.
 */
@EnabledIfEnvironmentVariable(named = "MIGRATION_CHECK", matches = "true")
@Import(MigrationCheckGuard::class)
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=\${MIGRATION_CHECK:false}",
        "spring.jpa.hibernate.ddl-auto=\${MIGRATION_SCHEMA_CHECK:none}",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    ]
)
class RefreshTokenDatabaseTest {

    @MockitoBean
    private lateinit var registrationCleanupJob: RegistrationCleanupJob

    @MockitoBean
    private lateinit var refreshTokenCleanupJob: RefreshTokenCleanupJob

    @Autowired private lateinit var service: RefreshTokenService
    @Autowired private lateinit var repo: RefreshTokenRepo
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun onlyOnThisMachine() {
        assumeTrue(MigrationCheckGuard.isLocal(MigrationCheckGuard.urlOf(dataSource)))
    }

    private val day = 86_400_000L

    private fun newUser(): String {
        val id = "usr_r" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        jdbc.update(
            "INSERT INTO users (id, username, name, email, email_verified, status, created_at, updated_at) VALUES (?, ?, ?, ?, true, 'ACTIVE', ?, ?)",
            id, id, id, "$id@x.com", now, now
        )
        return id
    }

    private fun row(userId: String, expiresAt: Long, revokedAt: Long? = null): RefreshToken =
        repo.save(
            RefreshToken(
                userId = userId,
                tokenHash = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
                expiresAt = expiresAt,
                revokedAt = revokedAt
            )
        )

    @Test
    fun `of eight claims at once on one token exactly one gets the user id`() {
        val user = newUser()
        val token = row(user, System.currentTimeMillis() + day)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)

        val claims = (1..8).map {
            pool.submit<String?> {
                start.await()
                transactionTemplate.execute { repo.claim(token.tokenHash, System.currentTimeMillis()) }
            }
        }
        start.countDown()
        val winners = claims.mapNotNull { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(listOf(user), winners)
    }

    @Test
    fun `cleanup deletes expired rows and rows revoked over seven days ago, and keeps the rest`() {
        val user = newUser()
        val now = System.currentTimeMillis()
        val expired = row(user, now - 1)
        val revokedEightDaysAgo = row(user, now + day, revokedAt = now - 8 * day)
        val live = row(user, now + day)
        val revokedSixDaysAgo = row(user, now + day, revokedAt = now - 6 * day)

        repo.deleteExpiredOrRevokedBefore(now, now - 7 * day)

        assertEquals(false, repo.existsById(expired.id))
        assertEquals(false, repo.existsById(revokedEightDaysAgo.id))
        assertEquals(true, repo.existsById(live.id))
        assertEquals(true, repo.existsById(revokedSixDaysAgo.id))
    }

    /** Pushes every rotation of the user past the grace, as if the copy came back a minute later. */
    private fun pastTheGrace(userId: String) {
        jdbc.update("UPDATE refresh_tokens SET rotated_at = rotated_at - 60000 WHERE user_id = ? AND rotated_at IS NOT NULL", userId)
    }

    private fun rowsOf(userId: String): List<Map<String, Any?>> =
        jdbc.queryForList("SELECT * FROM refresh_tokens WHERE user_id = ? ORDER BY id", userId)

    private fun assertRefused(rawToken: String) {
        val ex = assertThrows(ResponseStatusException::class.java) { service.refresh(rawToken).block() }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun `a theft 401 commits the delete of every token of that user and no other`() {
        val alice = newUser()
        val bob = newUser()
        val first = service.issue(alice)
        val otherDevice = service.issue(alice)
        val bobs = service.issue(bob)
        val second = service.refresh(first.token).block()!!.refreshToken
        pastTheGrace(alice)
        val bobsRowsBefore = rowsOf(bob)

        assertRefused(first.token)

        assertEquals(emptyList<Map<String, Any?>>(), rowsOf(alice))
        assertEquals(1, bobsRowsBefore.size)
        assertEquals(bobsRowsBefore, rowsOf(bob))
        assertRefused(second.token)
        assertRefused(otherDevice.token)
        assertEquals(bob, service.refresh(bobs.token).block()!!.user.id)
    }

    @Test
    fun `a sign-in made after the theft response survives an older token being presented`() {
        val alice = newUser()
        val first = service.issue(alice)
        service.refresh(first.token).block()
        pastTheGrace(alice)
        assertRefused(first.token)
        val signedInAgain = service.issue(alice)

        assertRefused(first.token)

        assertEquals(alice, service.refresh(signedInAgain.token).block()!!.user.id)
    }

    @Test
    fun `a sign-out with a token a copier renewed past the grace deletes every token of that user and no other`() {
        val alice = newUser()
        val bob = newUser()
        val owners = service.issue(alice)
        service.issue(bob)
        val copiers = service.refresh(owners.token).block()!!.refreshToken
        pastTheGrace(alice)
        val bobsRowsBefore = rowsOf(bob)

        service.logout(owners.token).block()

        assertEquals(emptyList<Map<String, Any?>>(), rowsOf(alice))
        assertRefused(copiers.token)
        assertEquals(1, bobsRowsBefore.size)
        assertEquals(bobsRowsBefore, rowsOf(bob))
    }

    @Test
    fun `the old token again right after a refresh succeeds, and the newest token still refreshes`() {
        val alice = newUser()
        val first = service.issue(alice)
        val second = service.refresh(first.token).block()!!.refreshToken

        val third = service.refresh(first.token).block()!!.refreshToken

        assertNotEquals(second.token, third.token)
        assertEquals(alice, service.refresh(third.token).block()!!.user.id)
    }
}
