package com.docuhyphen.app.api.resource.scim.operations

import com.docuhyphen.app.api.model.dto.ScimPatchRequest
import com.docuhyphen.app.api.model.dto.ScimUser
import com.docuhyphen.app.api.resource.scim.ScimMediaTypes
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/scim/v2/Users")
@Produces(MediaType.APPLICATION_JSON, ScimMediaTypes.SCIM_JSON)
@Consumes(MediaType.APPLICATION_JSON, ScimMediaTypes.SCIM_JSON)
interface ScimUserResourceOperations {
    @GET
    fun list(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        @QueryParam("filter") filter: String?,
        @QueryParam("startIndex") startIndex: Int?,
        @QueryParam("count") count: Int?,
    ): Response

    @GET
    @Path("/{id}")
    fun getById(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathParam("id") id: String,
    ): Response

    @POST
    fun create(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        payload: ScimUser,
    ): Response

    @PUT
    @Path("/{id}")
    fun replace(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathParam("id") id: String,
        payload: ScimUser,
    ): Response

    @PATCH
    @Path("/{id}")
    fun patch(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathParam("id") id: String,
        payload: ScimPatchRequest,
    ): Response

    @DELETE
    @Path("/{id}")
    fun delete(
        @HeaderParam(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathParam("id") id: String,
    ): Response
}
