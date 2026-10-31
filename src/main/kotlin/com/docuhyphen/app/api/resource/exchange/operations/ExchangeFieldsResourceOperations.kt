package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.AssignSchemaRequest
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeFieldsResourceOperations {
    @GET
    @Path("/{id}/schema")
    fun getSchema(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}/schema")
    fun assignSchema(
        @PathParam("id") id: String,
        request: AssignSchemaRequest,
        @HeaderParam(HttpHeaders.IF_MATCH) ifMatch: String?,
        @HeaderParam(HttpHeaders.IF_NONE_MATCH) ifNoneMatch: String?,
    ): Response

    @DELETE
    @Path("/{id}/schema")
    fun unassignSchema(
        @PathParam("id") id: String,
        @HeaderParam(HttpHeaders.IF_MATCH) ifMatch: String?,
    ): Response

    @PATCH
    @Path("/{id}/fields")
    fun patchValues(
        @PathParam("id") id: String,
        request: SetFieldValuesRequest,
        @HeaderParam(HttpHeaders.IF_MATCH) ifMatch: String?,
    ): Response
}
