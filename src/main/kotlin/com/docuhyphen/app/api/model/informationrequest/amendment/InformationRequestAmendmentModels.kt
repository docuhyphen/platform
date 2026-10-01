package com.docuhyphen.app.api.model.informationrequest.amendment

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.*

data class AmendInformationRequestCommand(
    val requestId: UUID,
    val targetTemplateVersionId: UUID? = null,
    val configuration: InformationRequestTemplateConfigurationRequest? = null,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestAmendmentRequirementChange(
    val requirementKey: String,
    val templateRequirementId: UUID,
    val kind: InformationRequestAmendmentChangeKind,
    val fromBindingId: UUID?,
    val toBindingId: UUID?,
    val fromStageKey: String?,
    val toStageKey: String?,
    val anchorChanged: Boolean,
)

data class InformationRequestAmendmentGroupChange(
    val groupKey: String,
    val removed: Boolean,
    val parentChanged: Boolean,
    val maximumOccurrences: Int?,
)

data class InformationRequestAmendmentPlan(
    val changes: List<InformationRequestAmendmentRequirementChange>,
    val groupChanges: List<InformationRequestAmendmentGroupChange>,
    val schemaChanged: Boolean,
    val submissionPolicyChanged: Boolean,
)

data class InformationRequestRequirementAdvance(
    val advancedRequirementIds: List<UUID>,
    val addedRequirementIds: List<UUID>,
)

data class InformationRequestAmendmentView(
    val amendment: InformationRequestAmendment,
    val changes: List<InformationRequestAmendmentChange>,
    val notices: List<InformationRequestNoticeIntent>,
    val noticeStates: Map<UUID, InformationRequestNoticeDeliveryState> = emptyMap(),
)

data class InformationRequestAmendmentResult(
    val request: InformationRequest,
    val amendment: InformationRequestAmendmentView,
    val requestETag: String,
)

data class InformationRequestReadableAmendment(
    val view: InformationRequestAmendmentView,
    val visibleTemplateRequirementIds: Set<UUID>,
    val visibleNoticePartyIds: Set<UUID>,
)

internal data class InformationRequestAmendmentPlacedRequirement(
    val requirement: InformationRequestTemplateRequirementDto,
    val section: InformationRequestTemplateSectionDto,
    val sectionIndex: Int,
    val index: Int,
)

internal data class InformationRequestAmendmentRequirementMeaning(
    val requirement: InformationRequestTemplateRequirementDto,
    val stageKey: String?,
    val condition: InformationRequestTemplateConditionRuleDto?,
    val anchor: InformationRequestTemplateGroupDto?,
)

internal data class InformationRequestAmendmentRequirementPresentation(
    val prompt: String,
    val helpText: String?,
    val sectionKey: String,
    val sectionPosition: Int,
    val position: Int,
)
