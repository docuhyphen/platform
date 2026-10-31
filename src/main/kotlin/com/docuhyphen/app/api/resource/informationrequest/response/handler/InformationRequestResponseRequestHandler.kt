package com.docuhyphen.app.api.resource.informationrequest.response.handler

import com.docuhyphen.app.api.model.InformationRequestItemRefusalDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponseDtoMapper
import com.docuhyphen.app.api.model.InformationRequestResponsePatchRequestMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestResponseRequestHandler(
    private val responseDraftService: InformationRequestResponseDraftService,
) {
    fun invalidPatch(request: PatchInformationRequestResponsesRequest): Response? = when {
        request.patches.isEmpty() -> badRequest("At least one patch is required")
        request.patches.size > MAXIMUM_PATCHES -> badRequest("A save names at most $MAXIMUM_PATCHES answers")
        request.patches.any { (it.narrative?.length ?: 0) > MAXIMUM_NARRATIVE } ->
            badRequest("A note on an answer is at most $MAXIMUM_NARRATIVE characters")

        request.patches.sumOf { it.fieldValues?.values?.size ?: 0 } > MAXIMUM_FIELD_VALUES ->
            badRequest("A save carries at most $MAXIMUM_FIELD_VALUES Field values")

        request.patches.any(InformationRequestResponsePatchRequestMapper::hasConflictingNarrativeOperation) ->
            badRequest("A patch cannot both set and clear its narrative")

        else -> null
    }

    fun patch(
        requestId: UUID,
        request: PatchInformationRequestResponsesRequest,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String,
    ): Response {
        val result = responseDraftService.patch(
            PatchInformationRequestResponsesCommand(
                requestId = requestId,
                access = access,
                precondition = CommandPreconditionHeader.requiredExact(ifMatch),
                idempotencyKey = idempotencyKey,
                patches = request.patches.map(InformationRequestResponsePatchRequestMapper::toDomain),
                confirmedHiddenResponseClears = request.confirmedHiddenResponseClearRequirementIds,
                respondentKey = request.respondentKey,
            ),
        )
        return Response.ok(InformationRequestResponseDtoMapper.toDtos(result).toTypedArray())
            .header("ETag", result.responseETag)
            .build()
    }

    fun refused(exception: InformationRequestItemRefusalException): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(InformationRequestItemRefusalDtoMapper.toDto(exception))
            .build()

    private fun badRequest(message: String): Response =
        Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(message)).build()

    private companion object {
        const val MAXIMUM_PATCHES = 200
        const val MAXIMUM_NARRATIVE = 4000
        const val MAXIMUM_FIELD_VALUES = 500
    }
}
