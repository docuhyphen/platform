package com.docuhyphen.app.api.service.communication

import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.SendEmailRequest

@ApplicationScoped
class EmailService @Inject constructor()
{
    private val sesClient: SesClient = createSesClient()
    private val fromAddress = "no-reply@docuhyphen.com"

    fun createSesClient(): SesClient
    {
        val credentials = AwsBasicCredentials.create(
            \"REDACTED_AWS_ACCESS_KEY\",
            \"REDACTED_AWS_SECRET_KEY\",
        )

        return SesClient.builder()
            .region(Region.US_EAST_1) // SES region must match verified domain
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }

    fun sendEmail(to: String, subject: String, body: String, useHtml: Boolean? = false)
    {
        val destination = Destination.builder()
            .toAddresses(to)
            .build()

        val messageBody = if (useHtml == true)
        {
            Body.builder()
                .html(Content.builder().data(body).charset("UTF-8").build())
                .build()
        }
        else
        {
            Body.builder()
                .text(Content.builder().data(body).charset("UTF-8").build())
                .build()
        }

        val message = Message.builder()
            .subject(Content.builder().data(subject).charset("UTF-8").build())
            .body(messageBody)
            .build()

        val request = SendEmailRequest.builder()
            .source(fromAddress)
            .destination(destination)
            .message(message)
            .build()

//        sesClient.sendEmail(request)
    }
}
