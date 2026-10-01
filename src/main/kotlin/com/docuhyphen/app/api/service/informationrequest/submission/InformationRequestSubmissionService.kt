package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.*
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSatisfactionService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewOpeningService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewRouting
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestSubmissionService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val contentCollector: InformationRequestSubmissionContentCollector,
    private val readinessEvaluator: InformationRequestSubmissionReadinessEvaluator,
    private val lockService: InformationRequestSubmissionLockService,
    private val requestRepository: InformationRequestRepository,
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val evidenceRepository: InformationRequestSubmissionEvidenceRepository,
    private val linkRepository: InformationRequestSubmissionSupportingLinkRepository,
    private val packageAttestationRepository: InformationRequestSubmissionPackageAttestationRepository,
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val reviewOpening: InformationRequestReviewOpeningService,
    private val satisfaction: InformationRequestSatisfactionService,
    private val clock: Clock,
    private val entityManager: EntityManager,
)
{
    @Transactional
    fun submit(command: SubmitInformationRequestPackageCommand): InformationRequestSubmissionResult
    {
        val stageKey = command.stageKey?.trim()?.ifBlank { null }
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = SUBMIT_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex("$SUBMIT_OPERATION|${command.requestId}|${stageKey.orEmpty()}"),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = createPackage(locked, command, stageKey)
                CommandMutationResult(result, reference(result))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command.access, decision.result)
        }
    }

    @Transactional
    fun withdraw(command: WithdrawInformationRequestPackageCommand): InformationRequestSubmissionResult
    {
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE, command.packageId),
            operation = WITHDRAW_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                "$WITHDRAW_OPERATION|${command.requestId}|${command.packageId}|${command.reasonCode?.trim().orEmpty()}",
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordWithdrawal(locked, command)
                CommandMutationResult(result, reference(result))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command.access, decision.result)
        }
    }

    private fun createPackage(
        locked: LockedInformationRequest,
        command: SubmitInformationRequestPackageCommand,
        stageKey: String?,
    ): InformationRequestSubmissionResult
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.SUBMIT)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_SUBMIT), request.id)

        val content = contentCollector.collect(request, stageKey)
        command.precondition.requireSatisfiedBy(InformationRequestETag.submissionOf(stageKey, content.contentHash))
        val submittedStages = lockService.submittedStages(request.id)
        val corrected = lockService.openCorrectionForStage(request.id, stageKey)
        if (stageKey in submittedStages && corrected == null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_ALREADY_SUBMITTED,
                "This part of the Information Request was already submitted",
            )
        }
        requireStageOrder(content, stageKey, submittedStages)

        val assessment = readinessEvaluator.assess(content, command.access)
        if (!assessment.readiness.ready) throw InformationRequestSubmissionIncompleteException(assessment.readiness)

        val now = Timestamp.from(clock.instant())
        val counted = assessment.attestations.values.flatMap { it.evaluation.counted }
        val itemHashes = content.items.associate { it.requirement.id to itemHash(it) }
        val reviewRequired =
            content.items.any { requiresReview(it, assessment.stateByRequirement.getValue(it.requirement.id)) }
        val stagesAfter = submittedStages + stageKey
        val completesRequest = content.stageOrder.isEmpty() || stagesAfter.containsAll(content.stageOrder)
        val withdrawnIds = withdrawalRepository.findForRequest(request.id).map { it.packageId }.toSet()
        val earlierPackages = packageRepository.findForRequest(request.id)

        val submission = packageRepository.save(
            InformationRequestSubmissionPackage().apply {
                informationRequestId = request.id
                packageNumber = packageRepository.nextPackageNumber(request.id)
                this.stageKey = stageKey
                templateVersionId = content.version.id
                schemaVersionId = content.version.schemaVersionId
                contentHashSha256 = content.contentHash
                manifestHashSha256 = sha256Hex(
                    (listOf(content.contentHash) + itemHashes.values.sorted() + counted.map { it.id.toString() }
                        .sorted())
                        .joinToString("\n"),
                )
                this.reviewRequired = reviewRequired
                this.completesRequest = completesRequest
                previousPackageId = corrected?.packageId ?: earlierPackages
                    .filter { it.stageKey == stageKey && it.id in withdrawnIds }
                    .maxByOrNull { it.packageNumber }
                    ?.id
                submittedByPrincipalKind = command.access.principal.kind
                submittedByPrincipalId = command.access.principal.id
                submittedBySessionRef = command.access.authorization.sessionRef?.takeIf { it.isNotBlank() }
                submittedAt = now
            },
        )
        entityManager.flush()
        writeMembers(submission, content, assessment, itemHashes, counted.map { it.id })

        request.responseRevision += 1
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        val details = mapOf(
            "submissionPackageId" to submission.id.toString(),
            "submissionPackageNumber" to submission.packageNumber.toString(),
            "contentHash" to submission.contentHashSha256,
        ) + (stageKey?.let { mapOf("stageKey" to it) } ?: emptyMap())
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.SUBMIT,
                actor = command.access.principal,
                idempotencyKey = "information_request.submission|${request.id}|${command.idempotencyKey}",
                details = details,
            ),
        )

        reviewOpening.onSubmitted(locked, submission, corrected, command.access.principal, command.idempotencyKey)
        satisfaction.closeIfSatisfied(locked, submission.id, command.access.principal, command.idempotencyKey)
        return result(request, submission.id)
    }

    private fun writeMembers(
        submission: InformationRequestSubmissionPackage,
        content: InformationRequestSubmissionContent,
        assessment: InformationRequestSubmissionAssessment,
        itemHashes: Map<UUID, String>,
        countedAttestationIds: List<UUID>,
    )
    {
        val items = content.items.map { item ->
            val state = assessment.stateByRequirement.getValue(item.requirement.id)
            val response = item.response.takeIf { state != InformationRequestCompletenessItemState.HIDDEN }
            val saved = itemRepository.save(
                InformationRequestSubmissionItem().apply {
                    packageId = submission.id
                    informationRequestId = submission.informationRequestId
                    informationRequestRequirementId = item.requirement.id
                    requirementRevisionId = item.revision.id
                    templateBindingId = item.binding.id
                    requirementKey = item.requirementKey
                    requirementType = item.requirementType
                    occurrencePath = item.requirement.occurrencePath
                    completenessState = state
                    disposition = response?.disposition ?: InformationRequestResponseDisposition.NOT_ANSWERED
                    narrative = response?.narrative
                    responseId = response?.id
                    responseRevision = response?.responseRevision
                    respondedByPrincipalKind = response?.recordedByPrincipalKind
                    respondedByPrincipalId = response?.recordedByPrincipalId
                    respondedBySessionRef = response?.recordedBySessionRef
                    fieldValueSetId = response?.fieldValueSetId
                    fieldValueRevisionId = item.fieldValueRevisionId.takeIf { response != null }
                    evidenceState = item.evidence?.state?.name
                    attestationState = assessment.attestations[item.requirement.id]?.evaluation?.state?.name
                    itemHashSha256 = itemHashes.getValue(item.requirement.id)
                },
            )
            item to saved
        }
        entityManager.flush()
        items.forEach { (item, saved) ->
            item.evidence?.members.orEmpty().forEach { member ->
                evidenceRepository.save(
                    InformationRequestSubmissionEvidence().apply {
                        packageId = submission.id
                        itemId = saved.id
                        informationRequestId = submission.informationRequestId
                        evidenceArtifactId = member.artifactId
                        evidenceVersionId = member.versionId
                        evidenceVersionNumber = member.versionNumber
                        documentVersionId = member.documentVersionId
                        contentHashAlgorithm = member.contentHashAlgorithm
                        contentHash = member.contentHash
                        contentLength = member.contentLength
                        contentVerification = member.contentVerification
                        conformance = member.conformance.name
                        inspectionAssessmentId = member.inspectionAssessmentId
                        malwareAssessmentId = member.malwareAssessmentId
                    },
                )
            }
        }
        content.links.forEach { link ->
            linkRepository.save(
                InformationRequestSubmissionSupportingLink().apply {
                    packageId = submission.id
                    informationRequestId = submission.informationRequestId
                    supportingEvidenceLinkId = link.id
                    supportedRequirementId = link.supportedRequirementId
                    supportingRequirementId = link.supportingRequirementId
                },
            )
        }
        countedAttestationIds.forEach { attestationId ->
            packageAttestationRepository.save(
                InformationRequestSubmissionPackageAttestation().apply {
                    packageId = submission.id
                    this.attestationId = attestationId
                    informationRequestId = submission.informationRequestId
                },
            )
        }
    }

    private fun recordWithdrawal(
        locked: LockedInformationRequest,
        command: WithdrawInformationRequestPackageCommand,
    ): InformationRequestSubmissionResult
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.WITHDRAW_SUBMISSION)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeRequest(
            command.access,
            listOf(Action.INFORMATION_REQUEST_SUBMIT, Action.INFORMATION_REQUEST_MANAGE_PARTIES),
            request.id,
        )
        command.precondition.requireSatisfiedBy(InformationRequestETag.responsesOf(request))
        val submission = packageRepository.findById(command.packageId)
            ?.takeIf { it.informationRequestId == request.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submission Package not found"
            )
        if (lockService.activePackages(request.id).none { it.id == submission.id })
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE,
                "This Submission Package was already withdrawn",
            )
        }
        reviewOpening.requireWithdrawable(submission, command.access.principal)

        val now = Timestamp.from(clock.instant())
        withdrawalRepository.save(
            InformationRequestSubmissionWithdrawal().apply {
                packageId = submission.id
                informationRequestId = request.id
                reasonCode = command.reasonCode?.trim()?.ifBlank { null }
                withdrawnByPrincipalKind = command.access.principal.kind
                withdrawnByPrincipalId = command.access.principal.id
                withdrawnBySessionRef = command.access.authorization.sessionRef?.takeIf { it.isNotBlank() }
                withdrawnAt = now
            },
        )
        request.responseRevision += 1
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.WITHDRAW_SUBMISSION,
                actor = command.access.principal,
                reasonCode = command.reasonCode,
                idempotencyKey = "information_request.withdrawal|${submission.id}|${command.idempotencyKey}",
                details = mapOf(
                    "submissionPackageId" to submission.id.toString(),
                    "submissionPackageNumber" to submission.packageNumber.toString(),
                ) + (submission.stageKey?.let { mapOf("stageKey" to it) } ?: emptyMap()),
            ),
        )
        return result(request, submission.id)
    }

    private fun replay(
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        recorded: CommandResultReference,
    ): InformationRequestSubmissionResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE) {
            "Command receipt does not reference a Submission Package"
        }
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW), locked.request.id)
        return result(locked.request, recorded.resourceId)
    }

    private fun result(request: InformationRequest, packageId: UUID) =
        InformationRequestSubmissionResult(
            request = request,
            submission = packageReader.view(request.id, packageId),
            requestETag = InformationRequestETag.aggregateOf(request),
            responseETag = InformationRequestETag.responsesOf(request),
        )

    private fun reference(result: InformationRequestSubmissionResult) = CommandResultReference(
        resourceType = ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE,
        resourceId = result.submission.submissionPackage.id,
        revision = result.submission.submissionPackage.packageNumber.toLong(),
        etag = result.responseETag,
    )

    private fun requireStageOrder(
        content: InformationRequestSubmissionContent,
        stageKey: String?,
        submitted: Set<String?>
    )
    {
        if (stageKey == null || content.version.submissionStageOrdering != InformationRequestSubmissionStageOrdering.SEQUENTIAL) return
        val earlier = content.stageOrder.takeWhile { it != stageKey }
        val missing = earlier.filterNot { it in submitted }
        if (missing.isNotEmpty())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBMISSION_STAGE_ORDER,
                "Stages are submitted in order and ${missing.joinToString()} has not been submitted",
            )
        }
    }

    private fun requiresReview(
        item: InformationRequestSubmissionContentItem,
        state: InformationRequestCompletenessItemState,
    ): Boolean =
        InformationRequestReviewRouting.routes(
            reviewPolicy = item.binding.reviewPolicy,
            requirementType = item.requirementType,
            completenessState = state,
            disposition = item.response?.takeIf { state != InformationRequestCompletenessItemState.HIDDEN }?.disposition,
            evidenceState = item.evidence?.state?.name,
        )

    private fun itemHash(item: InformationRequestSubmissionContentItem): String =
        sha256Hex(
            listOf(
                item.requirement.id,
                item.revision.id,
                item.binding.id,
                item.requirement.occurrencePath,
                item.response?.id ?: "",
                item.response?.responseRevision ?: "",
                item.response?.disposition ?: "",
                item.response?.narrative?.let(::sha256Hex) ?: "",
                item.fieldValueRevisionId ?: "",
                item.evidence?.members.orEmpty().joinToString(",") { "${it.versionId}:${it.contentHash ?: ""}" },
            ).joinToString("|"),
        )

    private fun sha256Hex(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        const val SUBMIT_OPERATION = "submit-information-request-package"
        const val WITHDRAW_OPERATION = "withdraw-information-request-package"
    }
}
