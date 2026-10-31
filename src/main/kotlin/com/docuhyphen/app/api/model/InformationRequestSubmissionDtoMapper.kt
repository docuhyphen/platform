package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionRecord
import com.docuhyphen.app.api.model.informationrequest.submission.*
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.*

object InformationRequestSubmissionDtoMapper
{
    fun toDto(
        readable: InformationRequestReadableSubmissionPackage,
        caller: PrincipalRef
    ): InformationRequestSubmissionPackageDto =
        toDto(readable.view, readable.visibleRequirementIds, caller, readable.fieldValues, readable.evidenceVersions)
            .copy(current = readable.current, withdrawable = readable.withdrawable)

    fun toDto(
        member: InformationRequestSubmissionEvidence,
        version: InformationRequestEvidenceVersionRecord?,
    ): InformationRequestSubmissionEvidenceDto = InformationRequestSubmissionEvidenceDto(
        artifactId = member.evidenceArtifactId,
        evidenceVersionId = member.evidenceVersionId,
        versionNumber = member.evidenceVersionNumber,
        declaredFileName = version?.declaredFileName,
        documentVersionId = member.documentVersionId,
        contentHashAlgorithm = member.contentHashAlgorithm,
        contentHash = member.contentHash,
        contentLength = member.contentLength,
        conformance = member.conformance,
    )

    fun toDto(
        preview: InformationRequestSubmissionPreview,
        caller: PrincipalRef
    ): InformationRequestSubmissionPreviewDto =
        InformationRequestSubmissionPreviewDto(
            informationRequestId = preview.requestId,
            stageKey = preview.stageKey,
            submissionMode = preview.submissionMode,
            submissionStageOrdering = preview.submissionStageOrdering,
            submissionETag = preview.submissionETag,
            ready = preview.readiness.ready,
            problems = preview.readiness.problems.map(::toDto),
            undisclosedProblemCount = preview.readiness.undisclosedProblemCount,
            groupProblems = preview.readiness.groupProblems.map(::toDto),
            attestations = preview.attestations.map { toDto(it, caller) },
            stages = preview.stages.map {
                InformationRequestSubmissionStageDto(
                    stageKey = it.stageKey,
                    submittedPackageId = it.submittedPackage?.id,
                    submittedPackageNumber = it.submittedPackage?.packageNumber,
                    submitted = it.submittedPackage != null,
                    correctionOpen = it.correctionOpen,
                )
            },
            canSubmit = preview.canSubmit,
            correctionOpen = preview.correctionOpen,
            packages = preview.packages.map { toDto(it, caller) },
            scopeItems = preview.scopeItems.map { item ->
                InformationRequestSubmissionScopeItemDto(
                    requirementId = item.requirementId,
                    templateBindingId = item.templateBindingId,
                    requirementKey = item.requirementKey,
                    requirementType = item.requirementType,
                    prompt = item.prompt,
                    occurrencePath = item.occurrencePath,
                    disposition = item.disposition,
                    narrative = item.narrative,
                    fieldContractId = item.fieldValue?.fieldContractId,
                    fieldValueType = item.fieldValue?.valueType,
                    fieldValue = item.fieldValue?.value,
                    fieldValueCleared = item.fieldValue?.cleared ?: false,
                    evidenceFileCount = item.evidenceFileCount,
                )
            },
            undisclosedScopeItemCount = preview.undisclosedScopeItemCount,
        )

    fun toDto(
        result: InformationRequestSubmissionResult,
        readable: InformationRequestReadableSubmissionPackage,
        caller: PrincipalRef,
    ): InformationRequestSubmissionResultDto = InformationRequestSubmissionResultDto(
        requestState = result.request.state,
        requestETag = result.requestETag,
        responseETag = result.responseETag,
        submission = toDto(readable, caller),
    )

    fun toDto(
        result: InformationRequestSubmissionAttestationResult,
        caller: PrincipalRef
    ): InformationRequestSubmissionAttestationResultDto =
        InformationRequestSubmissionAttestationResultDto(
            attestation = toDto(result.attestation, caller),
            state = result.evaluation?.evaluation?.state,
            submissionETag = result.submissionETag,
        )

    private fun toDto(
        standing: InformationRequestAttestationStanding,
        caller: PrincipalRef
    ): InformationRequestAttestationStatusDto
    {
        val policy = standing.evaluated.policy
        val evaluation = standing.evaluated.evaluation
        return InformationRequestAttestationStatusDto(
            requirementId = standing.requirementId,
            requirementKey = standing.requirementKey,
            prompt = standing.prompt,
            state = evaluation.state,
            requiredRoles = policy.requiredRoles,
            missingRoles = evaluation.missingRoles,
            ordering = policy.ordering,
            assentCount = evaluation.assentCount,
            requiredAssentCount = evaluation.requiredAssentCount,
            minimumAuthenticationStrength = policy.minimumAuthenticationStrength,
            externalSignatureReference = policy.externalSignatureReference,
            validityHours = policy.validityHours,
            callerCanAttest = standing.callerCanAttest,
            waitingForRoles = standing.waitingForRoles,
            callerDecision = standing.attestations
                .lastOrNull { it.principalKind == caller.kind && it.principalId == caller.id }
                ?.decision,
            attestations = standing.attestations.map { toDto(it, caller) },
            attestingParties = standing.attestingParties.map { acting ->
                InformationRequestAttestingPartyDto(
                    partyId = acting.party.id,
                    role = acting.role,
                    throughGroup = acting.party.principalKind == PrincipalKind.PRINCIPAL_GROUP,
                    delegated = acting.delegatedAuthorityId != null,
                )
            },
        )
    }

    private fun toDto(
        view: InformationRequestSubmissionPackageView,
        visibleRequirementIds: Set<UUID>,
        caller: PrincipalRef,
        fieldValues: Map<UUID, FieldValueRevisionValue>,
        evidenceVersions: Map<UUID, InformationRequestEvidenceVersionRecord>,
    ): InformationRequestSubmissionPackageDto
    {
        val submission = view.submissionPackage
        val visibleItems = view.items.filter { it.informationRequestRequirementId in visibleRequirementIds }
        val evidenceByItem = view.evidence.groupBy { it.itemId }
        return InformationRequestSubmissionPackageDto(
            id = submission.id,
            informationRequestId = submission.informationRequestId,
            packageNumber = submission.packageNumber,
            stageKey = submission.stageKey,
            templateVersionId = submission.templateVersionId,
            schemaVersionId = submission.schemaVersionId,
            contentHash = submission.contentHashSha256,
            manifestHash = submission.manifestHashSha256,
            reviewRequired = submission.reviewRequired,
            completesRequest = submission.completesRequest,
            previousPackageId = submission.previousPackageId,
            submittedAt = submission.submittedAt,
            submittedByCaller = submission.submittedByPrincipalKind == caller.kind &&
                    submission.submittedByPrincipalId == caller.id,
            withdrawn = view.withdrawal != null,
            withdrawnAt = view.withdrawal?.withdrawnAt,
            withdrawalReasonCode = view.withdrawal?.reasonCode,
            items = visibleItems.map { item ->
                toDto(
                    item,
                    evidenceByItem[item.id].orEmpty(),
                    item.fieldValueRevisionId?.let(fieldValues::get),
                    evidenceVersions,
                )
            },
            attestations = view.attestations
                .filter { it.attestationRequirementId in visibleRequirementIds }
                .map { toDto(it, caller) },
            supportingEvidenceLinks = view.links
                .filter { it.supportedRequirementId in visibleRequirementIds && it.supportingRequirementId in visibleRequirementIds }
                .map {
                    InformationRequestSupportingEvidenceLinkDto(
                        it.supportedRequirementId,
                        it.supportingRequirementId
                    )
                },
            undisclosedItemCount = view.items.size - visibleItems.size,
        )
    }

    private fun toDto(
        attestation: InformationRequestSubmissionAttestation,
        caller: PrincipalRef,
    ): InformationRequestSubmissionAttestationDto = InformationRequestSubmissionAttestationDto(
        id = attestation.id,
        requirementId = attestation.attestationRequirementId,
        stageKey = attestation.stageKey,
        partyRole = attestation.partyRole,
        decision = attestation.decision,
        refusalReason = attestation.refusalReason,
        authenticationStrength = attestation.authenticationStrength,
        externalSignatureReference = attestation.externalSignatureReference,
        attestedAt = attestation.attestedAt,
        expiresAt = attestation.expiresAt,
        attestedByCaller = attestation.principalKind == caller.kind && attestation.principalId == caller.id,
        madeUnderDelegatedAuthority = attestation.delegatedAuthorityId != null,
    )

    private fun toDto(problem: InformationRequestSubmissionItemProblem): InformationRequestSubmissionProblemDto =
        InformationRequestSubmissionProblemDto(
            requirementId = problem.requirementId,
            requirementKey = problem.requirementKey,
            occurrencePath = problem.occurrencePath,
            code = problem.code,
        )

    fun refusal(message: String, reasonCode: String, readiness: InformationRequestSubmissionReadiness) =
        InformationRequestSubmissionRefusalDto(
            errorMessage = message,
            reasonCode = reasonCode,
            problems = readiness.problems.map(::toDto),
            undisclosedProblemCount = readiness.undisclosedProblemCount,
            groupProblems = readiness.groupProblems.map(::toDto),
        )

    private fun toDto(problem: InformationRequestSubmissionGroupProblem): InformationRequestSubmissionGroupProblemDto =
        InformationRequestSubmissionGroupProblemDto(
            groupKey = problem.groupKey,
            parentOccurrencePath = problem.parentOccurrencePath,
            minOccurrences = problem.minOccurrences,
            maxOccurrences = problem.maxOccurrences,
            occurrenceCount = problem.occurrenceCount,
        )

    private fun toDto(
        item: InformationRequestSubmissionItem,
        evidence: List<InformationRequestSubmissionEvidence>,
        fieldValue: FieldValueRevisionValue?,
        evidenceVersions: Map<UUID, InformationRequestEvidenceVersionRecord>,
    ) = InformationRequestSubmissionItemDto(
        id = item.id,
        requirementId = item.informationRequestRequirementId,
        requirementKey = item.requirementKey,
        requirementType = item.requirementType,
        occurrencePath = item.occurrencePath,
        completenessState = item.completenessState,
        disposition = item.disposition,
        narrative = item.narrative,
        fieldValue = fieldValue?.value,
        fieldValueCleared = fieldValue?.cleared ?: false,
        evidenceState = item.evidenceState,
        attestationState = item.attestationState,
        evidence = evidence.map { member -> toDto(member, evidenceVersions[member.evidenceVersionId]) },
    )
}
