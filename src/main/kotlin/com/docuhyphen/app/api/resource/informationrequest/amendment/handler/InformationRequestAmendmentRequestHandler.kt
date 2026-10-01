package com.docuhyphen.app.api.resource.informationrequest.amendment.handler

import com.docuhyphen.app.api.model.InformationRequestAmendmentDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.AmendInformationRequestRequest
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestAmendmentRequestHandler(
    private val amendmentService: InformationRequestAmendmentService,
    private val queryService: InformationRequestAmendmentQueryService,
)
{
    fun list(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            queryService.amendments(requestId, access)
                .map { InformationRequestAmendmentDtoMapper.toDto(it, access.principal) }
                .toTypedArray(),
        ).build()

    fun amend(
        requestId: UUID,
        request: AmendInformationRequestRequest,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val result = amendmentService.amend(
            AmendInformationRequestCommand(
                requestId = requestId,
                targetTemplateVersionId = request.targetTemplateVersionId,
                configuration = request.configuration,
                reasonCode = request.reasonCode?.trim()?.ifBlank { null },
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED)
            .entity(
                InformationRequestAmendmentDtoMapper.toDto(
                    result,
                    queryService.readable(result.request, result.amendment, access),
                    access.principal,
                ),
            )
            .header("ETag", result.requestETag)
            .build()
    }
}
