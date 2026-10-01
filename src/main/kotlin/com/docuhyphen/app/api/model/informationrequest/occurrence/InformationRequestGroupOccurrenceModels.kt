package com.docuhyphen.app.api.model.informationrequest.occurrence

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateBindingConfiguration
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.*

data class AddInformationRequestGroupOccurrenceCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val groupKey: String,
    val parentOccurrenceId: UUID? = null,
)

data class RemoveInformationRequestGroupOccurrenceCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val occurrenceId: UUID,
)

data class ReorderInformationRequestGroupOccurrencesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val groupKey: String,
    val parentOccurrenceId: UUID? = null,
    val orderedOccurrenceIds: List<UUID>,
)

data class InformationRequestGroupOccurrenceResult(
    val request: InformationRequest,
    val responseETag: String,
    val occurrences: List<InformationRequestGroupOccurrence>,
)

internal data class InformationRequestGroupOccurrenceTemplateData(
    val templateDefinitionId: UUID,
    val groupsByKey: Map<String, InformationRequestTemplateRequirementGroup>,
    val groupsById: Map<UUID, InformationRequestTemplateRequirementGroup>,
    val childrenByParent: Map<UUID?, List<InformationRequestTemplateRequirementGroup>>,
    val bindings: List<InformationRequestTemplateRequirementBinding>,
    val requirementsById: Map<UUID, InformationRequestTemplateRequirement>,
    val configuration: InformationRequestTemplateBindingConfiguration,
)
