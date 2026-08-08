package com.docuhyphen.app.api.service.communication

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.SendEmailRequest

@ApplicationScoped
class EmailService @Inject constructor(
    @ConfigProperty(name = "app.email.ses.region") private val sesRegion: String,
    @ConfigProperty(name = "quarkus.mailer.from") private val fromAddress: String,
    @ConfigProperty(name = "app.email.subject-title") private val emailSubjectTitle: String,
)
{
    private val sesClient: SesClient = createSesClient()

    private fun createSesClient(): SesClient
    {
        return SesClient.builder()
            .region(Region.of(sesRegion))
            .build()
    }

    /**
     * Every outbound email subject is tagged with the deployment's display name so recipients
     * can immediately identify which environment/product sent the message, for example
     * "DocuHyphen: Confirm email change" wrapped in brackets. Callers must pass the bare,
     * descriptive subject text only; this is the single place that applies the bracketed prefix.
     */
    private fun formatSubject(subject: String): String = "[$emailSubjectTitle] $subject"

    fun sendEmail(to: String, subject: String, body: String, useHtml: Boolean? = false)
    {
        val formattedSubject = formatSubject(subject)
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
            .subject(Content.builder().data(formattedSubject).charset("UTF-8").build())
            .body(messageBody)
            .build()

        val request = SendEmailRequest.builder()
            .source(fromAddress)
            .destination(destination)
            .message(message)
            .build()

        sesClient.sendEmail(request)
    }
}
