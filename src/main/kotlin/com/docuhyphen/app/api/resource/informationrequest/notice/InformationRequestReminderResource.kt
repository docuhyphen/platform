package com.docuhyphen.app.api.resource.informationrequest.notice

import com.docuhyphen.app.api.model.InformationRequestReminderDtoMapper
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.notice.operations.InformationRequestReminderResourceOperations
import com.docuhyphen.app.api.resource.model.SendInformationRequestRemindersRequest
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestReminderService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestReminderResource @Inject constructor(
    private val reminderService: InformationRequestReminderService,
) : InformationRequestReminderResourceOperations
{
    override fun send(
        request: SendInformationRequestRemindersRequest,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val results = reminderService.send(
                SendInformationRequestRemindersCommand(
                    requestIds = request.requestIds,
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(results.map(InformationRequestReminderDtoMapper::toDto))
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reminders failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReminderResource::class.java)
    }
}
