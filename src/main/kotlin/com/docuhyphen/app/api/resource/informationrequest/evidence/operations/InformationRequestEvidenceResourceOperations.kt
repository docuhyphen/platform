package com.docuhyphen.app.api.resource.informationrequest.evidence.operations

import com.docuhyphen.app.api.resource.informationrequest.evidence.InformationRequestEvidenceHttp
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
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

@Path("/information-requests/{id}/requirements/{requirementId}/evidence-artifacts")
@Produces(APPLICATION_JSON)
interface InformationRequestEvidenceResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String, @PathParam("requirementId") requirementId: String): Response

    @GET
    @Path("/{artifactId}")
    fun artifact(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
    ): Response

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

    @GET
    @Path("/{artifactId}/versions/{versionId}/content")
    @Produces(WILDCARD)
    fun download(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
    ): Response

    @GET
    @Path("/{artifactId}/versions/{versionId}/preview")
    @Produces(WILDCARD)
    fun preview(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @PathParam("versionId") versionId: String,
    ): Response
}
