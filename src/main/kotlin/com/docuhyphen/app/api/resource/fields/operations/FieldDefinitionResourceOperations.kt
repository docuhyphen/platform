package com.docuhyphen.app.api.resource.fields.operations

import com.docuhyphen.app.api.resource.model.CreateFieldDefinitionRequest
import com.docuhyphen.app.api.resource.model.FieldContractRequest
import com.docuhyphen.app.api.resource.model.FieldStatusRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/fields")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface FieldDefinitionResourceOperations {
    @GET
    @Path("/types")
    fun listTypes(): Response

    @GET
    @Path("/definitions")
    fun listDefinitions(@QueryParam("scopeKind") scopeKindParam: String?): Response

    @POST
    @Path("/definitions")
    fun createDefinition(request: CreateFieldDefinitionRequest): Response

    @GET
    @Path("/definitions/{id}")
    fun getDefinition(@PathParam("id") id: String): Response

    @GET
    @Path("/definitions/{id}/contracts")
    fun listContracts(@PathParam("id") id: String): Response

    @POST
    @Path("/definitions/{id}/contracts")
    fun addContract(@PathParam("id") id: String, request: FieldContractRequest): Response

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: FieldStatusRequest): Response
}
