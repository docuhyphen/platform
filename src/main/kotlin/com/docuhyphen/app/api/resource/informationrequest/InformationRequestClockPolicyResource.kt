package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.model.DefineInformationRequestClockPolicyRequest
import com.docuhyphen.app.api.resource.model.InformationRequestClockPolicyDefinitionRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestClockService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-clock-policies")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestClockPolicyResource @Inject constructor(
    policies: InformationRequestClockPolicyService,
    clocks: InformationRequestClockService,
)
{
    private val endpoint = InformationRequestClockEndpoint(policies, clocks)

    @GET
    fun list(): Response
    {
        return try
        {
            endpoint.listPolicies()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy list failed", exception)
        }
    }

    @GET
    @Path("/{policyId}")
    fun get(@PathParam("policyId") policyId: String): Response
    {
        return try
        {
            endpoint.policy(InformationRequestCommandHttp.uuid(policyId, "clock policy id"))
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy read failed", exception)
        }
    }

    @POST
    fun define(request: DefineInformationRequestClockPolicyRequest?): Response
    {
        return try
        {
            endpoint.definePolicy(request)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy definition failed", exception)
        }
    }

    @POST
    @Path("/{policyId}/versions")
    fun publishVersion(
        @PathParam("policyId") policyId: String,
        request: InformationRequestClockPolicyDefinitionRequest?,
    ): Response
    {
        return try
        {
            endpoint.publishVersion(InformationRequestCommandHttp.uuid(policyId, "clock policy id"), request)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request clock policy version publication failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestClockPolicyResource::class.java)
    }
}
