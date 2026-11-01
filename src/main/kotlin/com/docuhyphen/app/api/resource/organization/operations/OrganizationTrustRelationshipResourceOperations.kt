package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.model.OrganizationTrustDecisionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustPolicyUpdateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustRelationshipCreateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustSuspensionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustTerminationRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustWithdrawalRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("organization-trust-relationships")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface OrganizationTrustRelationshipResourceOperations {
    @POST
    fun create(request: OrganizationTrustRelationshipCreateRequest): Response

    @GET
    fun list(): Response

    @GET
    @Path("{relationshipId}")
    fun get(@PathParam("relationshipId") relationshipId: String): Response

    @POST
    @Path("{relationshipId}/decisions")
    fun decide(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustDecisionRequest,
    ): Response

    @POST
    @Path("{relationshipId}/withdrawals")
    fun withdraw(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustWithdrawalRequest,
    ): Response

    @POST
    @Path("{relationshipId}/suspensions")
    fun suspend(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustSuspensionRequest,
    ): Response

    @DELETE
    @Path("{relationshipId}/suspensions/{suspensionId}")
    fun resume(
        @PathParam("relationshipId") relationshipId: String,
        @PathParam("suspensionId") suspensionId: String,
    ): Response

    @POST
    @Path("{relationshipId}/terminations")
    fun terminate(
        @PathParam("relationshipId") relationshipId: String,
        request: OrganizationTrustTerminationRequest,
    ): Response

    @GET
    @Path("{relationshipId}/policies")
    fun policies(@PathParam("relationshipId") relationshipId: String): Response

    @PATCH
    @Path("{relationshipId}/policies/{policyId}")
    fun updatePolicy(
        @PathParam("relationshipId") relationshipId: String,
        @PathParam("policyId") policyId: String,
        request: OrganizationTrustPolicyUpdateRequest,
    ): Response
}
