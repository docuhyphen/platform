package com.docuhyphen.app.api.resource.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.resource.informationrequest.evidence.handler.InformationRequestEvidenceRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.evidence.operations.InformationRequestEvidenceResourceOperations
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.slf4j.LoggerFactory

class InformationRequestEvidenceResource @Inject constructor(
    uploadService: InformationRequestEvidenceUploadService,
    collectionService: InformationRequestEvidenceCollectionService,
    queryService: InformationRequestEvidenceQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestEvidenceResourceOperations
{
    private val handler = InformationRequestEvidenceRequestHandler(
        uploadService,
        collectionService,
        queryService,
        InformationRequestEvidenceSurface.AUTHENTICATED,
    )

    override fun list(id: String, requirementId: String): Response
    {
        return try
        {
            handler.list(requestId(id), requirementId(requirementId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence list failed", exception)
        }
    }

    override fun artifact(
        id: String,
        requirementId: String,
        artifactId: String,
    ): Response
    {
        return try
        {
            handler.artifact(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                accessContextFactory.currentAuthenticated(),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence artifact read failed", exception)
        }
    }

    override fun upload(
        id: String,
        requirementId: String,
        file: FileUpload?,
        encryptionMode: String?,
        issuer: String?,
        jurisdiction: String?,
        language: String?,
        issuedOn: String?,
        expiresOn: String?,
        coverageStartsOn: String?,
        coverageEndsOn: String?,
        certificationReference: String?,
        signatureReference: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.upload(
                requestId(id),
                requirementId(requirementId),
                accessContextFactory.currentAuthenticated(),
                file,
                encryptionMode,
                InformationRequestEvidenceAttributeForm(
                    issuer, jurisdiction, language, issuedOn, expiresOn,
                    coverageStartsOn, coverageEndsOn, certificationReference, signatureReference,
                ),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence upload failed", exception)
        }
    }

    override fun replace(
        id: String,
        requirementId: String,
        artifactId: String,
        file: FileUpload?,
        encryptionMode: String?,
        issuer: String?,
        jurisdiction: String?,
        language: String?,
        issuedOn: String?,
        expiresOn: String?,
        coverageStartsOn: String?,
        coverageEndsOn: String?,
        certificationReference: String?,
        signatureReference: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.replace(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                accessContextFactory.currentAuthenticated(),
                file,
                encryptionMode,
                InformationRequestEvidenceAttributeForm(
                    issuer, jurisdiction, language, issuedOn, expiresOn,
                    coverageStartsOn, coverageEndsOn, certificationReference, signatureReference,
                ),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence replacement failed", exception)
        }
    }

    override fun withdraw(
        id: String,
        requirementId: String,
        artifactId: String,
        request: InformationRequestEvidenceStateChangeRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.withdraw(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                accessContextFactory.currentAuthenticated(),
                request?.reason,
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence withdrawal failed", exception)
        }
    }

    override fun remove(
        id: String,
        requirementId: String,
        artifactId: String,
        reason: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.remove(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                accessContextFactory.currentAuthenticated(),
                reason,
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence removal failed", exception)
        }
    }

    override fun download(
        id: String,
        requirementId: String,
        artifactId: String,
        versionId: String,
    ): Response
    {
        return try
        {
            handler.content(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                InformationRequestEvidenceHttp.uuid(versionId, "evidence version id"),
                accessContextFactory.currentAuthenticated(),
                InformationRequestEvidenceContentUse.DOWNLOAD,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence download failed", exception)
        }
    }

    override fun preview(
        id: String,
        requirementId: String,
        artifactId: String,
        versionId: String,
    ): Response
    {
        return try
        {
            handler.content(
                requestId(id),
                requirementId(requirementId),
                artifactId(artifactId),
                InformationRequestEvidenceHttp.uuid(versionId, "evidence version id"),
                accessContextFactory.currentAuthenticated(),
                InformationRequestEvidenceContentUse.PREVIEW,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence preview failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "information request id")

    private fun requirementId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "requirement id")

    private fun artifactId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "evidence artifact id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestEvidenceResource::class.java)
    }
}
