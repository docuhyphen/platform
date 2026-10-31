package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
data class WorkflowSpec(
    val steps: List<WorkflowStepSpec> = emptyList(),
    val onComplete: String? = null,
    val onReject: String? = null,
    val applicability: ApplicabilitySpec? = null,
)

@Serializable
data class ApplicabilitySpec(
    val fieldConditions: List<FieldConditionSpec> = emptyList(),
    val requirementConditions: List<RequirementConditionSpec> = emptyList(),
)

@Serializable
data class RequirementConditionSpec(
    val templateRequirementId: String,
    val occurrencePath: String = "root",
    val requirementKey: String? = null,
    val valueType: FieldValueType,
    val operator: FieldOperator,
    val value: JsonElement? = null,
)

@Serializable
data class FieldConditionSpec(
    val fieldDefinitionId: String,
    val fieldKey: String? = null,
    val valueType: FieldValueType,
    val operator: FieldOperator,
    val value: JsonElement? = null,
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
    val actionHandlerKey: String? = null,
    val webhookEndpointId: String? = null,
    val webhookEventType: String? = null,
    val communicationId: String? = null,
    val predicateExpression: String? = null,
    val onTrue: StepOutcomeSpec? = null,
    val onFalse: StepOutcomeSpec? = null,
    val addons: List<StepAddonSpec> = emptyList(),
)

@Serializable
@JsonClassDiscriminator("kind")
sealed class AssigneeSpec
{
    @Serializable
    @kotlinx.serialization.SerialName("PRINCIPAL")
    data class Principal(
        val principalKind: com.docuhyphen.app.api.model.entity.PrincipalKind,
        val principalId: String,
    ) : AssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("GROUP_ROLE")
    data class GroupRoleAssignees(
        val groupIdRef: String,
        val groupRole: PrincipalGroupRoleName,
    ) : AssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("APP_ROLE")
    data class AppRoleAssignees(
        val roleName: AppRoleName,
    ) : AssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("ORGANIZATION_ROLE")
    data class OrganizationRoleAssignees(
        val roleName: OrganizationRoleName,
        val organizationIdRef: String,
    ) : AssigneeSpec()
}

@Serializable
@JsonClassDiscriminator("kind")
sealed class QuorumSpec
{
    @Serializable
    @kotlinx.serialization.SerialName("ANY")
    object Any : QuorumSpec()

    @Serializable
    @kotlinx.serialization.SerialName("ALL")
    object All : QuorumSpec()

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
    ESCALATE,

    AUTO_REJECT,

    AUTO_APPROVE,
}

@Serializable
data class StepOutcomeSpec(
    val nextStep: String = "END",
    val emit: String? = null,
)

@Serializable
@JsonClassDiscriminator("kind")
sealed class StepAddonSpec
{
    @Serializable
    @kotlinx.serialization.SerialName("REMINDER_BEFORE_DUE")
    data class ReminderBeforeDue(
        val minutesBeforeDue: Int,
        val recipientRef: AssigneeSpec,
        val communicationId: String? = null,
    ) : StepAddonSpec()

    @Serializable
    @kotlinx.serialization.SerialName("REMINDER_IF_NO_DECISION")
    data class ReminderIfNoDecision(
        val afterMinutes: Int,
        val recipientRef: AssigneeSpec,
        val communicationId: String? = null,
        val repeatEveryMinutes: Int? = null,
    ) : StepAddonSpec()
}
