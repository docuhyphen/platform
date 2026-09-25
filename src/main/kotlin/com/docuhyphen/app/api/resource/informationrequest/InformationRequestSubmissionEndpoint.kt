package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestSubmissionDtoMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionResult
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubmissionService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.ws.rs.core.Response
import java.util.UUID

class InformationRequestSubmissionEndpoint(
    private val submissionService: InformationRequestSubmissionService,
    private val queryService: InformationRequestSubmissionQueryService,
)
{
    fun list(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            queryService.packages(requestId, access)
                .map { InformationRequestSubmissionDtoMapper.toDto(it, access.principal) }
                .toTypedArray(),
        ).build()

    fun detail(requestId: UUID, packageId: UUID, access: RequestAccessContext): Response =
        Response.ok(InformationRequestSubmissionDtoMapper.toDto(queryService.packageDetail(requestId, packageId, access), access.principal))
            .build()

    fun preview(requestId: UUID, stageKey: String?, access: RequestAccessContext): Response
    {
        val preview = queryService.preview(requestId, stageKey, access)
        return Response.ok(InformationRequestSubmissionDtoMapper.toDto(preview, access.principal))
            .header("ETag", preview.submissionETag)
            .build()
    }

    fun submit(
        requestId: UUID,
        stageKey: String?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val command = SubmitInformationRequestPackageCommand(
            requestId = requestId,
            stageKey = stageKey?.trim()?.ifBlank { null },
            access = access,
            precondition = CommandPreconditionHeader.required(ifMatch),
            idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
        )
        return commandResponse(Response.Status.CREATED, submissionService.submit(command), access)
    }

    fun withdraw(
        requestId: UUID,
        packageId: UUID,
        reasonCode: String?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val command = WithdrawInformationRequestPackageCommand(
            requestId = requestId,
            packageId = packageId,
            reasonCode = reasonCode?.trim()?.ifBlank { null },
            access = access,
            precondition = CommandPreconditionHeader.required(ifMatch),
            idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
        )
        return commandResponse(Response.Status.OK, submissionService.withdraw(command), access)
    }

    private fun commandResponse(
        status: Response.Status,
        result: InformationRequestSubmissionResult,
        access: RequestAccessContext,
    ): Response =
        Response.status(status)
            .entity(InformationRequestSubmissionDtoMapper.toDto(result, queryService.readable(result.submission, access), access.principal))
            .header("ETag", result.responseETag)
            .build()
}
