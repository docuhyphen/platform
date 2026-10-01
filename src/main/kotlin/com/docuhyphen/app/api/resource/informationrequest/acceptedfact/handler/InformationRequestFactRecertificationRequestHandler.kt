package com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestAcceptedFactDtoMapper
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestFactRecertificationRequestHandler(
    private val recertifications: InformationRequestFactRecertificationService,
)
{
    fun recertify(
        requestId: UUID,
        factId: UUID,
        request: RecertifyInformationRequestAcceptedFactRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val body = request
            ?: throw InformationRequestCommandRequestException("A recertification names its Requirement and states assent")
        val result = recertifications.recertify(
            RecertifyInformationRequestAcceptedFactCommand(
                requestId = requestId,
                factId = factId,
                requirementId = body.requirementId,
                assented = body.assented,
                precondition = CommandPreconditionHeader.required(ifMatch),
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED)
            .entity(InformationRequestAcceptedFactDtoMapper.toDto(result.view))
            .header("ETag", result.responseETag)
            .build()
    }
}
