package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("exchanges/{exchangeId}/documents/{documentId}/versions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeDocumentVersionResourceOperations {
    @GET
    fun getDocumentVersions(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun createVersion(
        @RestForm("file") file: File?,
        @RestForm("encryptionMode") encryptionMode: DocumentEncryptionMode?,
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
    ): Response

    @GET
    @Path("/{versionId}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadVersion(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        @PathParam("versionId") versionId: String
    ): Response

    @GET
    @Path("/latest")
    fun getLatestVersion(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
}
