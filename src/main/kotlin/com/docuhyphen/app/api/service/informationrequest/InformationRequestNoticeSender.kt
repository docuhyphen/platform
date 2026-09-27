package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestOutboundNotice
import com.docuhyphen.app.api.service.communication.EmailService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

interface InformationRequestNoticeSender
{
    fun send(notice: InformationRequestOutboundNotice)
}

@ApplicationScoped
class InformationRequestEmailNoticeSender @Inject constructor(
    private val emailService: EmailService,
) : InformationRequestNoticeSender
{
    override fun send(notice: InformationRequestOutboundNotice)
    {
        val endpoint = requireNotNull(notice.recipientEndpoint) { "A delivered notice has a resolved endpoint" }
        emailService.sendEmail(endpoint, notice.renderedSubject, notice.renderedBody, false)
    }
}
