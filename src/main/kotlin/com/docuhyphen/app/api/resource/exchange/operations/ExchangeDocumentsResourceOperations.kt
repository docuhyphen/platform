package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.resource.model.AddExchangeDocumentRequest
import com.docuhyphen.app.api.resource.model.DownloadDocumentsZipRequest
import com.docuhyphen.app.api.resource.model.UpdateShareSessionDocumentRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("exchanges/{exchangeId}/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeDocumentsResourceOperations {
    @POST
    fun addSessionDocument(
        request: AddExchangeDocumentRequest,
        @PathParam("exchangeId") exchangeId: String
    ): Response

    @DELETE
    @Path("/{documentId}")
    fun deleteSessionDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response

    @PUT
    @Path("/{documentId}")
    fun updateSessionDocument(
        request: UpdateShareSessionDocumentRequest,
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response

    @POST
    @Path("/{documentId}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadSessionDocument(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @RestForm("encryptionMode") encryptionMode: DocumentEncryptionMode?,
        @PathParam("exchangeId") exchangeId: String?,
        @PathParam("documentId") documentId: String?
    ): Response

    @POST
    @Path("/zip-file")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocumentsAsZip(
        request: DownloadDocumentsZipRequest,
        @PathParam("exchangeId") exchangeId: String
    ): Response

    @GET
    @Path("{documentId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadDocument(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response

    @GET
    @Path("{documentId}/preview")
    @Produces("application/pdf")
    fun getDocumentPreviewAsPdf(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response

    @GET
    @Path("{documentId}/thumbnail")
    @Produces("image/png")
    fun getDocumentThumbnail(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @HeaderParam(HttpHeaders.IF_NONE_MATCH) ifNoneMatch: String?,
    ): Response
}
