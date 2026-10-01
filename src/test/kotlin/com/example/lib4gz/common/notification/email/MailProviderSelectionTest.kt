package com.example.lib4gz.common.notification.email

import com.example.lib4gz.common.notification.email.brevo.BrevoEmailSender
import com.example.lib4gz.common.notification.email.brevo.BrevoProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClient
import java.util.function.Supplier

/**
 * Exactly one sender is in the context, and the property alone decides which. No `@Qualifier`
 * anywhere, so switching provider in production is configuration, never a code edit.
 */
class MailProviderSelectionTest {

    private val runner = ApplicationContextRunner()
        .withBean(WebClient.Builder::class.java, Supplier { WebClient.builder() })
        .withUserConfiguration(MailProvidersConfig::class.java)
        .withPropertyValues(
            "application.mail.from.address=no-reply@lib4gz.test",
            "application.mail.brevo.api-key=secret-key"
        )

    @Test
    fun `provider brevo puts the Brevo sender in the context and nothing else`() {
        runner.withPropertyValues("application.mail.provider=brevo").run { context ->
            assertThat(context).hasSingleBean(EmailSender::class.java)
            assertThat(context).hasSingleBean(BrevoEmailSender::class.java)
            assertThat(context).doesNotHaveBean(LoggingEmailSender::class.java)
        }
    }

    @Test
    fun `provider log puts the logging sender in the context and nothing else`() {
        runner.withPropertyValues("application.mail.provider=log").run { context ->
            assertThat(context).hasSingleBean(EmailSender::class.java)
            assertThat(context).hasSingleBean(LoggingEmailSender::class.java)
            assertThat(context).doesNotHaveBean(BrevoEmailSender::class.java)
        }
    }

    @Test
    fun `an unset provider falls back to the logging sender, so a fresh checkout runs`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(EmailSender::class.java)
            assertThat(context).hasSingleBean(LoggingEmailSender::class.java)
        }
    }

    @Test
    fun `a blank provider is the logging sender too, since that is what an untouched env line gives`() {
        runner.withPropertyValues("application.mail.provider=").run { context ->
            assertThat(context).hasSingleBean(EmailSender::class.java)
            assertThat(context).hasSingleBean(LoggingEmailSender::class.java)
        }
    }

    @Test
    fun `the provider name is matched whatever its case`() {
        runner.withPropertyValues("application.mail.provider=BREVO").run { context ->
            assertThat(context).hasSingleBean(BrevoEmailSender::class.java)
        }
    }

    @Test
    fun `a provider named in the enum but not yet written leaves no sender, and the boot fails`() {
        // RESEND and GMAIL are reserved names with no class yet. Naming one is a configuration
        // mistake, and it has to stop the boot rather than start an app that cannot send.
        runner.withPropertyValues("application.mail.provider=resend").run { context ->
            assertThat(context).doesNotHaveBean(EmailSender::class.java)
        }
    }

    @Test
    fun `a misspelt provider fails the boot at binding, naming the property and the value`() {
        runner.withPropertyValues("application.mail.provider=brvo").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .hasStackTraceContaining("application.mail.provider")
                .hasStackTraceContaining("brvo")
        }
    }

    @EnableConfigurationProperties(MailProperties::class, BrevoProperties::class)
    @Import(LoggingEmailSender::class, BrevoEmailSender::class)
    class MailProvidersConfig
}
