package com.docuhyphen.app.api.resource.variable.operations

import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.resource.RequestHeaders
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/sequences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SequenceDefinitionResourceOperations {
    @GET
    fun listSequences(@QueryParam("isActive") isActive: Boolean?): Response

    @POST
    fun createSequence(
        request: CreateSequenceRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @GET
    @Path("/{id}")
    fun getSequence(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}")
    fun updateSequence(
        @PathParam("id") id: String,
        request: UpdateSequenceRequest,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @DELETE
    @Path("/{id}")
    fun deleteSequence(
        @PathParam("id") id: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response

    @PATCH
    @Path("/{id}/reset")
    fun resetCounter(
        @PathParam("id") id: String,
        @HeaderParam(RequestHeaders.REQUEST_ID) requestId: String?,
    ): Response
}
