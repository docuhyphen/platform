package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestResponseDraftService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val responseStore: InformationRequestResponseStore,
    private val mutationGuard: InformationRequestResponseMutationGuard,
    private val patchValidator: InformationRequestResponsePatchValidator,
    private val fieldWriter: InformationRequestResponseFieldWriter,
    private val activeResponses: InformationRequestActiveResponseReader,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val structuredResponseValidationService: InformationRequestStructuredResponseValidationService,
    private val validationContextLoader: InformationRequestStructuredResponseValidationContextLoader,
    private val lockService: InformationRequestSubmissionLockService,
    private val hiddenResponsePolicies: InformationRequestHiddenResponsePolicyEnforcer,
    private val conditionEffectRecorder: InformationRequestConditionEffectRecorder,
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
)
{
    @Transactional
    fun patch(requested: PatchInformationRequestResponsesCommand): InformationRequestResponseDraftResult {
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            requested.requestId,
            requested.access
        )
        mutationGuard.lock(requested.requestId, "Information Request not found")
        val command = requested.copy(access = noAuthAccess.requireCurrent(requested.requestId, requested.access))
        InformationRequestRespondentKey.requireHeldBy(command.respondentKey, command.access.principal)
        return runOnce(command) {
            mutate(command)
        }
    }

    private fun runOnce(
        command: PatchInformationRequestResponsesCommand,
        mutation: () -> InformationRequestResponseDraftResult,
    ): InformationRequestResponseDraftResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = PATCH_RESPONSES_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = fingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation()
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayResponseDraftResult(command, decision.result)
        }
    }

    private fun mutate(command: PatchInformationRequestResponsesCommand): InformationRequestResponseDraftResult
    {
        val locked = mutationGuard.lock(command.requestId, "Information Request not found")
        val request = locked.request
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, command.access)
        command.precondition.requireSatisfiedBy(InformationRequestETag.responsesOf(request))

        mutationGuard.requireResponseChangeAllowed(locked, RESPONSE_CHANGE_REFUSAL)
        requireVisibleTargets(
            command.access,
            command.patches.map { it.requirementId } + command.confirmedHiddenResponseClears,
        )

        val allRequirements = requirementRepository.findForRequest(request.id).associateBy { it.id }
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        val requirements = allRequirements.filterValues {
            InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths)
        }
        patchValidator.requirePatchTargetsAvailable(command.patches, allRequirements, activeOccurrencePaths)
        lockService.requireUnlocked(request.id, command.patches.map { it.requirementId })
        structuredResponseValidationService.requireDistinctPatchTargets(command.patches)
        command.patches.forEach { patch -> authorize(command.access, patch.requirementId) }
        hiddenResponsePolicies.requireTargetsShown(
            request,
            command.patches.map { requirements.getValue(it.requirementId) })
        patchValidator.requirePermittedDispositions(command.patches, requirements)
        val currentRevisions = revisionRepository.findCurrentForRequest(request.id)
            .associateBy { it.informationRequestRequirementId }
        patchValidator.requireCurrentRevisions(command.patches, requirements, currentRevisions)
        patchValidator.requireNarratives(request.id, command.patches, requirements)

        val now = Timestamp.from(Instant.now())
        val changed = mutableListOf<InformationRequestResponse>()
        val revisionNumber = request.responseRevision + 1
        val fieldResultsByRequirement =
            fieldWriter.writeBatchedFieldValues(request.id, requirements, command.access, command.patches)
        fieldWriter.clearValuesOfValuelessAnswers(request.id, requirements, command.access, command.patches)

        command.patches.forEach { patch ->
            val requirement = requirements.getValue(patch.requirementId)
            val fieldResult = fieldResultsByRequirement[patch.requirementId]
            val updated = applyPatch(
                current = responseStore.findCurrentForUpdate(request.id, requirement.id),
                request = request,
                requirement = requirement,
                revision = currentRevisions.getValue(requirement.id),
                patch = patch,
                fieldValueSetId = fieldResult?.valueSetId,
                fieldValuesChanged = fieldResult?.changed ?: false,
                revisionNumber = revisionNumber,
                access = command.access,
                now = now,
            )
            if (updated != null) changed += updated
        }

        val enforcement = hiddenResponsePolicies.enforce(
            request = request,
            requirements = requirements,
            confirmedClears = command.confirmedHiddenResponseClears,
            access = command.access,
            revisionNumber = revisionNumber,
            now = now,
            patchedRequirementIds = command.patches.map { it.requirementId }.toSet(),
        )
        changed += enforcement.changed
        validateSavedResponse(request, command)

        if (changed.isNotEmpty())
        {
            val previousState = request.state
            request.responseRevision += 1
            request.updatedAt = now
            requestRepository.update(request)
            val transition = transitionHistory.record(
                InformationRequestTransitionHistoryCommand(
                    request = request,
                    fromState = previousState,
                    toState = request.state,
                    mutation = InformationRequestMutation.SAVE_RESPONSE,
                    actor = InformationRequestAuditActor.of(command.access),
                    auditIdempotencyKey = historyIdempotencyKey(request.id, command.idempotencyKey),
                    details = conditionEffectRecorder.auditDetails(enforcement.effects) + answeredDetails(
                        changed,
                        command
                    ),
                ),
            )
            conditionEffectRecorder.record(transition, enforcement.effects, command.access, now)
        }

        val disclosed = command.patches.map { it.requirementId }.toSet()
        val fieldsRemovedByConditions = enforcement.effects.any {
            it.effect == InformationRequestConditionEffectKind.CLEARED || it.effect == InformationRequestConditionEffectKind.ARCHIVED
        }
        return InformationRequestResponseDraftResult(
            request = request,
            responseETag = InformationRequestETag.responsesOf(request),
            responses = activeResponses.activeResponses(request.id)
                .filter { it.informationRequestRequirementId in disclosed },
            requirementsById = requirements.filterKeys { it in disclosed },
            fieldValueProjectionsByRequirementId = fieldResultsByRequirement.mapValues { (id, result) ->
                if (fieldsRemovedByConditions) fieldWriter.readFieldValues(
                    request.id,
                    requirements.getValue(id),
                    command.access
                )
                else activeResponses.activeFieldProjection(request.id, requirements.getValue(id), result.projection)
            },
        )
    }

    private fun answeredDetails(
        changed: List<InformationRequestResponse>,
        command: PatchInformationRequestResponsesCommand,
    ): Map<String, String> {
        val patched = command.patches.map { it.requirementId }.toSet()
        val answered = changed.map { it.informationRequestRequirementId }.filter { it in patched }.distinct()
        return if (answered.isEmpty()) emptyMap() else mapOf("answeredRequirementIds" to answered.joinToString(","))
    }

    private fun validateSavedResponse(request: InformationRequest, command: PatchInformationRequestResponsesCommand) {
        if (!structuredResponseValidationService.appliesTo(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)) return
        structuredResponseValidationService.validate(
            validationContextLoader.load(
                request,
                InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE,
                command.patches
            ),
        ) { requirementId -> mayView(command.access, requirementId) }
    }

    private fun requireVisibleTargets(access: RequestAccessContext, requirementIds: Collection<UUID>) {
        val hidden = requirementIds.distinct().filterNot { mayView(access, it) }
        if (hidden.isEmpty()) return
        if (hidden.all { awaitsVerification(access, it) }) throw InformationRequestVerificationRequiredException(
            VERIFICATION_REFUSAL
        )
        throw ForbiddenException(RESPOND_REFUSAL)
    }

    private fun awaitsVerification(access: RequestAccessContext, requirementId: UUID): Boolean =
        !access.authorization.mfaSatisfied &&
                mayView(access.copy(authorization = access.authorization.copy(mfaSatisfied = true)), requirementId)

    private fun authorize(access: RequestAccessContext, requirementId: UUID)
    {
        if (!mayRespond(access, requirementId)) throw ForbiddenException(RESPOND_REFUSAL)
    }

    private fun mayRespond(access: RequestAccessContext, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) !is Decision.Deny

    private fun mayView(access: RequestAccessContext, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) is Decision.Allow

    private fun applyPatch(
        current: InformationRequestResponse?,
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        revision: InformationRequestRequirementRevision,
        patch: InformationRequestResponsePatch,
        fieldValueSetId: UUID?,
        fieldValuesChanged: Boolean,
        revisionNumber: Long,
        access: RequestAccessContext,
        now: Timestamp,
    ): InformationRequestResponse?
    {
        val nextDisposition = patch.disposition ?: current?.disposition
        ?: InformationRequestResponseDisposition.NOT_ANSWERED
        val nextNarrative = nextNarrativeOf(patch, current)
        if (current != null &&
            current.disposition == nextDisposition &&
            current.narrative == nextNarrative &&
            (fieldValueSetId == null || current.fieldValueSetId == fieldValueSetId) &&
            current.reconfirmationRequiredByAmendmentId == null &&
            !fieldValuesChanged
        )
        {
            return null
        }

        val response = current ?: InformationRequestResponse().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            requirementRevisionId = revision.id
            occurrencePath = requirement.occurrencePath
            createdAt = now
        }
        response.requirementRevisionId = revision.id
        response.disposition = nextDisposition
        response.narrative = nextNarrative
        if (fieldValueSetId != null) response.fieldValueSetId = fieldValueSetId
        response.reconfirmationRequiredByAmendmentId = null
        response.responseRevision = revisionNumber
        response.recordedByPrincipalKind = access.principal.kind
        response.recordedByPrincipalId = access.principal.id
        response.recordedBySessionRef = access.authorization.sessionRef
        response.updatedAt = now
        return if (current == null) responseStore.save(response) else responseStore.update(response)
    }

    private fun replayResponseDraftResult(
        command: PatchInformationRequestResponsesCommand,
        result: CommandResultReference,
    ): InformationRequestResponseDraftResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val locked = mutationGuard.lock(result.resourceId, "Information Request receipt target not found")
        val request = locked.request
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, command.access)
        mutationGuard.requireResponseChangeAllowed(locked, RESPONSE_CHANGE_REFUSAL)
        requireVisibleTargets(command.access, command.patches.map { it.requirementId })
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(request.id).associateBy { it.id }
        patchValidator.requirePatchTargetsAvailable(command.patches, requirements, activeOccurrencePaths)
        command.patches.forEach { patch -> authorize(command.access, patch.requirementId) }
        val disclosed = command.patches.map { it.requirementId }.toSet()
        val responses = activeResponses.activeResponses(request.id)
            .filter { it.informationRequestRequirementId in disclosed }
        return InformationRequestResponseDraftResult(
            request = request,
            responseETag = InformationRequestETag.responsesOf(request),
            responses = responses,
            requirementsById = requirements.filterKeys { it in disclosed },
            fieldValueProjectionsByRequirementId = command.patches
                .filter {
                    it.fieldValues != null &&
                            responses.any { response -> response.informationRequestRequirementId == it.requirementId }
                }
                .associate { patch ->
                    patch.requirementId to fieldWriter.readFieldValues(
                        request.id,
                        requirements.getValue(patch.requirementId),
                        command.access
                    )
                },
        )
    }

    private fun InformationRequestResponseDraftResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = request.responseRevision,
            etag = responseETag,
        )

    private fun fingerprint(command: PatchInformationRequestResponsesCommand): String =
        CommandRequestFingerprint.of(
            PATCH_RESPONSES_OPERATION,
            command.requestId,
            command.patches.map { patch ->
                listOf(
                    patch.requirementId,
                    patch.disposition,
                    when (val narrative = patch.narrative) {
                        ResponseNarrativePatch.Unchanged -> listOf("unchanged")
                        ResponseNarrativePatch.Clear -> listOf("clear")
                        is ResponseNarrativePatch.Set -> listOf("set", narrative.value)
                    },
                    patch.fieldValues?.let { fieldPatch ->
                        listOf(
                            fieldPreconditionFingerprint(fieldPatch.precondition),
                            fieldPatch.entries.map { entry -> listOf(entry.fieldContractId, entry.value) },
                        )
                    },
                )
            },
            command.confirmedHiddenResponseClears,
        )

    private fun historyIdempotencyKey(requestId: UUID, idempotencyKey: String): String =
        "information_request.response|$requestId|$idempotencyKey"

    private companion object
    {
        const val PATCH_RESPONSES_OPERATION = "patch-information-request-responses"
        const val RESPONSE_CHANGE_REFUSAL = "Information Request response mutation is not allowed"
        const val RESPOND_REFUSAL = "Access denied to respond to Information Request Requirement"
        const val VERIFICATION_REFUSAL = "This protected item needs you to verify again before it can be saved"
    }
}
