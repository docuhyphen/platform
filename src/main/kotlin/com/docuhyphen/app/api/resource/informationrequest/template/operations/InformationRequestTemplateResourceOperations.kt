package com.docuhyphen.app.api.resource.informationrequest.template.operations

import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-templates")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestTemplateResourceOperations
{
    @GET
    fun list(@QueryParam("scopeKind") scopeKindParam: String?): Response

    @POST
    fun create(request: CreateInformationRequestTemplateRequest): Response

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}/draft/configuration")
    fun replaceDraftConfiguration(
        @PathParam("id") id: String,
        request: InformationRequestTemplateConfigurationRequest,
    ): Response

    @POST
    @Path("/{id}/draft/publication")
    fun publishDraft(@PathParam("id") id: String): Response

    @POST
    @Path("/{id}/versions")
    fun createDraftVersion(
        @PathParam("id") id: String,
        request: InformationRequestTemplateNewVersionRequest,
    ): Response

    @POST
    @Path("/{id}/versions/{versionNumber}/retirement")
    fun retireVersion(
        @PathParam("id") id: String,
        @PathParam("versionNumber") versionNumber: String,
    ): Response

    @POST
    @Path("/{id}/clones")
    fun clone(
        @PathParam("id") id: String,
        request: CloneInformationRequestTemplateRequest,
    ): Response
}
