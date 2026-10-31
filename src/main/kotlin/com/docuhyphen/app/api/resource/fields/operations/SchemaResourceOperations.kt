package com.docuhyphen.app.api.resource.fields.operations

import com.docuhyphen.app.api.resource.model.CreateSchemaRequest
import com.docuhyphen.app.api.resource.model.FieldStatusRequest
import com.docuhyphen.app.api.resource.model.PublishSchemaRequest
import com.docuhyphen.app.api.resource.model.UpdateBindingsRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/schemas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SchemaResourceOperations {
    @GET
    @Path("/definitions")
    fun listSchemas(@QueryParam("scopeKind") scopeKindParam: String?): Response

    @POST
    @Path("/definitions")
    fun createSchema(request: CreateSchemaRequest): Response

    @GET
    @Path("/definitions/{id}")
    fun getSchema(@PathParam("id") id: String): Response

    @GET
    @Path("/versions/{versionId}")
    fun getVersion(@PathParam("versionId") versionId: String): Response

    @GET
    @Path("/definitions/{id}/resolved")
    fun getResolved(@PathParam("id") id: String): Response

    @PUT
    @Path("/definitions/{id}/draft/bindings")
    fun updateDraftBindings(@PathParam("id") id: String, request: UpdateBindingsRequest): Response

    @POST
    @Path("/definitions/{id}/draft/publish")
    fun publishDraft(@PathParam("id") id: String, request: PublishSchemaRequest?): Response

    @POST
    @Path("/definitions/{id}/versions")
    fun createDraftVersion(@PathParam("id") id: String): Response

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: FieldStatusRequest): Response
}
