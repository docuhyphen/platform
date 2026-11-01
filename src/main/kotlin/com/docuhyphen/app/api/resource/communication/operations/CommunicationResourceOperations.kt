package com.docuhyphen.app.api.resource.communication.operations

import com.docuhyphen.app.api.model.dto.CloneCommunicationRequest
import com.docuhyphen.app.api.model.dto.CreateCommunicationRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationStatusRequest
import com.docuhyphen.app.api.model.dto.PreviewCommunicationRequest
import com.docuhyphen.app.api.model.dto.UpdateCommunicationRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/communications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface CommunicationResourceOperations {
    @GET
    fun listTemplates(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response

    @POST
    fun createTemplate(request: CreateCommunicationRequest): Response

    @GET
    @Path("/{id}")
    fun getTemplate(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}")
    fun updateTemplate(@PathParam("id") id: String, request: UpdateCommunicationRequest): Response

    @PATCH
    @Path("/{id}/status")
    fun patchTemplateStatus(@PathParam("id") id: String, request: PatchCommunicationStatusRequest): Response

    @PATCH
    @Path("/{id}/published")
    fun patchTemplatePublished(@PathParam("id") id: String, request: PatchCommunicationPublishedRequest): Response

    @DELETE
    @Path("/{id}")
    fun deleteTemplate(@PathParam("id") id: String): Response

    @POST
    @Path("/{id}/preview")
    fun previewTemplate(@PathParam("id") id: String, request: PreviewCommunicationRequest): Response

    @POST
    @Path("/{id}/clone")
    fun cloneTemplate(@PathParam("id") id: String, request: CloneCommunicationRequest): Response
}
