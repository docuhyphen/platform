package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceAttributeForm
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceUploadService
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

@Path("/information-requests/{id}/requirements/{requirementId}/evidence-artifacts")
@Produces(APPLICATION_JSON)
class InformationRequestEvidenceResource @Inject constructor(
    uploadService: InformationRequestEvidenceUploadService,
    collectionService: InformationRequestEvidenceCollectionService,
    queryService: InformationRequestEvidenceQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestEvidenceEndpoint(
        uploadService,
        collectionService,
        queryService,
        InformationRequestEvidenceSurface.AUTHENTICATED,
    )

    @GET
    fun list(@PathParam("id") id: String, @PathParam("requirementId") requirementId: String): Response
    {
        return try
        {
            endpoint.list(requestId(id), requirementId(requirementId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestEvidenceHttp.refused(logger, "Information Request evidence list failed", exception)
        }
    }

    @GET
    @Path("/{artifactId}")
    fun artifact(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
    ): Response
    {
        return try
        {
            endpoint.artifact(
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
    ): Response
    {
        return try
        {
            endpoint.upload(
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
    ): Response
    {
        return try
        {
            endpoint.replace(
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
    ): Response
    {
        return try
        {
            endpoint.withdraw(
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

    @DELETE
    @Path("/{artifactId}")
    fun remove(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @QueryParam("reason") reason: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestEvidenceHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.remove(
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

    @GET
    @Path("/{artifactId}/versions/{versionId}/content")
    @Produces(WILDCARD)
    fun download(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
    ): Response
    {
        return try
        {
            endpoint.content(
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

    @GET
    @Path("/{artifactId}/versions/{versionId}/preview")
    @Produces(WILDCARD)
    fun preview(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
    ): Response
    {
        return try
        {
            endpoint.content(
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
