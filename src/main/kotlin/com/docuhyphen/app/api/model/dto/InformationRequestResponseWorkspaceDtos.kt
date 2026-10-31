package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestResponseWorkspaceDto(
    val request: InformationRequestDto,
    val title: String,
    val respondentKey: String,
    val templateVersion: InformationRequestTemplateVersionDto,
    val responseETag: String,
    val occurrences: List<InformationRequestGroupOccurrenceDto>,
    val schemaAssignment: SchemaAssignmentDto? = null,
    val responses: List<InformationRequestResponseDto> = emptyList(),
    val supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
    val evidenceUploadAvailable: Boolean = false,
    val evidenceMalwareScanning: Boolean = false,
    val executionStanding: InformationRequestExecutionStandingDto,
    val requirementsAwaitingStepUp: Int = 0,
    val canRequestSupplement: Boolean = false,
    val authorActions: InformationRequestAuthorActionsDto = InformationRequestAuthorActionsDto(),
    val creatableGroupKeys: List<String> = emptyList(),
    val exchangeAccepted: Boolean = true,
)

@Serializable
data class InformationRequestAuthorActionsDto(
    val issue: Boolean = false,
    val cancel: Boolean = false,
    val supersede: Boolean = false,
    val changeCompletionGate: Boolean = false,
    val manageParties: Boolean = false,
    val previewRecipients: Boolean = false,
    val manageClocks: Boolean = false,
    val scheduleFollowUps: Boolean = false,
    val createFollowUps: Boolean = false,
    val promoteFacts: Boolean = false,
    val revokeFacts: Boolean = false,
    val recordDecisions: Boolean = false,
    val issueAccessLinks: Boolean = false,
)

@Serializable
data class InformationRequestSupportingEvidenceLinkDto(
    @Serializable(with = UUIDSerializer::class) val supportedRequirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val supportingRequirementId: UUID,
)
