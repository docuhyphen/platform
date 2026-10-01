package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestRecurrenceResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRecurrenceRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeParseException

class InformationRequestRecurrenceResource @Inject constructor(
    private val followUpService: InformationRequestFollowUpService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestRecurrenceResourceOperations
{
    override fun define(
        id: String,
        request: DefineInformationRequestRecurrenceRequest,
        ifMatch: String?,
        idempotencyKey: String?,
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

    override fun createNextOccurrence(
        id: String,
        recurrenceId: String,
        idempotencyKey: String?,
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
