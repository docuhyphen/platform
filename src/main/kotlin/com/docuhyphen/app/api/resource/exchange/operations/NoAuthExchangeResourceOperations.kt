package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.exchange.NoAuthExchangeHttp
import com.docuhyphen.app.api.resource.model.UpdateNoAuthExchange
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("no-auth/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface NoAuthExchangeResourceOperations {
    @GET
    @Path("/{exchangeId}")
    fun getNoAuthExchange(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam(NoAuthExchangeHttp.SHARE_LINK_TOKEN_HEADER) shareLinkToken: String?,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
    ): Response

    @POST
    @Path("/{exchangeId}/otp")
    fun issueNoAuthExchangeOtp(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
    ): Response

    @POST
    @Path("/{exchangeId}/verify-access-code")
    fun verifyNoAuthAccessCode(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
        request: UpdateNoAuthExchange,
    ): Response

    @PUT
    @Path("/{exchangeId}")
    fun updateNoAuthExchange(
        @PathParam("exchangeId") exchangeId: String,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
        request: UpdateNoAuthExchange
    ): Response

    @POST
    @Path("{exchangeId}/documents/{documentId}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadSessionDocument(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @RestForm("encryptionMode") encryptionMode: DocumentEncryptionMode?,
        @PathParam("exchangeId") exchangeId: String?,
        @PathParam("documentId") documentId: String?,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
    ): Response

    @GET
    @Path("{exchangeId}/documents/{documentId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
    ): Response

    @GET
    @Path("{exchangeId}/documents/{documentId}/thumbnail")
    @Produces("image/png")
    fun getNoAuthDocumentThumbnail(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @HeaderParam(NoAuthExchangeHttp.ACCESS_TOKEN_HEADER) noAuthAccessToken: String?,
        @HeaderParam(HttpHeaders.IF_NONE_MATCH) ifNoneMatch: String?,
    ): Response
}
