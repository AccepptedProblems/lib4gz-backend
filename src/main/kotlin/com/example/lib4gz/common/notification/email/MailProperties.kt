package com.example.lib4gz.common.notification.email

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The providers `application.mail.provider` may name. It exists so a typo such as
 * `MAIL_PROVIDER=brvo` fails at property binding, naming the property and the bad value, instead
 * of surfacing later as "no bean of type EmailSender". A name here without a class beside it means
 * that provider has not been written yet.
 */
enum class MailProvider { LOG, BREVO, RESEND, GMAIL }

/**
 * What every provider shares. Credentials and endpoints live in the provider's own block
 * (`application.mail.brevo`, …), so switching provider is one environment variable and a redeploy.
 */
@ConfigurationProperties("application.mail")
class MailProperties(
    val provider: MailProvider = MailProvider.LOG,
    val from: From = From(),
    /** Connect and response timeout: a stuck provider must not hold the OTP transaction open. */
    val timeoutMs: Long = 10_000
) {

    /** Two fields, never a parsed `"Name <address>"` string: the HTTP APIs want them apart. */
    class From(
        name: String = DEFAULT_NAME,
        val address: String = ""
    ) {
        /**
         * Blank counts as unset. `MAIL_FROM_NAME=` in a `.env` file is a defined empty value, so it
         * defeats the `${MAIL_FROM_NAME:Lib4GZ}` default in `application.yaml` and would otherwise
         * put an empty display name on every message.
         */
        val name: String = name.ifBlank { DEFAULT_NAME }

        private companion object {
            const val DEFAULT_NAME = "Lib4GZ"
        }
    }

    /**
     * Fails the boot naming every environment variable this provider needs and does not have, e.g.
     * `application.mail.provider=brevo needs: BREVO_API_KEY, MAIL_FROM_ADDRESS`. One line in the
     * startup log beats a 503 on somebody's first sign-up. Providers pass the variables behind
     * their own blank settings; the From address is checked here because every provider needs it.
     */
    fun requireConfigured(blankProviderVars: List<String>) {
        val missing = blankProviderVars + listOfNotNull("MAIL_FROM_ADDRESS".takeIf { from.address.isBlank() })
        check(missing.isEmpty()) {
            "application.mail.provider=${provider.name.lowercase()} needs: ${missing.joinToString(", ")}"
        }
    }
}
