package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.RefreshToken
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.RefreshTokenRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.JwtProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.Optional

class RefreshTokenServiceImplTest {

    private val repo = mock(RefreshTokenRepo::class.java)
    private val userRepo = mock(UserRepo::class.java)

    /** Set when the last transaction callback returned normally; a throw inside it leaves it false. */
    private var callbackReturned = false

    /** Like `passThroughTransactionTemplate()`, and also records whether the callback returned. */
    private val recordingTransactionTemplate = mock(TransactionTemplate::class.java).also { tx ->
        `when`(tx.execute(ArgumentMatchers.any<TransactionCallback<Any>>())).thenAnswer {
            callbackReturned = false
            val result = (it.arguments[0] as TransactionCallback<*>).doInTransaction(mock(TransactionStatus::class.java))
            callbackReturned = true
            result
        }
    }

    private val service = RefreshTokenServiceImpl(
        refreshTokenRepo = repo,
        userRepo = userRepo,
        jwtProvider = JwtProvider(TEST_JWT_SECRET, 60_000),
        transactionTemplate = recordingTransactionTemplate,
        ttlMs = 2_592_000_000,
        reuseGraceMs = 10_000
    )

    private val alice = User(id = "usr_alice", email = "alice@x.com", username = "alice", name = "Alice")
    private val raw = "the-raw-token"
    private val hash = sha256(raw)
    private val now get() = System.currentTimeMillis()

    init {
        `when`(repo.save(anyValue<RefreshToken>())).then(returnsFirstArg<RefreshToken>())
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
    }

    /** Kotlin-safe `eq()`: Mockito's returns null, which a non-null parameter rejects. */
    private fun <T> eqValue(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun storedRow(expiresAt: Long = now + 60_000, revokedAt: Long? = null, rotatedAt: Long? = null) {
        `when`(repo.findByTokenHash(hash)).thenReturn(
            RefreshToken(userId = "usr_alice", tokenHash = hash, expiresAt = expiresAt, revokedAt = revokedAt, rotatedAt = rotatedAt)
        )
    }

    private fun assertRefused() {
        val ex = assertThrows(ResponseStatusException::class.java) { service.refresh(raw).block() }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("Your session has ended. Please sign in again.", ex.reason)
    }

    private fun assertNewPair() {
        val response = service.refresh(raw).block()!!
        assertEquals("usr_alice", response.user.id)
        assertTrue(response.accessToken.token.isNotBlank())
        assertNotEquals(raw, response.refreshToken.token)
        assertTrue(response.refreshToken.expiredAt > now + 2_591_000_000)
    }

    @Test
    fun `issue stores the SHA-256 hex and never the raw value`() {
        val before = now
        val token = service.issue("usr_alice")

        val saved = ArgumentCaptor.forClass(RefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals(sha256(token.token), saved.value.tokenHash)
        assertNotEquals(token.token, saved.value.tokenHash)
        assertEquals(43, token.token.length)
        assertTrue(token.token.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals("usr_alice", saved.value.userId)
        assertEquals(token.expiredAt, saved.value.expiresAt)
        assertTrue(token.expiredAt >= before + 2_592_000_000)
    }

    @Test
    fun `issue never hands out the same token twice`() {
        assertNotEquals(service.issue("usr_alice").token, service.issue("usr_alice").token)
    }

    @Test
    fun `a live token rotates into a new pair`() {
        `when`(repo.claim(eqValue(hash), anyLong())).thenReturn("usr_alice")

        assertNewPair()

        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `a live token rotates without the row being read`() {
        `when`(repo.claim(eqValue(hash), anyLong())).thenReturn("usr_alice")

        assertNewPair()

        verify(repo, never()).findByTokenHash(anyString())
    }

    @Test
    fun `reuse 5 seconds after rotation gets a new pair and deletes nothing`() {
        storedRow(revokedAt = now - 5_000, rotatedAt = now - 5_000)

        assertNewPair()

        verify(repo, never()).deleteAllForUser(anyString())
        verify(repo, never()).revoke(anyString(), anyLong())
    }

    @Test
    fun `reuse after the grace deletes every token of the user and is 401`() {
        storedRow(revokedAt = now - 60_000, rotatedAt = now - 60_000)

        assertRefused()

        verify(repo).deleteAllForUser(eqValue("usr_alice"))
    }

    @Test
    fun `reuse of a logged-out token deletes every token of the user and is 401`() {
        storedRow(revokedAt = now - 1_000)

        assertRefused()

        verify(repo).deleteAllForUser(eqValue("usr_alice"))
    }

    @Test
    fun `the theft 401 is thrown after the transaction, so the delete commits`() {
        storedRow(revokedAt = now - 60_000, rotatedAt = now - 60_000)

        assertRefused()

        verify(repo).deleteAllForUser(eqValue("usr_alice"))
        assertTrue(callbackReturned, "the 401 was thrown inside the transaction, which rolls the delete back")
    }

    @Test
    fun `an expired token is 401 without deleting the rest`() {
        storedRow(expiresAt = now - 1)

        assertRefused()

        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `an unknown token is 401 without deleting anything`() {
        assertRefused()

        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `logout of an unknown token completes`() {
        `when`(repo.revoke(eqValue(hash), anyLong())).thenReturn(0)

        service.logout(raw).block()

        verify(repo).revoke(eqValue(hash), anyLong())
        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `logout of a live token revokes it without the row being read`() {
        `when`(repo.revoke(eqValue(hash), anyLong())).thenReturn(1)

        service.logout(raw).block()

        verify(repo).revoke(eqValue(hash), anyLong())
        verify(repo, never()).findByTokenHash(anyString())
        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `logout of a token rotated 60 seconds ago deletes every token of the user and completes`() {
        storedRow(revokedAt = now - 60_000, rotatedAt = now - 60_000)

        service.logout(raw).block()

        verify(repo).deleteAllForUser(eqValue("usr_alice"))
        assertTrue(callbackReturned, "something was thrown inside the transaction, which rolls the delete back")
    }

    @Test
    fun `logout of a token rotated 5 seconds ago deletes nothing`() {
        storedRow(revokedAt = now - 5_000, rotatedAt = now - 5_000)

        service.logout(raw).block()

        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `logout of a token already signed out deletes nothing`() {
        storedRow(revokedAt = now - 60_000)

        service.logout(raw).block()

        verify(repo, never()).deleteAllForUser(anyString())
    }

    @Test
    fun `logout of an expired token rotated long ago deletes nothing`() {
        storedRow(expiresAt = now - 1, revokedAt = now - 60_000, rotatedAt = now - 60_000)

        service.logout(raw).block()

        verify(repo, never()).deleteAllForUser(anyString())
    }
}
