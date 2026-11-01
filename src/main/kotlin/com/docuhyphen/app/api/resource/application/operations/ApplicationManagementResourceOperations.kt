package com.docuhyphen.app.api.resource.application.operations

import com.docuhyphen.app.api.resource.model.CreateApplicationRequestBody
import com.docuhyphen.app.api.resource.model.UpdateCapabilitiesRequestBody
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/admin/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ApplicationManagementResourceOperations
{
    @GET
    fun listApplications(): Response

    @POST
    fun createApplication(body: CreateApplicationRequestBody): Response

    @GET
    @Path("/{id}")
    fun getApplication(@PathParam("id") id: String): Response

    @POST
    @Path("/{id}/credentials/rotate")
    fun rotateCredentials(@PathParam("id") id: String): Response

    @PUT
    @Path("/{id}/capabilities")
    fun updateCapabilities(
        @PathParam("id") id: String,
        body: UpdateCapabilitiesRequestBody,
    ): Response

    @DELETE
    @Path("/{id}")
    fun deactivateApplication(@PathParam("id") id: String): Response
}
