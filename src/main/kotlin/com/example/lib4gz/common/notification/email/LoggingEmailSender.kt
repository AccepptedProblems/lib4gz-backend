package com.example.lib4gz.common.notification.email

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

/**
 * The provider for local runs: the mail, one-time code and all, goes to the console.
 * Also the fallback when `MAIL_PROVIDER` says nothing, so a fresh checkout runs without a mail account.
 *
 * Deliberately not `@ConditionalOnProperty`: `application.yaml` always defines
 * `application.mail.provider`, so that annotation's `matchIfMissing` could never fire, and a blank
 * `MAIL_PROVIDER` — what an untouched `.env` line gives — would match no provider at all and leave
 * the context with no `EmailSender`. Blank means log, which is what "unset" means to anyone editing
 * a `.env` file. Every real provider opts in by name, so only this fallback needs the wider test.
 */
@Component
@ConditionalOnExpression(
    "'\${application.mail.provider:log}'.isBlank() or 'log'.equalsIgnoreCase('\${application.mail.provider:log}'.trim())"
)
class LoggingEmailSender : EmailSender {
    private val logger = LoggerFactory.getLogger(LoggingEmailSender::class.java)

    init {
        logger.warn("One-time codes are written to this log, not emailed. Set MAIL_PROVIDER to send them.")
    }

    override fun send(to: String, subject: String, body: String) {
        logger.info("Email (not sent, MAIL_PROVIDER is log)\nTo: {}\nSubject: {}\n{}", to, subject, body)
    }
}
