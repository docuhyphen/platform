package com.docuhyphen.app.api.resource.documentlibrary.operations

import com.docuhyphen.app.api.model.dto.CloneDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.CreateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateDocumentLibraryEntryRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("/document-library")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface DocumentLibraryResourceOperations {
    @GET
    fun listEntries(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
    ): Response

    @POST
    fun createEntry(request: CreateDocumentLibraryEntryRequest): Response

    @GET
    @Path("/{id}")
    fun getEntry(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}")
    fun updateEntry(@PathParam("id") id: String, request: UpdateDocumentLibraryEntryRequest): Response

    @PATCH
    @Path("/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: PatchDocumentLibraryStatusRequest): Response

    @PATCH
    @Path("/{id}/published")
    fun patchPublished(@PathParam("id") id: String, request: PatchDocumentLibraryPublishedRequest): Response

    @DELETE
    @Path("/{id}")
    fun deleteEntry(@PathParam("id") id: String): Response

    @POST
    @Path("/{id}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadFile(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
        @PathParam("id") id: String,
    ): Response

    @GET
    @Path("/{id}/file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun downloadFile(@PathParam("id") id: String): Response

    @POST
    @Path("/{id}/clone")
    fun cloneEntry(@PathParam("id") id: String, request: CloneDocumentLibraryEntryRequest): Response
}
