package com.docuhyphen.app.api.resource.informationrequest.clock.handler

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestClockDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.*
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestClockRequest
import com.docuhyphen.app.api.resource.model.DefineInformationRequestClockPolicyRequest
import com.docuhyphen.app.api.resource.model.InformationRequestClockPolicyDefinitionRequest
import com.docuhyphen.app.api.resource.model.StartInformationRequestClockRequest
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import jakarta.ws.rs.core.Response
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

class InformationRequestClockRequestHandler(
    private val policies: InformationRequestClockPolicyService,
    private val clocks: InformationRequestClockService,
)
{
    fun listPolicies(): Response =
        Response.ok(policies.list().map(InformationRequestClockDtoMapper::toDto).toTypedArray()).build()

    fun policy(policyId: UUID): Response =
        Response.ok(InformationRequestClockDtoMapper.toDto(policies.get(policyId))).build()

    fun definePolicy(request: DefineInformationRequestClockPolicyRequest?): Response
    {
        val body = request
            ?: throw InformationRequestCommandRequestException("A clock policy states its key, name, and definition")
        val view = policies.define(
            DefineInformationRequestClockPolicyCommand(
                body.policyKey,
                body.displayName,
                definition(body.definition)
            )
        )
        return Response.status(Response.Status.CREATED).entity(InformationRequestClockDtoMapper.toDto(view)).build()
    }

    fun publishVersion(policyId: UUID, request: InformationRequestClockPolicyDefinitionRequest?): Response
    {
        val body =
            request ?: throw InformationRequestCommandRequestException("A clock policy version states its definition")
        val view =
            policies.publishVersion(PublishInformationRequestClockPolicyVersionCommand(policyId, definition(body)))
        return Response.status(Response.Status.CREATED).entity(InformationRequestClockDtoMapper.toDto(view)).build()
    }

    fun clocks(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(clocks.clocks(requestId, access).map(InformationRequestClockDtoMapper::toDto).toTypedArray())
            .build()

    fun start(
        requestId: UUID,
        request: StartInformationRequestClockRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?
    ): Response
    {
        val body =
            request ?: throw InformationRequestCommandRequestException("A clock names its key and policy version")
        val view = clocks.start(
            StartInformationRequestClockCommand(
                requestId = requestId,
                clockKey = body.clockKey,
                policyVersionId = body.policyVersionId,
                urgency = body.urgency,
                receivedAt = body.receivedAt?.toInstant(),
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED).entity(InformationRequestClockDtoMapper.toDto(view))
            .header("ETag", view.clockETag).build()
    }

    @Suppress("LongParameterList")
    fun change(
        requestId: UUID,
        clockId: UUID,
        change: InformationRequestClockChange,
        request: ChangeInformationRequestClockRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val body = request ?: throw InformationRequestCommandRequestException("A clock change states its reason")
        val view = clocks.change(
            ChangeInformationRequestClockCommand(
                requestId = requestId,
                clockId = clockId,
                change = change,
                extensionMinutes = body.extensionMinutes,
                reasonCode = body.reasonCode,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.ok(InformationRequestClockDtoMapper.toDto(view)).header("ETag", view.clockETag).build()
    }

    private fun definition(request: InformationRequestClockPolicyDefinitionRequest) =
        InformationRequestClockPolicyDefinition(
            clockType = request.clockType,
            businessTimezone = request.businessTimezone,
            workingPeriods = request.workingPeriods.map {
                InformationRequestWorkingPeriod(dayOf(it.dayOfWeek), it.startMinute, it.endMinute)
            },
            holidays = request.holidays.map(::dateOf),
            standardDurationMinutes = request.standardDurationMinutes,
            urgentDurationMinutes = request.urgentDurationMinutes,
            reminderMinutesBeforeDue = request.reminderMinutesBeforeDue,
            escalationAfterMinutes = request.escalationAfterMinutes,
            dueEffect = request.dueEffect,
            reminderCommunicationId = request.reminderCommunicationId,
            overdueCommunicationId = request.overdueCommunicationId,
        )

    private fun dayOf(raw: String): DayOfWeek =
        runCatching { DayOfWeek.valueOf(raw.trim().uppercase()) }.getOrNull()
            ?: throw InformationRequestCommandRequestException("A working period names a day of the week")

    private fun dateOf(raw: String): LocalDate =
        try
        {
            LocalDate.parse(raw.trim())
        }
        catch (_: DateTimeException)
        {
            throw InformationRequestCommandRequestException("A holiday is an ISO calendar date")
        }
}
