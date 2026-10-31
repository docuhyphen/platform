package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestOutboundNotice
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.MarkdownRenderer
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

interface InformationRequestNoticeSender
{
    fun subjectForDelivery(subject: String): String = subject

    fun send(notice: InformationRequestOutboundNotice)
}

@ApplicationScoped
class InformationRequestEmailNoticeSender @Inject constructor(
    private val emailService: EmailService,
    private val markdownRenderer: MarkdownRenderer,
    private val templateRenderer: EmailTemplateRenderer,
    @ConfigProperty(name = "app.url", defaultValue = "https://app.docuhyphen.com") private val appUrl: String,
    @ConfigProperty(name = "app.name", defaultValue = "DocuHyphen") private val appName: String,
) : InformationRequestNoticeSender
{
    private val logger = LoggerFactory.getLogger(InformationRequestEmailNoticeSender::class.java)

    override fun subjectForDelivery(subject: String): String = emailService.subjectForDelivery(subject)

    override fun send(notice: InformationRequestOutboundNotice)
    {
        val endpoint = requireNotNull(notice.recipientEndpoint) { "A delivered notice has a resolved endpoint" }
        if (notice.sourceKind == InformationRequestNoticeSourceKind.COMMUNICATION) {
            emailService.sendPreparedEmail(endpoint, notice.renderedSubject, formattedBody(notice), true)
        } else {
            emailService.sendPreparedEmail(endpoint, notice.renderedSubject, notice.renderedBody, false)
        }
    }

    private fun formattedBody(notice: InformationRequestOutboundNotice): String {
        val html = markdownRenderer.toHtml(notice.renderedBody)
        return runCatching {
            templateRenderer.render(
                WRAPPER_TEMPLATE,
                mapOf(
                    "appName" to appName,
                    "appUrl" to appUrl,
                    "htmlBody" to html,
                    "emailTitle" to notice.renderedSubject
                ),
            )
        }.getOrElse { failure ->
            logger.warn(
                "Information Request notice {} is sent without the email wrapper: {}",
                notice.id,
                failure.message
            )
            html
        }
    }

    private companion object {
        const val WRAPPER_TEMPLATE = "communication-wrapper.ftl"
    }
}
