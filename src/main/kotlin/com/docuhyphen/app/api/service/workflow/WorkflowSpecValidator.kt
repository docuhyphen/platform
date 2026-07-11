package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.CommunicationRepository
import com.docuhyphen.app.api.repository.WorkflowTriggerEventRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

data class WorkflowValidationError(
    val code: String,
    val stepIndex: Int?,
    val fieldPath: String,
    val message: String,
)

class WorkflowSpecValidationException(val errors: List<WorkflowValidationError>) :
    IllegalArgumentException(errors.joinToString("; ") { it.message })

/**
 * The scope, organization, and creator a definition is being saved under. Used to check that a
 * referenced resource (such as a NOTIFICATION communication) is actually visible to the definition.
 */
data class WorkflowDefinitionScope(
    val scope: WorkflowScope,
    val organizationId: UUID?,
    val createdByAppUserId: UUID?,
)

/**
 * Structural and referenced-resource validation for a [WorkflowSpec] before it is persisted.
 *
 * Save-time validation is the authoritative gate: it rejects definitions that would otherwise fail
 * open, auto-complete, or stall at runtime (empty definitions, unreachable steps, approvals with no
 * assignees, impossible static quorum, unregistered action handlers, escalation with no SLA, and so
 * on). Every failure is reported as a structured [WorkflowValidationError] with a stable code, the
 * offending step index, a field path, and a safe message, so the frontend can map errors without
 * parsing free-form text.
 *
 * Predicate and route target checks reuse the same [ConditionPredicateService] and in-range rules
 * the runtime engine uses, so save-time and runtime behavior cannot diverge.
 */
@ApplicationScoped
class WorkflowSpecValidator @Inject constructor(
    private val triggerEventRepository: WorkflowTriggerEventRepository,
    private val predicateService: ConditionPredicateService,
    private val actionHandlerCatalog: WorkflowActionHandlerCatalog,
    private val communicationRepository: CommunicationRepository,
)
{
    /**
     * Runs every structural and referenced-resource check and throws once with the full set of
     * errors. This is the single entry point the definition service calls on create, update, and
     * clone. [definitionScope] enables scope-aware referenced-resource checks; pass null to skip
     * the scope-visibility portion (still validating existence and active state).
     */
    fun validate(spec: WorkflowSpec, triggerEvent: String, definitionScope: WorkflowDefinitionScope?)
    {
        val errors = buildList {
            addAll(collectRouteErrors(spec))
            addAll(collectStructureErrors(spec))
            addAll(collectStepErrors(spec, definitionScope))
            addAll(collectPredicateErrors(spec, triggerEvent))
        }
        if (errors.isNotEmpty()) throw WorkflowSpecValidationException(errors)
    }

    /**
     * Rejects step outcomes whose `nextStep` is neither the terminal `END` marker nor an in-range
     * step index. This closes the gap where a deleted step left dangling numeric references that
     * could route an instance to the wrong step or an out-of-range index at runtime.
     */
    fun validateRoutes(spec: WorkflowSpec)
    {
        throwIfAny(collectRouteErrors(spec))
    }

    fun validatePredicates(spec: WorkflowSpec, triggerEvent: String)
    {
        throwIfAny(collectPredicateErrors(spec, triggerEvent))
    }

    // -------------------------------------------------------------------------
    // Route target validation
    // -------------------------------------------------------------------------

    private fun collectRouteErrors(spec: WorkflowSpec): List<WorkflowValidationError>
    {
        val stepCount = spec.steps.size
        return spec.steps.flatMapIndexed { index, step ->
            outcomeFields(step).mapNotNull { (field, outcome) ->
                val target = outcome.nextStep
                if (target.equals(END_TARGET, ignoreCase = true)) return@mapNotNull null
                val parsed = target.toIntOrNull()
                if (parsed != null && parsed in 0 until stepCount) return@mapNotNull null
                WorkflowValidationError(
                    code = "WORKFLOW_ROUTE_INVALID_TARGET",
                    stepIndex = index,
                    fieldPath = "steps[$index].$field.nextStep",
                    message = "Step ${index + 1} has an invalid route target \"$target\"",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Structural validation (empty definition, reachability, cycles, self-routes)
    // -------------------------------------------------------------------------

    private fun collectStructureErrors(spec: WorkflowSpec): List<WorkflowValidationError>
    {
        if (spec.steps.isEmpty())
        {
            return listOf(
                WorkflowValidationError(
                    code = "WORKFLOW_EMPTY_DEFINITION",
                    stepIndex = null,
                    fieldPath = "steps",
                    message = "A workflow must have at least one step",
                ),
            )
        }

        val errors = mutableListOf<WorkflowValidationError>()
        val stepCount = spec.steps.size

        // Only in-range numeric targets participate in the graph; out-of-range targets are already
        // reported by route validation, so ignore them here to avoid double-counting and indexing
        // past the step list.
        val edges = spec.steps.map { step ->
            stepTargets(step).mapNotNull { it.toIntOrNull()?.takeIf { i -> i in 0 until stepCount } }
        }

        // Self-routes: a step that routes to itself never terminates.
        spec.steps.forEachIndexed { index, step ->
            outcomeFields(step).forEach { (field, outcome) ->
                if (outcome.nextStep.toIntOrNull() == index)
                {
                    errors += WorkflowValidationError(
                        code = "WORKFLOW_ROUTE_SELF",
                        stepIndex = index,
                        fieldPath = "steps[$index].$field.nextStep",
                        message = "Step ${index + 1} routes to itself",
                    )
                }
            }
        }

        // Unreachable steps: not reachable from the entry step (index 0).
        val reachable = reachableFrom(0, edges)
        for (index in 0 until stepCount)
        {
            if (index !in reachable)
            {
                errors += WorkflowValidationError(
                    code = "WORKFLOW_STEP_UNREACHABLE",
                    stepIndex = index,
                    fieldPath = "steps[$index]",
                    message = "Step ${index + 1} is not reachable from the start of the workflow",
                )
            }
        }

        // No path to END: every step must be able to reach a terminal edge, otherwise an instance
        // that lands on it stalls or loops forever.
        val canReachEnd = stepsThatReachEnd(spec, edges)
        for (index in 0 until stepCount)
        {
            if (index in reachable && index !in canReachEnd)
            {
                errors += WorkflowValidationError(
                    code = "WORKFLOW_STEP_NO_PATH_TO_END",
                    stepIndex = index,
                    fieldPath = "steps[$index]",
                    message = "Step ${index + 1} has no path to the end of the workflow",
                )
            }
        }

        // Cycles: a directed cycle among steps means the workflow can loop indefinitely.
        val cycle = detectCycle(edges)
        if (cycle != null)
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_CYCLE_DETECTED",
                stepIndex = cycle,
                fieldPath = "steps[$cycle]",
                message = "The workflow contains a cycle involving step ${cycle + 1}",
            )
        }

        return errors
    }

    // -------------------------------------------------------------------------
    // Per-step validation (assignees, quorum, action handlers, escalation, SLA, addons)
    // -------------------------------------------------------------------------

    private fun collectStepErrors(
        spec: WorkflowSpec,
        definitionScope: WorkflowDefinitionScope?,
    ): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        spec.steps.forEachIndexed { index, step ->
            errors += assigneeFormatErrors(index, step)
            when (step.type)
            {
                WorkflowStepType.APPROVAL ->
                {
                    errors += approvalErrors(index, step)
                    errors += escalationErrors(index, step)
                }
                WorkflowStepType.CONDITION -> errors += conditionBranchErrors(index, step)
                WorkflowStepType.ACTION -> errors += actionErrors(index, step)
                WorkflowStepType.NOTIFICATION ->
                    errors += communicationErrors(index, "communicationId", step.communicationId, definitionScope)
                WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE -> Unit
            }
            errors += slaErrors(index, step)
            errors += addonErrors(index, step, definitionScope)
        }
        return errors
    }

    private fun approvalErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        if (step.assignees.isEmpty())
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_APPROVAL_NO_ASSIGNEES",
                stepIndex = index,
                fieldPath = "steps[$index].assignees",
                message = "Approval step ${index + 1} has no assignees",
            )
        }
        val quorum = step.quorum
        if (quorum is QuorumSpec.NOfM)
        {
            if (quorum.n < 1)
            {
                errors += WorkflowValidationError(
                    code = "WORKFLOW_QUORUM_INVALID",
                    stepIndex = index,
                    fieldPath = "steps[$index].quorum.n",
                    message = "Approval step ${index + 1} requires a quorum of at least 1",
                )
            }
            // When every assignee is a literal principal, the total is knowable now and an n larger
            // than the total can never be satisfied. Group/role/placeholder assignees resolve
            // dynamically, so their feasibility is re-checked when an instance starts.
            val staticTotal = staticAssigneeCount(step.assignees)
            if (staticTotal != null && staticTotal > 0 && quorum.n > staticTotal)
            {
                errors += WorkflowValidationError(
                    code = "WORKFLOW_QUORUM_EXCEEDS_ASSIGNEES",
                    stepIndex = index,
                    fieldPath = "steps[$index].quorum.n",
                    message = "Approval step ${index + 1} requires ${quorum.n} approvals but only has $staticTotal assignees",
                )
            }
        }
        return errors
    }

    private fun escalationErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val escalation = step.escalation ?: return emptyList()
        val errors = mutableListOf<WorkflowValidationError>()
        // Escalation is driven by the SLA deadline; without an SLA the step never becomes overdue,
        // so the escalation configuration would never fire.
        if (step.slaMinutes == null)
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_ESCALATION_NO_SLA",
                stepIndex = index,
                fieldPath = "steps[$index].escalation",
                message = "Approval step ${index + 1} configures escalation but has no SLA",
            )
        }
        if (escalation.afterSlaBreach == EscalationAction.ESCALATE && escalation.escalateTo.isEmpty())
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_ESCALATION_NO_TARGETS",
                stepIndex = index,
                fieldPath = "steps[$index].escalation.escalateTo",
                message = "Approval step ${index + 1} escalates on SLA breach but has no escalation targets",
            )
        }
        return errors
    }

    private fun conditionBranchErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        if (step.onTrue == null)
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_CONDITION_MISSING_BRANCH",
                stepIndex = index,
                fieldPath = "steps[$index].onTrue",
                message = "Condition step ${index + 1} is missing its true branch",
            )
        }
        if (step.onFalse == null)
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_CONDITION_MISSING_BRANCH",
                stepIndex = index,
                fieldPath = "steps[$index].onFalse",
                message = "Condition step ${index + 1} is missing its false branch",
            )
        }
        return errors
    }

    private fun actionErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val handlerKey = step.actionHandlerKey
        if (handlerKey.isNullOrBlank())
        {
            return listOf(
                WorkflowValidationError(
                    code = "WORKFLOW_ACTION_NO_HANDLER",
                    stepIndex = index,
                    fieldPath = "steps[$index].actionHandlerKey",
                    message = "Action step ${index + 1} has no action handler",
                ),
            )
        }
        if (!actionHandlerCatalog.isRegistered(handlerKey))
        {
            return listOf(
                WorkflowValidationError(
                    code = "WORKFLOW_ACTION_UNKNOWN_HANDLER",
                    stepIndex = index,
                    fieldPath = "steps[$index].actionHandlerKey",
                    message = "Action step ${index + 1} references an unknown action handler",
                ),
            )
        }
        if (handlerKey == WebhookWorkflowActionHandler.KEY)
        {
            return webhookConfigErrors(index, step)
        }
        return emptyList()
    }

    private fun webhookConfigErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        val endpointId = step.webhookEndpointId
        if (endpointId.isNullOrBlank())
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_WEBHOOK_CONFIG_MISSING",
                stepIndex = index,
                fieldPath = "steps[$index].webhookEndpointId",
                message = "Webhook action step ${index + 1} has no endpoint configured",
            )
        }
        else if (!isUuid(endpointId))
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_WEBHOOK_ENDPOINT_INVALID",
                stepIndex = index,
                fieldPath = "steps[$index].webhookEndpointId",
                message = "Webhook action step ${index + 1} has a malformed endpoint reference",
            )
        }
        if (step.webhookEventType.isNullOrBlank())
        {
            errors += WorkflowValidationError(
                code = "WORKFLOW_WEBHOOK_CONFIG_MISSING",
                stepIndex = index,
                fieldPath = "steps[$index].webhookEventType",
                message = "Webhook action step ${index + 1} has no event type configured",
            )
        }
        return errors
    }

    private fun slaErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val sla = step.slaMinutes ?: return emptyList()
        if (sla <= 0)
        {
            return listOf(
                WorkflowValidationError(
                    code = "WORKFLOW_SLA_INVALID",
                    stepIndex = index,
                    fieldPath = "steps[$index].slaMinutes",
                    message = "Step ${index + 1} has a non-positive SLA",
                ),
            )
        }
        return emptyList()
    }

    private fun addonErrors(
        index: Int,
        step: WorkflowStepSpec,
        definitionScope: WorkflowDefinitionScope?,
    ): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        step.addons.forEachIndexed { addonIndex, addon ->
            val base = "steps[$index].addons[$addonIndex]"
            when (addon)
            {
                is StepAddonSpec.ReminderBeforeDue ->
                {
                    if (addon.minutesBeforeDue <= 0)
                    {
                        errors += reminderIntervalError(index, "$base.minutesBeforeDue")
                    }
                    errors += assigneeErrorFor(index, "$base.recipientRef", addon.recipientRef)
                    errors += communicationErrors(index, "$base.communicationId", addon.communicationId, definitionScope)
                }
                is StepAddonSpec.ReminderIfNoDecision ->
                {
                    if (addon.afterMinutes <= 0)
                    {
                        errors += reminderIntervalError(index, "$base.afterMinutes")
                    }
                    if (addon.repeatEveryMinutes != null && addon.repeatEveryMinutes <= 0)
                    {
                        errors += reminderIntervalError(index, "$base.repeatEveryMinutes")
                    }
                    errors += assigneeErrorFor(index, "$base.recipientRef", addon.recipientRef)
                    errors += communicationErrors(index, "$base.communicationId", addon.communicationId, definitionScope)
                }
            }
        }
        return errors
    }

    private fun reminderIntervalError(index: Int, fieldPath: String) = WorkflowValidationError(
        code = "WORKFLOW_REMINDER_INTERVAL_INVALID",
        stepIndex = index,
        fieldPath = fieldPath,
        message = "Step ${index + 1} has a non-positive reminder interval",
    )

    // -------------------------------------------------------------------------
    // Assignee well-formedness
    // -------------------------------------------------------------------------

    private fun assigneeFormatErrors(index: Int, step: WorkflowStepSpec): List<WorkflowValidationError>
    {
        val errors = mutableListOf<WorkflowValidationError>()
        step.assignees.forEachIndexed { i, assignee ->
            errors += assigneeErrorFor(index, "steps[$index].assignees[$i]", assignee)
        }
        step.escalation?.escalateTo?.forEachIndexed { i, assignee ->
            errors += assigneeErrorFor(index, "steps[$index].escalation.escalateTo[$i]", assignee)
        }
        return errors
    }

    private fun assigneeErrorFor(
        index: Int,
        fieldPath: String,
        assignee: AssigneeSpec,
    ): List<WorkflowValidationError>
    {
        val malformed = when (assignee)
        {
            is AssigneeSpec.Principal -> !isUuid(assignee.principalId)
            is AssigneeSpec.GroupRoleAssignees -> !isPlaceholderOrUuid(assignee.groupIdRef)
            is AssigneeSpec.OrganizationRoleAssignees -> !isPlaceholderOrUuid(assignee.organizationIdRef)
            is AssigneeSpec.AppRoleAssignees -> false
        }
        if (!malformed) return emptyList()
        return listOf(
            WorkflowValidationError(
                code = "WORKFLOW_ASSIGNEE_MALFORMED",
                stepIndex = index,
                fieldPath = fieldPath,
                message = "Step ${index + 1} has a malformed assignee reference",
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Referenced communication existence, active state, and scope visibility
    // -------------------------------------------------------------------------

    private fun communicationErrors(
        index: Int,
        fieldPath: String,
        communicationId: String?,
        definitionScope: WorkflowDefinitionScope?,
    ): List<WorkflowValidationError>
    {
        if (communicationId.isNullOrBlank()) return emptyList()
        val id = runCatching { UUID.fromString(communicationId) }.getOrNull()
            ?: return listOf(communicationError("WORKFLOW_COMMUNICATION_NOT_FOUND", index, fieldPath,
                "Step ${index + 1} references a malformed communication"))

        val communication = communicationRepository.findById(id)
        if (communication == null || communication.isDeleted)
        {
            return listOf(communicationError("WORKFLOW_COMMUNICATION_NOT_FOUND", index, fieldPath,
                "Step ${index + 1} references a communication that does not exist"))
        }
        if (!communication.isActive)
        {
            return listOf(communicationError("WORKFLOW_COMMUNICATION_INACTIVE", index, fieldPath,
                "Step ${index + 1} references an inactive communication"))
        }
        if (definitionScope != null && !communicationVisibleTo(communication, definitionScope))
        {
            return listOf(communicationError("WORKFLOW_COMMUNICATION_OUT_OF_SCOPE", index, fieldPath,
                "Step ${index + 1} references a communication outside this workflow's scope"))
        }
        return emptyList()
    }

    private fun communicationVisibleTo(
        communication: com.docuhyphen.app.api.model.entity.Communication,
        definitionScope: WorkflowDefinitionScope,
    ): Boolean = when (communication.scope)
    {
        CommunicationScope.PLATFORM -> communication.isTemplate
        CommunicationScope.ORG ->
            definitionScope.organizationId != null &&
                communication.organizationId == definitionScope.organizationId
        CommunicationScope.PERSONAL ->
            definitionScope.createdByAppUserId != null &&
                communication.createdByAppUserId == definitionScope.createdByAppUserId
    }

    private fun communicationError(code: String, index: Int, fieldPath: String, message: String) =
        WorkflowValidationError(code = code, stepIndex = index, fieldPath = fieldPath, message = message)

    // -------------------------------------------------------------------------
    // Predicate validation
    // -------------------------------------------------------------------------

    private fun collectPredicateErrors(spec: WorkflowSpec, triggerEvent: String): List<WorkflowValidationError>
    {
        val fields = triggerEventRepository.findByEventName(triggerEvent)?.subjectFieldsJson?.let { fieldsJson ->
            runCatching {
                WorkflowSpecJson.instance.decodeFromString(
                    ListSerializer(WorkflowSubjectField.serializer()),
                    fieldsJson,
                )
            }.getOrDefault(emptyList())
        }.orEmpty()
        return spec.steps.mapIndexedNotNull { index, step ->
            if (step.type != WorkflowStepType.CONDITION) return@mapIndexedNotNull null
            when (val result = predicateService.validate(step.predicateExpression, fields))
            {
                is PredicateResult.Valid -> null
                is PredicateResult.Invalid -> WorkflowValidationError(
                    code = "WORKFLOW_CONDITION_${result.code.name}",
                    stepIndex = index,
                    fieldPath = "steps[$index].predicateExpression",
                    message = "Condition step ${index + 1} has an invalid predicate (${result.code.name.lowercase()})",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Graph helpers
    // -------------------------------------------------------------------------

    /** Outgoing step-to-step routing targets (as raw strings) for graph analysis. */
    private fun stepTargets(step: WorkflowStepSpec): List<String> = when (step.type)
    {
        // An approval reject always terminates at runtime, so only the approve branch is a step edge.
        WorkflowStepType.APPROVAL -> listOfNotNull(step.onApprove?.nextStep)
        WorkflowStepType.CONDITION -> listOfNotNull(step.onTrue?.nextStep, step.onFalse?.nextStep)
        else -> listOfNotNull(step.onApprove?.nextStep)
    }

    /** True if a step has at least one outgoing edge that terminates the instance. */
    private fun stepHasTerminalEdge(step: WorkflowStepSpec): Boolean = when (step.type)
    {
        // Reject always terminates, and a missing approve outcome defaults to END.
        WorkflowStepType.APPROVAL -> true
        WorkflowStepType.CONDITION ->
            terminatesToEnd(step.onTrue) || terminatesToEnd(step.onFalse)
        else -> terminatesToEnd(step.onApprove)
    }

    private fun terminatesToEnd(outcome: StepOutcomeSpec?): Boolean =
        outcome == null || outcome.nextStep.equals(END_TARGET, ignoreCase = true)

    private fun reachableFrom(entry: Int, edges: List<List<Int>>): Set<Int>
    {
        val visited = mutableSetOf<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(entry)
        while (stack.isNotEmpty())
        {
            val current = stack.removeLast()
            if (!visited.add(current)) continue
            edges.getOrNull(current)?.forEach { stack.addLast(it) }
        }
        return visited
    }

    private fun stepsThatReachEnd(spec: WorkflowSpec, edges: List<List<Int>>): Set<Int>
    {
        // Reverse reachability from the steps that have a direct terminal edge.
        val reverse = Array(spec.steps.size) { mutableListOf<Int>() }
        edges.forEachIndexed { from, targets -> targets.forEach { to -> reverse[to].add(from) } }
        val canReach = mutableSetOf<Int>()
        val stack = ArrayDeque<Int>()
        spec.steps.forEachIndexed { index, step -> if (stepHasTerminalEdge(step)) stack.addLast(index) }
        while (stack.isNotEmpty())
        {
            val current = stack.removeLast()
            if (!canReach.add(current)) continue
            reverse[current].forEach { stack.addLast(it) }
        }
        return canReach
    }

    /** Returns a step index participating in a directed cycle, or null when the graph is acyclic. */
    private fun detectCycle(edges: List<List<Int>>): Int?
    {
        val state = IntArray(edges.size) // 0 = unvisited, 1 = on stack, 2 = done
        fun dfs(node: Int): Int?
        {
            state[node] = 1
            for (next in edges[node])
            {
                if (state[next] == 1) return next
                if (state[next] == 0) dfs(next)?.let { return it }
            }
            state[node] = 2
            return null
        }
        for (index in edges.indices)
        {
            if (state[index] == 0) dfs(index)?.let { return it }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    private fun outcomeFields(step: WorkflowStepSpec): List<Pair<String, StepOutcomeSpec>> =
        buildList {
            step.onApprove?.let { add("onApprove" to it) }
            step.onReject?.let { add("onReject" to it) }
            step.onTrue?.let { add("onTrue" to it) }
            step.onFalse?.let { add("onFalse" to it) }
        }

    /** Total assignees when all are literal principals; null when any resolve dynamically. */
    private fun staticAssigneeCount(assignees: List<AssigneeSpec>): Int?
    {
        if (assignees.isEmpty()) return 0
        if (assignees.any { it !is AssigneeSpec.Principal }) return null
        return assignees.map { (it as AssigneeSpec.Principal).principalId }.distinct().size
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun isPlaceholderOrUuid(value: String): Boolean =
        value.startsWith(SUBJECT_PLACEHOLDER_PREFIX) || isUuid(value)

    private fun throwIfAny(errors: List<WorkflowValidationError>)
    {
        if (errors.isNotEmpty()) throw WorkflowSpecValidationException(errors)
    }

    private companion object
    {
        const val END_TARGET = "END"
        const val SUBJECT_PLACEHOLDER_PREFIX = "\$subject."
    }
}
