package com.docuhyphen.app.api.resource.identity.operations

import com.docuhyphen.app.api.resource.model.LinkProviderInitiateRequest
import com.docuhyphen.app.api.resource.model.SetupPasswordRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/auth/identity-providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface IdentityProviderResourceOperations
{
    @GET
    fun getLinkedProviders(): Response

    @POST
    @Path("/link/initiate")
    fun initiateLinking(payload: LinkProviderInitiateRequest): Response

    @DELETE
    @Path("/{provider}")
    fun unlinkProvider(@PathParam("provider") providerName: String): Response

    @POST
    @Path("/internal/setup-password")
    fun setupPassword(payload: SetupPasswordRequest): Response
}
