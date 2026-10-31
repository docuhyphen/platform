package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestOutboundNotice
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.MarkdownRenderer
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.SendEmailRequest

class InformationRequestEmailNoticeSenderTest {
    private val client = mock<SesClient>()
    private val email = EmailService("af-south-1", "sender@example.test", "Workspace").also {
        EmailService::class.java.getDeclaredField("sesClient").apply { isAccessible = true }.set(it, client)
    }
    private val wrapper = mock<EmailTemplateRenderer> {
        on { render(any(), any()) } doAnswer { "<html>${it.getArgument<Map<String, Any>>(1)["htmlBody"]}</html>" }
    }

    @Test
    fun `the transmitted subject is exactly the immutable recorded subject`() {
        val notice = InformationRequestOutboundNotice().apply {
            recipientEndpoint = "recipient@example.test"
            renderedSubject = "Recorded subject"
            renderedBody = "Recorded body"
        }
        sender(wrapper).send(notice)
        val sent = argumentCaptor<SendEmailRequest>()
        verify(client).sendEmail(sent.capture())
        assertEquals(notice.renderedSubject, sent.firstValue.message().subject().data())
        assertEquals(notice.renderedBody, sent.firstValue.message().body().text().data())
    }

    @Test
    fun `a notice prepared from a Communication is sent as formatted email made from its Markdown body`() {
        val notice = communicationNotice()
        sender(wrapper).send(notice)
        val sent = argumentCaptor<SendEmailRequest>()
        verify(client).sendEmail(sent.capture())
        val body = sent.firstValue.message().body()
        assertNull(body.text())
        assertTrue(
            body.html().data().startsWith("<html><p>Please <strong>respond</strong> by Friday</p>"),
            body.html().data()
        )
        assertEquals("Recorded subject", sent.firstValue.message().subject().data())
        assertEquals("Please **respond** by Friday", notice.renderedBody)
    }

    @Test
    fun `a Communication notice is still sent as formatted email when the wrapper cannot be rendered`() {
        val failing =
            mock<EmailTemplateRenderer> { on { render(any(), any()) } doThrow IllegalStateException("no template") }
        sender(failing).send(communicationNotice())
        val sent = argumentCaptor<SendEmailRequest>()
        verify(client).sendEmail(sent.capture())
        assertTrue(sent.firstValue.message().body().html().data().startsWith("<p>Please <strong>respond</strong>"))
    }

    private fun sender(templates: EmailTemplateRenderer) =
        InformationRequestEmailNoticeSender(
            email,
            MarkdownRenderer(),
            templates,
            "https://app.example.test",
            "Workspace"
        )

    private fun communicationNotice() = InformationRequestOutboundNotice().apply {
        recipientEndpoint = "recipient@example.test"
        renderedSubject = "Recorded subject"
        renderedBody = "Please **respond** by Friday"
        sourceKind = InformationRequestNoticeSourceKind.COMMUNICATION
    }
}
