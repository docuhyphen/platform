package com.docuhyphen.app.api.resource.informationrequest.evidence

import com.docuhyphen.app.api.exception.InformationRequestEvidenceRequestException
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.resource.informationrequest.evidence.handler.InformationRequestEvidenceRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.evidence.operations.InformationRequestNoAuthEvidenceResourceOperations
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.slf4j.LoggerFactory
import java.util.*

class InformationRequestNoAuthEvidenceResource @Inject constructor(
    uploadService: InformationRequestEvidenceUploadService,
    collectionService: InformationRequestEvidenceCollectionService,
    queryService: InformationRequestEvidenceQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthEvidenceResourceOperations
{
    private val handler = InformationRequestEvidenceRequestHandler(
        uploadService,
        collectionService,
        queryService,
        InformationRequestEvidenceSurface.NO_AUTH,
    )

    override fun list(
        id: String,
        requirementId: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.list(requestId, requirementId(requirementId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence list failed",
                exception
            )
        }
    }

    override fun artifact(
        id: String,
        requirementId: String,
        artifactId: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.artifact(requestId, requirementId(requirementId), artifactId(artifactId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence artifact read failed",
                exception
            )
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
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.upload(
                    requestId,
                    requirementId(requirementId),
                    access,
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
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence upload failed",
                exception
            )
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
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.replace(
                    requestId,
                    requirementId(requirementId),
                    artifactId(artifactId),
                    access,
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
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence replacement failed",
                exception
            )
        }
    }

    override fun withdraw(
        id: String,
        requirementId: String,
        artifactId: String,
        request: InformationRequestEvidenceStateChangeRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.withdraw(
                    requestId,
                    requirementId(requirementId),
                    artifactId(artifactId),
                    access,
                    request?.reason,
                    ifMatch,
                    idempotencyKey,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence withdrawal failed",
                exception
            )
        }
    }

    override fun remove(
        id: String,
        requirementId: String,
        artifactId: String,
        reason: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.remove(
                    requestId,
                    requirementId(requirementId),
                    artifactId(artifactId),
                    access,
                    reason,
                    ifMatch,
                    idempotencyKey,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence removal failed",
                exception
            )
        }
    }

    override fun download(
        id: String,
        requirementId: String,
        artifactId: String,
        versionId: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.content(
                    requestId,
                    requirementId(requirementId),
                    artifactId(artifactId),
                    InformationRequestEvidenceHttp.uuid(versionId, "evidence version id"),
                    access,
                    InformationRequestEvidenceContentUse.DOWNLOAD,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence download failed",
                exception
            )
        }
    }

    override fun preview(
        id: String,
        requirementId: String,
        artifactId: String,
        versionId: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                handler.content(
                    requestId,
                    requirementId(requirementId),
                    artifactId(artifactId),
                    InformationRequestEvidenceHttp.uuid(versionId, "evidence version id"),
                    access,
                    InformationRequestEvidenceContentUse.PREVIEW,
                )
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(
                logger,
                "No-auth Information Request evidence preview failed",
                exception
            )
        }
    }

    private fun withAccess(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
        call: (UUID, RequestAccessContext) -> Response,
    ): Response
    {
        val requestId = requestId(id)
        val token = accessLinkToken?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InformationRequestEvidenceRequestException("An access link token is required")
        val noAuthAccess = readAccessService.resolve(token, sessionToken)
        if (noAuthAccess.requestId != requestId)
        {
            return InformationRequestEvidenceHttp.notFound()
        }
        return call(requestId, noAuthAccess.access)
    }

    private fun requestId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "information request id")

    private fun requirementId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "requirement id")

    private fun artifactId(raw: String) = InformationRequestEvidenceHttp.uuid(raw, "evidence artifact id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthEvidenceResource::class.java)
    }
}
