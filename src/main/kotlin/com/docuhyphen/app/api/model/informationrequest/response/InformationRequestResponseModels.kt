package com.docuhyphen.app.api.model.informationrequest.response

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import java.util.UUID

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

data class PatchInformationRequestResponsesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val patches: List<InformationRequestResponsePatch>,
    val confirmedHiddenResponseClears: Set<UUID> = emptySet(),
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

class InformationRequestStructuredResponseValidationContext(
    val request: InformationRequest,
    val requirementsById: Map<UUID, InformationRequestRequirement>,
    val patches: List<InformationRequestResponsePatch>,
    val activeResponses: List<InformationRequestResponse>,
)

class InformationRequestStructuredResponseValidationIssue(
    val kind: InformationRequestStructuredResponseValidationKind,
    val message: String,
    val requirementId: UUID? = null,
    val fieldContractId: UUID? = null,
)

internal data class InformationRequestFieldResponsePatchResult(
    val valueSetId: UUID,
    val projection: SchemaAssignmentDto,
    val changed: Boolean,
)
