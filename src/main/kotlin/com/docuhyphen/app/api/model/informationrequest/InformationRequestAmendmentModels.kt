package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAmendment
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChange
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeIntent
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.util.UUID

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
