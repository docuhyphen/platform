package com.docuhyphen.app.api.resource.workflow

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.WorkflowStepDecisionDtoMapper
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.WorkflowDecisionRequest
import com.docuhyphen.app.api.resource.workflow.operations.WorkflowDecisionResourceOperations
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class WorkflowDecisionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val workflowEngineService: WorkflowEngineService,
) : WorkflowDecisionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowDecisionResource::class.java)
    }

    override fun decide(
        stepInstanceId: String,
        payload: WorkflowDecisionRequest,
    ): Response
    {
        return try
        {
            val actor = RequestGuards.requireUser(authTokenContext)
            val stepId = RequestGuards.uuid(stepInstanceId, "Invalid step instance id")
            val decision = when (payload.decision?.trim()?.uppercase())
            {
                Decision.APPROVE.name -> Decision.APPROVE
                Decision.REJECT.name -> Decision.REJECT
                else -> throw RequestValidationException("decision must be APPROVE or REJECT")
            }

            val result = workflowEngineService.recordDecision(
                stepInstanceId = stepId,
                decider = PrincipalRef.user(actor.id),
                decision = decision,
                reason = payload.reason?.trim()?.ifBlank { null },
            )
            Response.ok(WorkflowStepDecisionDtoMapper.toDto(result)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                {
                    logger.info("Workflow decision rejected for step {}: {}", stepInstanceId, exception.message)
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()
                }

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error recording workflow decision", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while recording the workflow decision"))
                        .build()
                }
            }
        }
    }

    override fun listPending(): Response
    {
        return try
        {
            val actor = RequestGuards.requireUser(authTokenContext)
            val pending = workflowEngineService.listPendingForUser(actor.id).toTypedArray()

            Response.ok(pending).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to list pending workflow steps", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to load pending approvals"))
                        .build()
                }
            }
        }
    }
}
