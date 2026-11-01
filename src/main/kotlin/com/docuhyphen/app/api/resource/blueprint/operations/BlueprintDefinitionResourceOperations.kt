package com.docuhyphen.app.api.resource.blueprint.operations

import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/blueprints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface BlueprintDefinitionResourceOperations {
    @GET
    fun listBlueprints(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response

    @POST
    fun createBlueprint(
        request: CreateBlueprintRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/{id}")
    fun getBlueprint(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}")
    fun updateBlueprint(
        @PathParam("id") id: String,
        request: UpdateBlueprintRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PATCH
    @Path("/{id}/status")
    fun patchBlueprintStatus(
        @PathParam("id") id: String,
        request: PatchBlueprintStatusRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PATCH
    @Path("/{id}/published")
    fun patchBlueprintPublished(
        @PathParam("id") id: String,
        request: PatchBlueprintPublishedRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @DELETE
    @Path("/{id}")
    fun deleteBlueprint(
        @PathParam("id") id: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @POST
    @Path("/{id}/clone")
    fun cloneBlueprint(
        @PathParam("id") id: String,
        request: CloneBlueprintRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
