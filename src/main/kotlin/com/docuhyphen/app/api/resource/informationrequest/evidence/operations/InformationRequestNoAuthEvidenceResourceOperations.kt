package com.docuhyphen.app.api.resource.informationrequest.evidence.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.evidence.InformationRequestEvidenceHttp
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.*
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload

@Path("no-auth/information-requests/{id}/requirements/{requirementId}/evidence-artifacts")
@Produces(APPLICATION_JSON)
interface InformationRequestNoAuthEvidenceResourceOperations
{
    @GET
    fun list(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response

    @GET
    @Path("/{artifactId}")
    fun artifact(
        @PathParam("id") id: String,
        @PathParam("requirementId") requirementId: String,
        @PathParam("artifactId") artifactId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
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
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
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
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
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
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
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
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response

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
}
