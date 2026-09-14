package com.example.lib4gz.common.mail

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.server.ResponseStatusException

interface EmailSender {
    fun send(to: String, subject: String, body: String)
}

class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val from: String
) : EmailSender {
    private val logger = LoggerFactory.getLogger(SmtpEmailSender::class.java)

    override fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        try {
            mailSender.send(message)
        } catch (e: MailException) {
            logger.error("Failed to send email to {}", to, e)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send email")
        }
    }
}

/** Local development fallback when no mail credentials are configured: the mail goes to the log. */
class LoggingEmailSender : EmailSender {
    private val logger = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    override fun send(to: String, subject: String, body: String) {
        logger.info("Email (not sent, no MAIL_USERNAME configured)\nTo: {}\nSubject: {}\n{}", to, subject, body)
    }
}
