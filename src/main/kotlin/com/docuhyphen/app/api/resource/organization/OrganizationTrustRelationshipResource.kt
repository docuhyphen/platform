package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
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
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import java.util.UUID
import org.slf4j.LoggerFactory

@Path("organization-trust-relationships")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationTrustRelationshipResource @Inject constructor(
    private val commandService: OrganizationTrustCommandService,
    private val queryService: OrganizationTrustQueryService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationTrustRelationshipResource::class.java)
    }

    @POST
    fun create(request: OrganizationTrustRelationshipCreateRequest): Response
    {
        return guarded("creation") {
            val targetOrganizationId = parseId(request.targetOrganizationId, "Target organization")
            Response.status(Response.Status.CREATED)
                .entity(commandService.requestRelationship(targetOrganizationId, request.requestMessage))
                .build()
        }
    }

    @GET
    fun list(): Response
    {
        val relationships = queryService.listRelationships()
        return Response.ok(
            object : GenericEntity<List<OrganizationTrustRelationshipDto>>(relationships) {},
        ).build()
    }

    @GET
    @Path("{relationshipId}")
    fun get(@PathParam("relationshipId") relationshipId: String): Response =
        Response.ok(queryService.getRelationship(parseId(relationshipId, "Relationship"))).build()

    @POST
    @Path("{relationshipId}/decisions")
    fun decide(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustDecisionRequest,
    ): Response = guarded("decision") {
        Response.ok(
            commandService.decide(
                parseId(relationshipId, "Relationship"),
                request.decision,
                request.reason,
                request.expectedVersion,
            ),
        ).build()
    }

    @POST
    @Path("{relationshipId}/withdrawals")
    fun withdraw(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustWithdrawalRequest,
    ): Response = guarded("withdrawal") {
        Response.ok(
            commandService.withdraw(
                parseId(relationshipId, "Relationship"),
                request.reason,
                request.expectedVersion,
            ),
        ).build()
    }

    @POST
    @Path("{relationshipId}/suspensions")
    fun suspend(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustSuspensionRequest,
    ): Response = guarded("suspension") {
        Response.status(Response.Status.CREATED).entity(
            commandService.suspend(parseId(relationshipId, "Relationship"), request.reason),
        ).build()
    }

    @DELETE
    @Path("{relationshipId}/suspensions/{suspensionId}")
    fun resume(
        @PathParam("relationshipId") relationshipId: String,
        @PathParam("suspensionId") suspensionId: String,
    ): Response = guarded("resumption") {
        Response.ok(
            commandService.resume(
                parseId(relationshipId, "Relationship"),
                parseId(suspensionId, "Suspension"),
            ),
        ).build()
    }

    @POST
    @Path("{relationshipId}/terminations")
    fun terminate(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustTerminationRequest,
    ): Response = guarded("termination") {
        Response.ok(
            commandService.terminate(
                parseId(relationshipId, "Relationship"),
                request.reason,
                request.expectedVersion,
            ),
        ).build()
    }

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
    ): Response = guarded("policy update") {
        Response.ok(
            commandService.updatePolicy(
                parseId(relationshipId, "Relationship"),
                parseId(policyId, "Policy"),
                request,
            ),
        ).build()
    }

    private fun guarded(operation: String, block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Organization trust {} refused by subscription policy", operation, e)
            throw e
        }
    }

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
