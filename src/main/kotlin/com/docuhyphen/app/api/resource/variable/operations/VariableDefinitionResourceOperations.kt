package com.docuhyphen.app.api.resource.variable.operations

import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/variables")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface VariableDefinitionResourceOperations {
    @GET
    @Path("/available")
    fun getAvailableVariables(): Response

    @GET
    fun listVariables(@QueryParam("scope") scopeParam: String?): Response

    @POST
    fun createVariable(
        request: CreateVariableRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PUT
    @Path("/{id}")
    fun updateVariable(
        @PathParam("id") id: String,
        request: UpdateVariableRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @DELETE
    @Path("/{id}")
    fun deleteVariable(
        @PathParam("id") id: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
