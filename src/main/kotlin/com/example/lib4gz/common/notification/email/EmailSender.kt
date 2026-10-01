package com.example.lib4gz.common.notification.email

/**
 * Transport only: it knows a recipient, a subject and a body, never who asked or why.
 * Exactly one implementation is in the context, chosen by `application.mail.provider`.
 *
 * Blocking, and expected to throw `ResponseStatusException(503)` when it cannot deliver: callers
 * send inside their own transaction, so the failure rolls their writes back.
 */
interface EmailSender {
    fun send(to: String, subject: String, body: String)
}
