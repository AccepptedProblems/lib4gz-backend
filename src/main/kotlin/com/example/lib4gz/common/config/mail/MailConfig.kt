package com.example.lib4gz.common.config.mail

import com.example.lib4gz.common.mail.EmailSender
import com.example.lib4gz.common.mail.LoggingEmailSender
import com.example.lib4gz.common.mail.SmtpEmailSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender

@Configuration
class MailConfig {

    @Bean
    fun emailSender(
        mailSender: JavaMailSender,
        @Value("\${spring.mail.username:}") username: String,
        @Value("\${application.mail.from}") from: String
    ): EmailSender =
        if (username.isBlank()) LoggingEmailSender() else SmtpEmailSender(mailSender, from)
}
