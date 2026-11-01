package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
import com.docuhyphen.app.api.resource.model.OrganizationTrustDecisionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustPolicyUpdateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustRelationshipCreateRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustSuspensionRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustTerminationRequest
import com.docuhyphen.app.api.resource.model.OrganizationTrustWithdrawalRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.OrganizationTrustRelationshipResourceOperations
import com.docuhyphen.app.api.service.organization.OrganizationTrustCommandService
import com.docuhyphen.app.api.service.organization.OrganizationTrustIdentifierParser
import com.docuhyphen.app.api.service.organization.OrganizationTrustQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationTrustRelationshipResource @Inject constructor(
    private val commandService: OrganizationTrustCommandService,
    private val queryService: OrganizationTrustQueryService,
    private val identifierParser: OrganizationTrustIdentifierParser,
) : OrganizationTrustRelationshipResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationTrustRelationshipResource::class.java)
        private const val RELATIONSHIP_LABEL = "Relationship"
        private const val FAILURE_MESSAGE = "Trusted Organization request failed"
    }

    override fun create(request: OrganizationTrustRelationshipCreateRequest): Response
    {
        return try
        {
            val targetOrganizationId = identifierParser.parse(request.targetOrganizationId, "Target organization")
            Response.status(CREATED)
                .entity(commandService.requestRelationship(targetOrganizationId, request.requestMessage))
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust creation refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error creating organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun list(): Response
    {
        return try
        {
            val relationships = queryService.listRelationships()
            Response.ok(
                object : GenericEntity<List<OrganizationTrustRelationshipDto>>(relationships)
                {},
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error listing organization trust relationships", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun get(relationshipId: String): Response
    {
        return try
        {
            Response.ok(queryService.getRelationship(identifierParser.parse(relationshipId, RELATIONSHIP_LABEL))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error fetching organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun decide(
        relationshipId: String,
        request: OrganizationTrustDecisionRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                commandService.decide(
                    identifierParser.parse(relationshipId, RELATIONSHIP_LABEL),
                    request.decision,
                    request.reason,
                    request.expectedVersion,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust decision refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error deciding organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun withdraw(
        relationshipId: String,
        request: OrganizationTrustWithdrawalRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                commandService.withdraw(
                    identifierParser.parse(relationshipId, RELATIONSHIP_LABEL),
                    request.reason,
                    request.expectedVersion,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust withdrawal refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error withdrawing organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun suspend(
        relationshipId: String,
        request: OrganizationTrustSuspensionRequest,
    ): Response
    {
        return try
        {
            Response.status(CREATED).entity(
                commandService.suspend(identifierParser.parse(relationshipId, RELATIONSHIP_LABEL), request.reason),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust suspension refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error suspending organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun resume(
        relationshipId: String,
        suspensionId: String,
    ): Response
    {
        return try
        {
            Response.ok(
                commandService.resume(
                    identifierParser.parse(relationshipId, RELATIONSHIP_LABEL),
                    identifierParser.parse(suspensionId, "Suspension"),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust resumption refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error resuming organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun terminate(
        relationshipId: String,
        request: OrganizationTrustTerminationRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                commandService.terminate(
                    identifierParser.parse(relationshipId, RELATIONSHIP_LABEL),
                    request.reason,
                    request.expectedVersion,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust termination refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error terminating organization trust relationship", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun policies(relationshipId: String): Response
    {
        return try
        {
            Response.ok(queryService.getPolicies(identifierParser.parse(relationshipId, RELATIONSHIP_LABEL))).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException -> throw exception

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error fetching organization trust policies", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }

    override fun updatePolicy(
        relationshipId: String,
        policyId: String,
        request: OrganizationTrustPolicyUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                commandService.updatePolicy(
                    identifierParser.parse(relationshipId, RELATIONSHIP_LABEL),
                    identifierParser.parse(policyId, "Policy"),
                    request,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn("Organization trust policy update refused by subscription policy", exception)
                    throw exception
                }

                is OrganizationTrustException -> throw exception

                else ->
                {
                    logger.error("Error updating organization trust policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError(FAILURE_MESSAGE)).build()
                }
            }
        }
    }
}
