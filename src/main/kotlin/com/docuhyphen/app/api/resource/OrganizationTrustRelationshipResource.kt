package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.resource.model.OrganizationTrustDecisionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustPolicyUpdateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustRelationshipCreateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustSuspensionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustTerminationRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustWithdrawalRequest
import com.docuhyphen.app.api.service.organization.OrganizationTrustCommandService
import com.docuhyphen.app.api.service.organization.OrganizationTrustQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("organization-trust-relationships")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationTrustRelationshipResource @Inject constructor(
    private val commandService: OrganizationTrustCommandService,
    private val queryService: OrganizationTrustQueryService,
)
{
    @POST
    fun create(request: OrganizationTrustRelationshipCreateRequest): Response
    {
        val targetOrganizationId = parseId(request.targetOrganizationId, "Target organization")
        return Response.status(Response.Status.CREATED)
            .entity(commandService.requestRelationship(targetOrganizationId, request.requestMessage))
            .build()
    }

    @GET
    fun list(): Response = Response.ok(queryService.listRelationships()).build()

    @GET
    @Path("{relationshipId}")
    fun get(@PathParam("relationshipId") relationshipId: String): Response =
        Response.ok(queryService.getRelationship(parseId(relationshipId, "Relationship"))).build()

    @POST
    @Path("{relationshipId}/decisions")
    fun decide(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustDecisionRequest,
    ): Response = Response.ok(
        commandService.decide(
            parseId(relationshipId, "Relationship"),
            request.decision,
            request.reason,
            request.expectedVersion,
        ),
    ).build()

    @POST
    @Path("{relationshipId}/withdrawals")
    fun withdraw(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustWithdrawalRequest,
    ): Response = Response.ok(
        commandService.withdraw(
            parseId(relationshipId, "Relationship"),
            request.reason,
            request.expectedVersion,
        ),
    ).build()

    @POST
    @Path("{relationshipId}/suspensions")
    fun suspend(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustSuspensionRequest,
    ): Response = Response.status(Response.Status.CREATED).entity(
        commandService.suspend(parseId(relationshipId, "Relationship"), request.reason),
    ).build()

    @DELETE
    @Path("{relationshipId}/suspensions/{suspensionId}")
    fun resume(
        @PathParam("relationshipId") relationshipId: String,
        @PathParam("suspensionId") suspensionId: String,
    ): Response = Response.ok(
        commandService.resume(
            parseId(relationshipId, "Relationship"),
            parseId(suspensionId, "Suspension"),
        ),
    ).build()

    @POST
    @Path("{relationshipId}/terminations")
    fun terminate(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustTerminationRequest,
    ): Response = Response.ok(
        commandService.terminate(
            parseId(relationshipId, "Relationship"),
            request.reason,
            request.expectedVersion,
        ),
    ).build()

    @GET
    @Path("{relationshipId}/policies")
    fun policies(@PathParam("relationshipId") relationshipId: String): Response =
        Response.ok(queryService.getPolicies(parseId(relationshipId, "Relationship"))).build()

    @PATCH
    @Path("{relationshipId}/policies/{policyId}")
    fun updatePolicy(
        @PathParam("relationshipId") relationshipId: String,
        @PathParam("policyId") policyId: String,
        request: OrganizationTrustPolicyUpdateRequest,
    ): Response = Response.ok(
        commandService.updatePolicy(
            parseId(relationshipId, "Relationship"),
            parseId(policyId, "Policy"),
            request,
        ),
    ).build()

    private fun parseId(value: String?, label: String): UUID = try
    {
        UUID.fromString(value?.takeIf { it.isNotBlank() }
            ?: throw OrganizationTrustValidationException("$label ID is required"))
    }
    catch (exception: IllegalArgumentException)
    {
        throw OrganizationTrustValidationException("$label ID is invalid")
    }
}
