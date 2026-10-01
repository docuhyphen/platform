package com.docuhyphen.app.api.resource.informationrequest.clock.operations

import com.docuhyphen.app.api.resource.model.DefineInformationRequestClockPolicyRequest
import com.docuhyphen.app.api.resource.model.InformationRequestClockPolicyDefinitionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-clock-policies")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestClockPolicyResourceOperations
{
    @GET
    fun list(): Response

    @GET
    @Path("/{policyId}")
    fun get(@PathParam("policyId") policyId: String): Response

    @POST
    fun define(request: DefineInformationRequestClockPolicyRequest?): Response

    @POST
    @Path("/{policyId}/versions")
    fun publishVersion(
        @PathParam("policyId") policyId: String,
        request: InformationRequestClockPolicyDefinitionRequest?,
    ): Response
}
