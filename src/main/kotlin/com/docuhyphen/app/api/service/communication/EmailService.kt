package com.docuhyphen.app.api.service.communication

import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class EmailService @Inject constructor(var mailer: Mailer)
{
    fun sendEmail(to: String, subject: String, body: String)
    {
        val mail = Mail.withHtml(to, subject, body)
        mailer.send(mail)
    }
}
