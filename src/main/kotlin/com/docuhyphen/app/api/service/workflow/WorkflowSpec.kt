@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.GroupRole
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Workflow DSL, the in-memory representation of `workflow_definition.steps_json`.
 *
 * Keep the wire format intentionally small and stable: every step has a `type` discriminator,
 * every assignee has a `kind` discriminator. Placeholder references (`$subject.foo`) are
 * resolved at instance-start time by [WorkflowAssigneeResolver] against
 * `WorkflowInstance.subjectDataJson`.
 *
 * Adding new step types or assignee kinds is non-breaking as long as old fields are kept
 * optional.
 */
@Serializable
data class WorkflowSpec(
    val steps: List<WorkflowStepSpec> = emptyList(),
)

@Serializable
data class WorkflowStepSpec(
    val type: WorkflowStepType,
    val assignees: List<AssigneeSpec> = emptyList(),
    val quorum: QuorumSpec = QuorumSpec.Any,
    val slaMinutes: Int? = null,
    val escalation: EscalationSpec? = null,
    val onApprove: StepOutcomeSpec? = null,
    val onReject: StepOutcomeSpec? = null,
    /** For ACTION steps: key of the registered WorkflowActionHandler bean to invoke (see Phase 2). */
    val actionHandlerKey: String? = null,
    /** For NOTIFICATION steps: ID of the Communication to use for subject/body. */
    val communicationId: String? = null,
    /**
     * For CONDITION steps: simple predicate evaluated against `subjectDataJson` fields.
     * Syntax: `"$subject.<key> <op> '<value>'"` where op is one of ==, !=, contains, startsWith.
     */
    val predicateExpression: String? = null,
    /** For CONDITION steps: outcome when [predicateExpression] evaluates to true. */
    val onTrue: StepOutcomeSpec? = null,
    /** For CONDITION steps: outcome when [predicateExpression] evaluates to false. */
    val onFalse: StepOutcomeSpec? = null,
    /** Optional addons: reminders, conditional reminders, etc. Evaluated by the scheduler tick. */
    val addons: List<StepAddonSpec> = emptyList(),
)

/** Sealed assignee model, every variant carries the data needed to resolve a principal set. */
@Serializable
@JsonClassDiscriminator("kind")
sealed class AssigneeSpec
{
    /** Direct principal, exact (kind, id) reference. */
    @Serializable
    @kotlinx.serialization.SerialName("PRINCIPAL")
    data class Principal(
        val principalKind: com.docuhyphen.app.api.model.entity.PrincipalKind,
        val principalId: String,                // UUID as string for JSON portability
    ) : AssigneeSpec()

    /** Members of a [com.docuhyphen.app.api.model.entity.PrincipalGroup] holding [groupRole]. */
    @Serializable
    @kotlinx.serialization.SerialName("GROUP_ROLE")
    data class GroupRoleAssignees(
        /** Literal group id OR `$subject.<field>` placeholder. */
        val groupIdRef: String,
        val groupRole: GroupRole,
    ) : AssigneeSpec()

    /** Anyone holding [roleName] in [scopeType] / [scopeIdRef]. */
    @Serializable
    @kotlinx.serialization.SerialName("ROLE")
    data class RoleAssignees(
        val roleName: String,
        val scopeType: RoleScopeType,
        /** Literal scope id OR `$subject.<field>` placeholder. Null for APP scope. */
        val scopeIdRef: String? = null,
    ) : AssigneeSpec()
}

/** How many assignees must approve before the step completes successfully. */
@Serializable
@JsonClassDiscriminator("kind")
sealed class QuorumSpec
{
    /** At least one approval. */
    @Serializable
    @kotlinx.serialization.SerialName("ANY")
    object Any : QuorumSpec()

    /** Every resolved assignee must approve. */
    @Serializable
    @kotlinx.serialization.SerialName("ALL")
    object All : QuorumSpec()

    /** N of the resolved assignees must approve. */
    @Serializable
    @kotlinx.serialization.SerialName("N_OF_M")
    data class NOfM(val n: Int) : QuorumSpec()
}

@Serializable
data class EscalationSpec(
    val afterSlaBreach: EscalationAction = EscalationAction.ESCALATE,
    val escalateTo: List<AssigneeSpec> = emptyList(),
)

enum class EscalationAction
{
    /** Reassign the step to [EscalationSpec.escalateTo] and reset SLA. */
    ESCALATE,

    /** Mark the step as REJECTED on SLA breach (no escalation). */
    AUTO_REJECT,

    /** Mark the step as APPROVED on SLA breach (rare; explicit opt-in). */
    AUTO_APPROVE,
}

/** What happens when a step finishes with approve/reject. */
@Serializable
data class StepOutcomeSpec(
    /** "END" or numeric index of next step (as string for JSON portability). */
    val nextStep: String = "END",
    /** Event to emit when this outcome is reached (e.g. `session.activated`). */
    val emit: String? = null,
)

/**
 * Optional behaviour attached to a step that is evaluated by the scheduler tick
 * independently of the step's human-decision path.
 */
@Serializable
@JsonClassDiscriminator("kind")
sealed class StepAddonSpec
{
    /** Send a reminder to [recipientRef] when the step is within [minutesBeforeDue] of its dueAt. */
    @Serializable
    @kotlinx.serialization.SerialName("REMINDER_BEFORE_DUE")
    data class ReminderBeforeDue(
        val minutesBeforeDue: Int,
        val recipientRef: AssigneeSpec,
        val communicationId: String? = null,
    ) : StepAddonSpec()

    /** Send a reminder if no decision has been recorded after [afterMinutes]. */
    @Serializable
    @kotlinx.serialization.SerialName("REMINDER_IF_NO_DECISION")
    data class ReminderIfNoDecision(
        val afterMinutes: Int,
        val recipientRef: AssigneeSpec,
        val communicationId: String? = null,
        /** When set, repeat the reminder every [repeatEveryMinutes] after the first fire. Null = send once. */
        val repeatEveryMinutes: Int? = null,
    ) : StepAddonSpec()
}

/**
 * Single shared JSON config, `ignoreUnknownKeys` makes the DSL forwards-compatible,
 * `classDiscriminator` matches what's already in this package.
 */
object WorkflowSpecJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun decode(stepsJson: String): WorkflowSpec = instance.decodeFromString(WorkflowSpec.serializer(), stepsJson)
    fun encode(spec: WorkflowSpec): String = instance.encodeToString(WorkflowSpec.serializer(), spec)

    fun encodeStep(step: WorkflowStepSpec): String =
        instance.encodeToString(WorkflowStepSpec.serializer(), step)

    fun decodeStep(json: String): WorkflowStepSpec =
        instance.decodeFromString(WorkflowStepSpec.serializer(), json)
}



