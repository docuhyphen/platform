package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForward
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineage
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.time.Instant
import java.util.UUID

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

data class RefreshInformationRequestCommand(
    val requestId: UUID,
    val refreshRuleId: UUID,
    val access: RequestAccessContext,
    val idempotencyKey: String,
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

data class InformationRequestLineageView(
    val request: InformationRequest,
    val source: InformationRequestLineage?,
    val successors: List<InformationRequestLineage>,
)

data class InformationRequestCarryForwardOffer(
    val carryForward: InformationRequestCarryForward,
    val sourceItem: InformationRequestSubmissionItem?,
    val sourceFieldValue: FieldValueRevisionValue?,
)
