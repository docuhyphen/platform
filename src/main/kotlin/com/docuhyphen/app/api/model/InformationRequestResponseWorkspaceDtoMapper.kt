package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestAuthorActions
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestResponseEditLock
import java.sql.Timestamp

object InformationRequestResponseWorkspaceDtoMapper
{
    fun toDto(
        request: InformationRequestDto,
        title: String,
        respondentKey: String,
        templateVersion: InformationRequestTemplateVersionDto,
        responseETag: String,
        occurrences: List<InformationRequestGroupOccurrenceDto>,
        schemaAssignment: SchemaAssignmentDto?,
        responses: List<InformationRequestResponseDto>,
        supportingEvidenceLinks: List<InformationRequestSupportingEvidenceLinkDto> = emptyList(),
        evidenceUploadAvailable: Boolean = false,
        evidenceMalwareScanning: Boolean = false,
        executionStanding: InformationRequestExecutionStanding,
        requirementsAwaitingStepUp: Int = 0,
        canRequestSupplement: Boolean,
        authorActions: InformationRequestAuthorActions = InformationRequestAuthorActions(),
        creatableGroupKeys: Set<String> = emptySet(),
        exchangeAccepted: Boolean = true,
    ): InformationRequestResponseWorkspaceDto =
        InformationRequestResponseWorkspaceDto(
            request = request,
            title = title,
            respondentKey = respondentKey,
            templateVersion = templateVersion,
            responseETag = responseETag,
            occurrences = occurrences,
            schemaAssignment = schemaAssignment,
            responses = responses,
            supportingEvidenceLinks = supportingEvidenceLinks,
            evidenceUploadAvailable = evidenceUploadAvailable,
            evidenceMalwareScanning = evidenceMalwareScanning,
            executionStanding = InformationRequestExecutionStandingDtoMapper.toDto(executionStanding),
            requirementsAwaitingStepUp = requirementsAwaitingStepUp,
            canRequestSupplement = canRequestSupplement,
            authorActions = authorActionsDto(authorActions),
            creatableGroupKeys = creatableGroupKeys.sorted(),
            exchangeAccepted = exchangeAccepted,
        )

    fun authorActionsDto(actions: InformationRequestAuthorActions): InformationRequestAuthorActionsDto =
        InformationRequestAuthorActionsDto(
            issue = actions.issue,
            cancel = actions.cancel,
            supersede = actions.supersede,
            changeCompletionGate = actions.changeCompletionGate,
            manageParties = actions.manageParties,
            previewRecipients = actions.previewRecipients,
            manageClocks = actions.manageClocks,
            scheduleFollowUps = actions.scheduleFollowUps,
            createFollowUps = actions.createFollowUps,
            promoteFacts = actions.promoteFacts,
            revokeFacts = actions.revokeFacts,
            recordDecisions = actions.recordDecisions,
            issueAccessLinks = actions.issueAccessLinks,
        )

    fun linkDto(link: InformationRequestSupportingEvidenceLink): InformationRequestSupportingEvidenceLinkDto =
        InformationRequestSupportingEvidenceLinkDto(link.supportedRequirementId, link.supportingRequirementId)

    fun responseDto(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        response: InformationRequestResponse?,
        fieldProjection: SchemaAssignmentDto?,
        updatedAt: Timestamp,
        editLock: InformationRequestResponseEditLock? = null,
        evidenceFileCount: Int? = null,
    ): InformationRequestResponseDto =
        (response?.let { InformationRequestResponseDtoMapper.toDto(it, requirement, fieldProjection) }
            ?: InformationRequestResponseDto(
                informationRequestRequirementId = requirement.id,
                sourceTemplateRequirementId = requirement.sourceTemplateRequirementId,
                sourceTemplateBindingId = requirement.sourceTemplateBindingId,
                occurrencePath = requirement.occurrencePath,
                disposition = InformationRequestResponseDisposition.NOT_ANSWERED,
                fieldValueSetETag = fieldProjection?.etag,
                fieldValues = fieldProjection?.fields.orEmpty(),
                responseRevision = request.responseRevision,
                updatedAt = updatedAt,
            )).copy(editLock = editLock, evidenceFileCount = evidenceFileCount)
}
