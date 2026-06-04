package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Lets an assignee record an approve/reject decision on a pending workflow step (e.g. the
 * group-manager approval that gates a group-recipient sharing session). The step instance id is
 * delivered to assignees in the `workflow.step_assigned` notification payload.
 *
 * The decider identity is taken from the authenticated session — never the request body — and
 * [WorkflowEngineService.recordDecision] independently verifies the decider is a snapshotted
 * assignee of the step, so an attacker cannot decide on a step they were not assigned.
 */
@Path("/workflows/steps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WorkflowDecisionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val workflowEngineService: WorkflowEngineService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowDecisionResource::class.java)
    }

    @POST
    @Path("/{stepInstanceId}/decision")
    fun decide(
        @PathParam("stepInstanceId") stepInstanceId: String,
        payload: WorkflowDecisionRequest,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val stepId = runCatching { UUID.fromString(stepInstanceId) }.getOrNull()
            ?: return Response.status(Response.Status.BAD_REQUEST).entity(ResponseError("Invalid step instance id")).build()

        val decision = when (payload.decision?.trim()?.uppercase())
        {
            "APPROVE" -> Decision.APPROVE
            "REJECT" -> Decision.REJECT
            else -> return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseError("decision must be APPROVE or REJECT")).build()
        }

        return try
        {
            val result = workflowEngineService.recordDecision(
                stepInstanceId = stepId,
                decider = PrincipalRef.user(actor.id),
                decision = decision,
                reason = payload.reason?.trim()?.ifBlank { null },
            )
            Response.ok(
                WorkflowDecisionResponse(
                    instanceId = result.instanceId.toString(),
                    stepInstanceId = result.stepInstanceId.toString(),
                    stepStatus = result.stepStatus.name,
                    instanceStatus = result.instanceStatus.name,
                )
            ).build()
        }
        catch (e: IllegalStateException)
        {
            // Not an assignee, step not PENDING, or instance missing — a conflict with current state.
            logger.info("Workflow decision rejected for step {}: {}", stepId, e.message)
            Response.status(Response.Status.CONFLICT).entity(ResponseError(e.message)).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(Response.Status.NOT_FOUND).entity(ResponseError(e.message)).build()
        }
    }

    /**
     * List PENDING workflow steps assigned to the authenticated user (either
     * directly as a USER assignee or via a PRINCIPAL_GROUP assignee they're a member of).
     * Powers the "Pending approvals" inbox on app load so realtime push isn't the only path
     * to discovering pending tasks.
     */
    @GET
    @Path("/pending")
    fun listPending(): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        return try
        {
            val pending = workflowEngineService.listPendingForUser(actor.id)
            Response.ok(pending).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list pending workflow steps for user {}", actor.id, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to load pending approvals")).build()
        }
    }
}

@Serializable
data class WorkflowDecisionRequest(
    val decision: String? = null,
    val reason: String? = null,
)

@Serializable
data class WorkflowDecisionResponse(
    val instanceId: String,
    val stepInstanceId: String,
    val stepStatus: String,
    val instanceStatus: String,
)
