package com.docuhyphen.app.api.model.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class CreateInformationRequestSuccessorCommand(
    val sourceRequestId: UUID,
    val kind: InformationRequestLineageKind,
    val targetTemplateVersionId: UUID? = null,
    val sourcePackageId: UUID? = null,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class DefineInformationRequestRecurrenceCommand(
    val requestId: UUID,
    val intervalUnit: InformationRequestRecurrenceUnit,
    val intervalCount: Int,
    val firstDueAt: Instant,
    val maximumOccurrences: Int? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class CreateNextInformationRequestOccurrenceCommand(
    val requestId: UUID,
    val recurrenceId: UUID,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class DefineInformationRequestRefreshRuleCommand(
    val requestId: UUID,
    val requirementKey: String,
    val leadDays: Int,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestRecurrenceDefinition(
    val recurrence: InformationRequestRecurrence,
    val requestETag: String,
)

data class InformationRequestRefreshRuleDefinition(
    val refreshRule: InformationRequestRefreshRule,
    val requestETag: String,
)

data class RefreshInformationRequestCommand(
    val requestId: UUID,
    val refreshRuleId: UUID,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestRefreshSource(
    val packageId: UUID,
    val expiresOn: LocalDate,
)

data class InformationRequestFollowUpSpec(
    val kind: InformationRequestLineageKind,
    val targetTemplateVersionId: UUID? = null,
    val sourcePackageId: UUID? = null,
    val reasonCode: String? = null,
    val recurrenceId: UUID? = null,
    val recurrenceSequence: Int? = null,
    val refreshRuleId: UUID? = null,
)

data class InformationRequestSuccessorOccurrence(
    val requirementId: UUID,
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val occurrencePath: String,
)

data class InformationRequestPlannedCarryForward(
    val requirementId: UUID,
    val sourceItemId: UUID,
    val decision: InformationRequestCarryForwardDecision,
    val reasonCode: String?,
)

data class InformationRequestSuccessorResult(
    val source: InformationRequest,
    val successor: InformationRequest,
    val lineage: InformationRequestLineage,
    val carryForwards: List<InformationRequestCarryForward>,
    val successorETag: String,
)

data class InformationRequestRefreshRuleStanding(
    val rule: InformationRequestRefreshRule,
    val refreshDueOn: LocalDate? = null,
    val refreshDue: Boolean = false,
    val refreshed: Boolean = false,
)

data class InformationRequestLineageView(
    val request: InformationRequest,
    val source: InformationRequestLineage?,
    val successors: List<InformationRequestLineage>,
    val recurrence: InformationRequestRecurrence? = null,
    val series: InformationRequestRecurrence? = null,
    val nextOccurrenceDueAt: Instant? = null,
    val everyOccurrenceCreated: Boolean = false,
    val nextOccurrenceCreatable: Boolean = false,
    val refreshRules: List<InformationRequestRefreshRuleStanding> = emptyList(),
)

data class InformationRequestCarryForwardOffer(
    val carryForward: InformationRequestCarryForward,
    val sourceItem: InformationRequestSubmissionItem?,
    val sourceFieldValue: FieldValueRevisionValue?,
)
