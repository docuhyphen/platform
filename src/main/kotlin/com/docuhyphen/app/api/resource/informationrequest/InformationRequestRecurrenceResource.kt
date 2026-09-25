package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRecurrenceRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestFollowUpService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeParseException

@Path("/information-requests/{id}/recurrences")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestRecurrenceResource @Inject constructor(
    private val followUpService: InformationRequestFollowUpService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun define(
        @PathParam("id") id: String,
        request: DefineInformationRequestRecurrenceRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val recurrence = followUpService.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    intervalUnit = request.intervalUnit,
                    intervalCount = request.intervalCount,
                    firstDueAt = instant(request.firstDueAt),
                    maximumOccurrences = request.maximumOccurrences,
                    access = accessContextFactory.currentAuthenticated(),
                    precondition = CommandPreconditionHeader.required(ifMatch),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestLineageDtoMapper.toDto(recurrence)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request recurrence definition failed", exception)
        }
    }

    @POST
    @Path("/{recurrenceId}/occurrences")
    fun createNextOccurrence(
        @PathParam("id") id: String,
        @PathParam("recurrenceId") recurrenceId: String,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val result = followUpService.createNextOccurrence(
                CreateNextInformationRequestOccurrenceCommand(
                    requestId = InformationRequestCommandHttp.uuid(id, "information request id"),
                    recurrenceId = InformationRequestCommandHttp.uuid(recurrenceId, "recurrence id"),
                    access = accessContextFactory.currentAuthenticated(),
                    idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestLineageDtoMapper.toDto(result))
                .header("ETag", result.successorETag)
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request recurrence occurrence failed", exception)
        }
    }

    private fun instant(raw: String): Instant =
        try
        {
            Instant.parse(raw.trim())
        }
        catch (_: DateTimeParseException)
        {
            throw InformationRequestCommandRequestException("The first due time is not an ISO instant")
        }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestRecurrenceResource::class.java)
    }
}
