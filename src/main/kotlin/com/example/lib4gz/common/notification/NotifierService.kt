package com.example.lib4gz.common.notification

import com.example.lib4gz.common.notification.email.EmailSender
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * How the app tells a person something, and what it says. Callers name the business event;
 * the wording and the channel are decided here and nowhere else.
 *
 * Blocking on purpose, like [EmailSender]. A code is sent inside the caller's
 * `Mono.fromCallable` + `TransactionTemplate` block, so a delivery failure rolls their writes back.
 * A notice about something that already happened is sent after the commit, and the caller decides
 * that a failure only gets logged.
 */
interface NotifierService {
    /** The code that proves an address before a sign-up may continue. */
    fun sendRegistrationOtp(email: String, code: String, ttlMinutes: Long)

    /** Tells the address an account no longer uses that it was replaced. */
    fun sendEmailChanged(oldEmail: String, newEmail: String)
}

@Service
class NotifierServiceImpl(
    private val emailSender: EmailSender
) : NotifierService {

    override fun sendRegistrationOtp(email: String, code: String, ttlMinutes: Long) {
        val subject = "Your Lib4GZ sign-up code"
        emailSender.send(
            email,
            subject,
            """
            |$subject: $code
            |
            |The code expires in $ttlMinutes minutes. If you did not ask for it, ignore this email.
            """.trimMargin()
        )
    }

    // No "if this was not you" line yet: there is no password reset to point people to.
    override fun sendEmailChanged(oldEmail: String, newEmail: String) =
        emailSender.send(
            oldEmail,
            "Your Lib4GZ email was changed",
            """
            |The email on your Lib4GZ account changed from $oldEmail to $newEmail on ${LocalDate.now()}.
            |
            |If this was you, there is nothing to do.
            """.trimMargin()
        )
}
