package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestEvidenceRequestException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA
import jakarta.ws.rs.core.MediaType.WILDCARD
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("no-auth/information-requests/{id}/requirements/{requirementId}/evidence-artifacts")
@Produces(APPLICATION_JSON)
class InformationRequestNoAuthEvidenceResource @Inject constructor(
    uploadService: InformationRequestEvidenceUploadService,
    collectionService: InformationRequestEvidenceCollectionService,
    queryService: InformationRequestEvidenceQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestEvidenceEndpoint(
        uploadService,
        collectionService,
        queryService,
        InformationRequestEvidenceSurface.NO_AUTH,
    )

    @GET
    fun list(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.list(requestId, requirementId(requirementId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence list failed", exception)
        }
    }

    @GET
    @Path("/{artifactId}")
    fun artifact(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.artifact(requestId, requirementId(requirementId), artifactId(artifactId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence artifact read failed", exception)
        }
    }

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    fun upload(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @RestForm("file") file: FileUpload?,
        @RestForm("encryptionMode") encryptionMode: String?,
        @RestForm("issuer") issuer: String?,
        @RestForm("jurisdiction") jurisdiction: String?,
        @RestForm("language") language: String?,
        @RestForm("issuedOn") issuedOn: String?,
        @RestForm("expiresOn") expiresOn: String?,
        @RestForm("coverageStartsOn") coverageStartsOn: String?,
        @RestForm("coverageEndsOn") coverageEndsOn: String?,
        @RestForm("certificationReference") certificationReference: String?,
        @RestForm("signatureReference") signatureReference: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestEvidenceHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.upload(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence upload failed", exception)
        }
    }

    @POST
    @Path("/{artifactId}/versions")
    @Consumes(MULTIPART_FORM_DATA)
    fun replace(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @RestForm("file") file: FileUpload?,
        @RestForm("encryptionMode") encryptionMode: String?,
        @RestForm("issuer") issuer: String?,
        @RestForm("jurisdiction") jurisdiction: String?,
        @RestForm("language") language: String?,
        @RestForm("issuedOn") issuedOn: String?,
        @RestForm("expiresOn") expiresOn: String?,
        @RestForm("coverageStartsOn") coverageStartsOn: String?,
        @RestForm("coverageEndsOn") coverageEndsOn: String?,
        @RestForm("certificationReference") certificationReference: String?,
        @RestForm("signatureReference") signatureReference: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestEvidenceHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.replace(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence replacement failed", exception)
        }
    }

    @POST
    @Path("/{artifactId}/withdrawals")
    @Consumes(APPLICATION_JSON)
    fun withdraw(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        request: InformationRequestEvidenceStateChangeRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestEvidenceHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.withdraw(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence withdrawal failed", exception)
        }
    }

    @DELETE
    @Path("/{artifactId}")
    fun remove(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @QueryParam("reason") reason: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestEvidenceHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.remove(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence removal failed", exception)
        }
    }

    @GET
    @Path("/{artifactId}/versions/{versionId}/content")
    @Produces(WILDCARD)
    fun download(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.content(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence download failed", exception)
        }
    }

    @GET
    @Path("/{artifactId}/versions/{versionId}/preview")
    @Produces(WILDCARD)
    fun preview(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            withAccess(id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.content(
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
            InformationRequestEvidenceHttp.refused(logger, "No-auth Information Request evidence preview failed", exception)
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
        const val ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token"
        const val SESSION_TOKEN_HEADER = "X-Request-Session-Token"
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthEvidenceResource::class.java)
    }
}
