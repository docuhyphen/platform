package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestSubmissionDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.RecordInformationRequestAttestationRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.ws.rs.core.Response
import java.util.UUID

class InformationRequestAttestationEndpoint(
    private val attestationService: InformationRequestSubmissionAttestationService,
)
{
    fun record(
        requestId: UUID,
        requirementId: UUID,
        request: RecordInformationRequestAttestationRequest,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val result = attestationService.record(
            RecordInformationRequestSubmissionAttestationCommand(
                requestId = requestId,
                requirementId = requirementId,
                decision = request.decision,
                refusalReason = request.refusalReason?.trim()?.ifBlank { null },
                externalSignatureReference = request.externalSignatureReference?.trim()?.ifBlank { null },
                partyId = request.partyId,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED)
            .entity(InformationRequestSubmissionDtoMapper.toDto(result, access.principal))
            .header("ETag", result.submissionETag)
            .build()
    }
}
