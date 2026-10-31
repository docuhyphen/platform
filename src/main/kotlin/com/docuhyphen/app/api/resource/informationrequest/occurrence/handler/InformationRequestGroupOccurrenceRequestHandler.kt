package com.docuhyphen.app.api.resource.informationrequest.occurrence.handler

import com.docuhyphen.app.api.model.InformationRequestGroupOccurrenceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestGroupOccurrenceRequestHandler(
    private val occurrences: InformationRequestGroupOccurrenceService,
) {
    fun add(
        requestId: UUID,
        request: CreateInformationRequestGroupOccurrenceRequest,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String,
    ): Response = ok(
        occurrences.add(
            AddInformationRequestGroupOccurrenceCommand(
                requestId = requestId,
                access = access,
                precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                idempotencyKey = idempotencyKey,
                groupKey = request.groupKey,
                parentOccurrenceId = request.parentOccurrenceId,
            ),
        ),
    )

    fun remove(
        requestId: UUID,
        occurrenceId: UUID,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String,
    ): Response = ok(
        occurrences.remove(
            RemoveInformationRequestGroupOccurrenceCommand(
                requestId = requestId,
                access = access,
                precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                idempotencyKey = idempotencyKey,
                occurrenceId = occurrenceId,
            ),
        ),
    )

    fun reorder(
        requestId: UUID,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String,
    ): Response = ok(
        occurrences.reorder(
            ReorderInformationRequestGroupOccurrencesCommand(
                requestId = requestId,
                access = access,
                precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                idempotencyKey = idempotencyKey,
                groupKey = request.groupKey,
                parentOccurrenceId = request.parentOccurrenceId,
                orderedOccurrenceIds = request.occurrenceIds,
            ),
        ),
    )

    private fun ok(result: InformationRequestGroupOccurrenceResult): Response =
        Response.ok(result.occurrences.map(InformationRequestGroupOccurrenceDtoMapper::toDto).toTypedArray())
            .header("ETag", result.responseETag)
            .build()
}
