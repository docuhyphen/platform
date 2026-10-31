package com.docuhyphen.app.api.model.informationrequest.response

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import java.util.*

enum class InformationRequestWorkspacePurpose {
    RESPONSE,
    MANAGEMENT,
}

sealed interface ResponseNarrativePatch
{
    data object Unchanged : ResponseNarrativePatch

    data object Clear : ResponseNarrativePatch

    data class Set(val value: String) : ResponseNarrativePatch
}

data class InformationRequestResponsePatch(
    val requirementId: UUID,
    val disposition: InformationRequestResponseDisposition? = null,
    val narrative: ResponseNarrativePatch = ResponseNarrativePatch.Unchanged,
    val fieldValues: ResponseFieldValuesPatch? = null,
)

data class ResponseFieldValuesPatch(
    val entries: List<FieldValueEntry>,
    val precondition: FieldsPrecondition,
)

data class HiddenResponseClearTarget(
    val requirementId: UUID,
    val sourceTemplateBindingId: UUID,
    val occurrencePath: String,
)

data class ConditionalResponseState(
    val requirement: InformationRequestRequirement,
    val ruleKey: String,
    val collectedFieldDefinitionId: UUID?,
    val evaluation: InformationRequestConditionEvaluationProjection,
    val response: InformationRequestResponse?,
)

data class PatchInformationRequestResponsesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val patches: List<InformationRequestResponsePatch>,
    val confirmedHiddenResponseClears: Set<UUID> = emptySet(),
    val respondentKey: String? = null,
)

data class InformationRequestResponseDraftResult(
    val request: InformationRequest,
    val responseETag: String,
    val responses: List<InformationRequestResponse>,
    val requirementsById: Map<UUID, InformationRequestRequirement> = emptyMap(),
    val fieldValueProjectionsByRequirementId: Map<UUID, SchemaAssignmentDto> = emptyMap(),
)

enum class InformationRequestStructuredResponseValidationKind
{
    CROSS_FIELD,
    CROSS_ROW,
    UNIT,
    CURRENCY,
    DATE_RANGE,
    PERIOD_COVERAGE,
    DUPLICATE,
}

enum class InformationRequestStructuredResponseValidationStage {
    RESPONSE_SAVE,
    OCCURRENCE_CHANGE,
    SUBMISSION,
}

class InformationRequestStructuredResponseValidationContext(
    val stage: InformationRequestStructuredResponseValidationStage,
    val request: InformationRequest,
    val requirementsById: Map<UUID, InformationRequestRequirement>,
    val bindingsById: Map<UUID, InformationRequestTemplateRequirementBinding> = emptyMap(),
    val occurrences: List<InformationRequestGroupOccurrence> = emptyList(),
    val responses: List<InformationRequestResponse> = emptyList(),
    val fieldValuesByOccurrencePath: Map<String, Map<UUID, CanonicalFieldValue?>> = emptyMap(),
    val conditionEvaluations: List<InformationRequestConditionEvaluationProjection> = emptyList(),
    val patches: List<InformationRequestResponsePatch> = emptyList(),
)

class InformationRequestStructuredResponseValidationIssue(
    val kind: InformationRequestStructuredResponseValidationKind,
    val message: String,
    val requirementId: UUID? = null,
    val occurrencePath: String? = null,
    val fieldContractId: UUID? = null,
)

data class InformationRequestConditionEffectDescriptor(
    val requirement: InformationRequestRequirement,
    val response: InformationRequestResponse,
    val ruleKey: String,
    val expressionVersion: Int,
    val evaluationState: InformationRequestConditionEvaluationState,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy,
    val effect: InformationRequestConditionEffectKind,
    val archiveId: UUID? = null,
)

data class InformationRequestHiddenResponseEnforcement(
    val changed: List<InformationRequestResponse> = emptyList(),
    val effects: List<InformationRequestConditionEffectDescriptor> = emptyList(),
)

data class InformationRequestArchivedEvidence(
    val artifactId: UUID,
    val versionId: UUID,
    val versionNumber: Int,
)

internal data class InformationRequestFieldResponsePatchResult(
    val valueSetId: UUID,
    val projection: SchemaAssignmentDto,
    val changed: Boolean,
)
