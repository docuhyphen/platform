package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance

/**
 * SPI for ACTION workflow steps. Each implementation registers a unique [key] that maps to
 * the `actionHandlerKey` field in [WorkflowStepSpec]. Implementations must be
 * `@ApplicationScoped` CDI beans so that [DefaultWorkflowEngineService] can discover them
 * at startup via `Instance<WorkflowActionHandler>`.
 *
 * Implementations should be side-effect-safe to retry (best-effort; the engine does not
 * retry automatically in Phase 2 but the design must allow it later).
 */
interface WorkflowActionHandler
{
    /** Unique key matching `WorkflowStepSpec.actionHandlerKey`. */
    fun key(): String

    /**
     * Execute the action. The engine calls this inside the same transaction as the step
     * state update. A returned [ActionResult.success] == false marks the step REJECTED
     * and the workflow instance REJECTED.
     */
    fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
}

/**
 * Result of a [WorkflowActionHandler.execute] call.
 *
 * @param success true if the action completed normally; false to reject the step.
 * @param reason  optional human-readable reason surfaced in logs and future audit trails.
 */
data class ActionResult(
    val success: Boolean,
    val reason: String? = null,
)
