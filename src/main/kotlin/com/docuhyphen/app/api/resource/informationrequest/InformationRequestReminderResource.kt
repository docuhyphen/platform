package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestReminderResultDto
import com.docuhyphen.app.api.model.informationrequest.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.resource.model.SendInformationRequestRemindersRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReminderService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-reminders")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestReminderResource @Inject constructor(
    private val reminderService: InformationRequestReminderService,
)
{
    @POST
    fun send(
        request: SendInformationRequestRemindersRequest,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
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
                .entity(results.map { InformationRequestReminderResultDto(it.requestId, it.noticeCount) })
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
