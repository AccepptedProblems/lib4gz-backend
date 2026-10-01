package com.example.lib4gz.common.notification

import com.example.lib4gz.common.notification.email.EmailSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/** The wording of every message the app sends lives in one class, so it is asserted in one test. */
class NotifierServiceImplTest {

    private val emailSender = mock(EmailSender::class.java)
    private val notifier = NotifierServiceImpl(emailSender)

    @Test
    fun `sendRegistrationOtp mails the sign-up code with its expiry`() {
        notifier.sendRegistrationOtp("new@x.com", "123456", 10)

        val (to, subject, body) = sent()
        assertEquals("new@x.com", to)
        assertEquals("Your Lib4GZ sign-up code", subject)
        assertTrue(body.contains("123456"), body)
        assertTrue(body.contains("expires in 10 minutes"), body)
        assertTrue(body.contains("If you did not ask for it, ignore this email."), body)
    }

    @Test
    fun `sendEmailChanged goes to the old address and names both addresses`() {
        notifier.sendEmailChanged("old@x.com", "new@x.com")

        val (to, subject, body) = sent()
        assertEquals("old@x.com", to)
        assertEquals("Your Lib4GZ email was changed", subject)
        assertTrue(body.contains("from old@x.com to new@x.com"), body)
        assertTrue(body.contains("If this was you, there is nothing to do."), body)
        // Nowhere to send people yet: no password reset exists.
        assertFalse(body.contains("not you"), body)
    }

    /** The single `send` the notifier made, as (to, subject, body). */
    private fun sent(): Triple<String, String, String> {
        val to = ArgumentCaptor.forClass(String::class.java)
        val subject = ArgumentCaptor.forClass(String::class.java)
        val body = ArgumentCaptor.forClass(String::class.java)
        verify(emailSender).send(to.capture() ?: "", subject.capture() ?: "", body.capture() ?: "")
        return Triple(to.value, subject.value, body.value)
    }
}
