package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestEvidenceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.util.UUID

class InformationRequestEvidenceEndpoint(
    private val uploadService: InformationRequestEvidenceUploadService,
    private val collectionService: InformationRequestEvidenceCollectionService,
    private val queryService: InformationRequestEvidenceQueryService,
    private val surface: InformationRequestEvidenceSurface,
)
{
    fun list(requestId: UUID, requirementId: UUID, access: RequestAccessContext): Response
    {
        val list = queryService.list(requestId, requirementId, access)
        return Response.ok(InformationRequestEvidenceDtoMapper.toDto(list, access.principal))
            .header("ETag", list.evidenceETag)
            .build()
    }

    fun artifact(requestId: UUID, requirementId: UUID, artifactId: UUID, access: RequestAccessContext): Response
    {
        val artifact = InformationRequestEvidenceDtoMapper.toDto(
            queryService.artifact(requestId, requirementId, artifactId, access),
            access.principal,
        )
        return Response.ok(artifact).header("ETag", artifact.etag).build()
    }

    fun upload(
        requestId: UUID,
        requirementId: UUID,
        access: RequestAccessContext,
        file: FileUpload?,
        encryptionMode: String?,
        attributes: InformationRequestEvidenceAttributeForm,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val key = InformationRequestEvidenceHttp.idempotencyKey(idempotencyKey)
        val command = UploadInformationRequestEvidenceCommand(
            requestId = requestId,
            requirementId = requirementId,
            access = access,
            surface = surface,
            precondition = CommandPreconditionHeader.required(ifMatch),
            idempotencyKey = key,
            file = InformationRequestEvidenceHttp.evidenceFile(file, encryptionMode),
            attributes = InformationRequestEvidenceHttp.attributes(attributes),
        )
        return commandResponse(Response.Status.CREATED, uploadService.upload(command), access)
    }

    fun replace(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
        file: FileUpload?,
        encryptionMode: String?,
        attributes: InformationRequestEvidenceAttributeForm,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val key = InformationRequestEvidenceHttp.idempotencyKey(idempotencyKey)
        val command = ReplaceInformationRequestEvidenceCommand(
            requestId = requestId,
            requirementId = requirementId,
            artifactId = artifactId,
            access = access,
            surface = surface,
            precondition = CommandPreconditionHeader.required(ifMatch),
            idempotencyKey = key,
            file = InformationRequestEvidenceHttp.evidenceFile(file, encryptionMode),
            attributes = InformationRequestEvidenceHttp.attributes(attributes),
        )
        return commandResponse(Response.Status.OK, uploadService.replace(command), access)
    }

    fun withdraw(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
        reason: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response =
        commandResponse(
            Response.Status.OK,
            collectionService.withdraw(stateCommand(requestId, requirementId, artifactId, access, reason, ifMatch, idempotencyKey)),
            access,
        )

    fun remove(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
        reason: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response =
        commandResponse(
            Response.Status.OK,
            collectionService.remove(stateCommand(requestId, requirementId, artifactId, access, reason, ifMatch, idempotencyKey)),
            access,
        )

    fun content(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        versionId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): Response =
        InformationRequestEvidenceHttp.content(
            queryService.openContent(requestId, requirementId, artifactId, versionId, access, use),
        )

    private fun stateCommand(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
        reason: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ) = ChangeInformationRequestEvidenceStateCommand(
        requestId = requestId,
        requirementId = requirementId,
        artifactId = artifactId,
        access = access,
        precondition = CommandPreconditionHeader.required(ifMatch),
        idempotencyKey = InformationRequestEvidenceHttp.idempotencyKey(idempotencyKey),
        reason = reason?.trim()?.takeIf { it.isNotEmpty() },
    )

    private fun commandResponse(
        status: Response.Status,
        result: InformationRequestEvidenceCommandResult,
        access: RequestAccessContext,
    ): Response =
        Response.status(status)
            .entity(InformationRequestEvidenceDtoMapper.toDto(result, access.principal))
            .header("ETag", result.artifactETag)
            .header(InformationRequestEvidenceHttp.EVIDENCE_ETAG_HEADER, result.evidenceETag)
            .build()
}
