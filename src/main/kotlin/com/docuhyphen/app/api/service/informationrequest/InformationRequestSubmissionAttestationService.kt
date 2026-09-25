package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestActingParty
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicy
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicyEvaluator
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionAttestationResult
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionAttestationService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val attestationRepository: InformationRequestSubmissionAttestationRepository,
    private val policyLoader: InformationRequestAttestationPolicyLoader,
    private val evaluationService: InformationRequestAttestationEvaluationService,
    private val contentCollector: InformationRequestSubmissionContentCollector,
    private val stages: InformationRequestSubmissionStages,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun record(command: RecordInformationRequestSubmissionAttestationCommand): InformationRequestSubmissionAttestationResult
    {
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequestRequirement(command.requirementId),
            operation = ATTEST_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    ATTEST_OPERATION,
                    command.requestId,
                    command.requirementId,
                    command.decision,
                    command.refusalReason?.trim().orEmpty(),
                    command.externalSignatureReference?.trim().orEmpty(),
                    command.partyId ?: "",
                ).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordAttestation(locked, command)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_SUBMISSION_ATTESTATION,
                        resourceId = result.attestation.id,
                        revision = result.attestation.sequenceNumber.toLong(),
                        etag = InformationRequestETag.responsesOf(locked.request),
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, command, decision.result)
        }
    }

    private fun recordAttestation(
        locked: LockedInformationRequest,
        command: RecordInformationRequestSubmissionAttestationCommand,
    ): InformationRequestSubmissionAttestationResult
    {
        val request = locked.request
        val requirement = requireAssertion(request.id, request.templateVersionId, command.requirementId)
        val policy = policyLoader.forBinding(requirement.sourceTemplateBindingId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_NOT_AN_ASSERTION,
                "This Information Request Requirement is not an assertion a party can make",
            )
        gate.requireMutation(locked, InformationRequestMutation.ATTEST_RESPONSE)
        gate.requireContinuationEntitlement(locked)
        lockService.requireUnlocked(request.id, listOf(requirement.id))
        gate.authorizeRequirement(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, requirement.id)

        val party = actingParty(locked, policy, requirement, command)
        val strength = strengthOf(command.access)
        if (strength.rank < policy.minimumAuthenticationStrength.rank)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_STRENGTH_INSUFFICIENT,
                "This assertion needs ${policy.minimumAuthenticationStrength} and the caller proved $strength",
            )
        }
        val refusalReason = requireReason(command)
        val reference = requireReference(policy, command)

        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        val stageKey = stages.stageByBinding(version)[requirement.sourceTemplateBindingId]
        val content = contentCollector.collect(request, stageKey)
        command.precondition.requireSatisfiedBy(InformationRequestETag.submissionOf(stageKey, content.contentHash))
        val current = evaluationService.evaluate(content)[requirement.id]
        if (command.decision == InformationRequestAttestationDecision.ASSENTED)
        {
            val unmet = InformationRequestAttestationPolicyEvaluator.rolesBefore(policy, party.role)
                .filter { role -> current?.evaluation?.counted?.none { it.partyRole == role } != false }
            if (unmet.isNotEmpty())
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.ATTESTATION_ORDER_UNMET,
                    "This assertion is made in role order and ${unmet.joinToString()} has not assented to this content",
                )
            }
        }

        val now = clock.instant()
        val binding = bindingRepository.findById(requirement.sourceTemplateBindingId)
            ?: throw IllegalStateException("Information Request Requirement binding not found")
        val revision = revisionRepository.findCurrentForRequest(request.id)
            .first { it.informationRequestRequirementId == requirement.id }
        val attestation = attestationRepository.save(
            InformationRequestSubmissionAttestation().apply {
                informationRequestId = request.id
                attestationRequirementId = requirement.id
                requirementRevisionId = revision.id
                this.stageKey = stageKey
                partyId = party.party.id
                partyRole = party.role
                principalKind = command.access.principal.kind
                principalId = command.access.principal.id
                sessionRef = command.access.authorization.sessionRef?.takeIf { it.isNotBlank() }
                delegatedAuthorityId = party.delegatedAuthorityId
                decision = command.decision
                this.refusalReason = refusalReason
                authenticationStrength = strength
                externalSignatureReference = reference
                attestedContentHashSha256 = content.contentHash
                statementHashSha256 = policyLoader.statementHash(binding)
                policyHashSha256 = policy.policyHash
                attestedAt = Timestamp.from(now)
                expiresAt = policy.validityHours?.let { Timestamp.from(now.plus(it.toLong(), ChronoUnit.HOURS)) }
                sequenceNumber = attestationRepository.nextSequenceNumber(request.id)
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.ATTEST_RESPONSE,
                actor = command.access.principal,
                partyId = party.party.id,
                idempotencyKey = "information_request.attestation|${requirement.id}|" +
                    "${command.access.principal.kind}:${command.access.principal.id}|${command.idempotencyKey}",
                details = buildMap {
                    put("requirementId", requirement.id.toString())
                    put("attestationId", attestation.id.toString())
                    put("attestationDecision", attestation.decision.name)
                    put("attestedContentHash", content.contentHash)
                    stageKey?.let { put("stageKey", it) }
                },
            ),
        )
        return InformationRequestSubmissionAttestationResult(
            attestation = attestation,
            evaluation = evaluationService.evaluate(content)[requirement.id],
            submissionETag = InformationRequestETag.submissionOf(stageKey, content.contentHash),
        )
    }

    private fun replay(
        locked: LockedInformationRequest,
        command: RecordInformationRequestSubmissionAttestationCommand,
        recorded: CommandResultReference,
    ): InformationRequestSubmissionAttestationResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST_SUBMISSION_ATTESTATION) {
            "Command receipt does not reference a Submission Attestation"
        }
        gate.authorizeRequirement(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, command.requirementId)
        val attestation = attestationRepository.findById(recorded.resourceId)
            ?.takeIf { it.informationRequestId == locked.request.id && it.attestationRequirementId == command.requirementId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submission Attestation receipt target not found",
            )
        val content = contentCollector.collect(locked.request, attestation.stageKey)
        return InformationRequestSubmissionAttestationResult(
            attestation = attestation,
            evaluation = evaluationService.evaluate(content)[command.requirementId],
            submissionETag = InformationRequestETag.submissionOf(attestation.stageKey, content.contentHash),
        )
    }

    private fun requireAssertion(requestId: UUID, templateVersionId: UUID, requirementId: UUID): InformationRequestRequirement
    {
        val requirement = requirementRepository.findById(requirementId)
            ?.takeIf { it.informationRequestId == requestId && it.sourceTemplateVersionId == templateVersionId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request Requirement not found",
            )
        val activePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        if (!InformationRequestOccurrencePath.isActiveOccurrence(requirement.occurrencePath, activePaths))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                "This Information Request group occurrence has been removed",
            )
        }
        val type = templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)?.requirementType
        if (type != InformationRequestRequirementType.RESPONSE_ATTESTATION)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_NOT_AN_ASSERTION,
                "This Information Request Requirement is not an assertion a party can make",
            )
        }
        return requirement
    }

    private fun actingParty(
        locked: LockedInformationRequest,
        policy: InformationRequestAttestationPolicy,
        requirement: InformationRequestRequirement,
        command: RecordInformationRequestSubmissionAttestationCommand,
    ): InformationRequestActingParty
    {
        val candidates = requirementContext.actingPartiesFor(
            locked.request,
            policy.requiredRoles.toSet(),
            command.access.principal,
            requirement.id,
        )
        val chosen = command.partyId?.let { named -> candidates.filter { it.party.id == named } } ?: candidates
        return when (chosen.size)
        {
            1 -> chosen.single()
            0 -> throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_PARTY_NOT_ELIGIBLE,
                "The caller acts as no active party this assertion's policy names",
            )
            else -> throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_PARTY_AMBIGUOUS,
                "The caller may attest as more than one party; name the party",
            )
        }
    }

    private fun strengthOf(access: RequestAccessContext): InformationRequestAuthenticationStrength = when (access.principal.kind)
    {
        PrincipalKind.PARTICIPANT -> InformationRequestAuthenticationStrength.VERIFIED_CONTACT
        PrincipalKind.USER -> if (access.authorization.mfaSatisfied)
            InformationRequestAuthenticationStrength.MULTI_FACTOR
        else
            InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN
        else -> throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.ATTESTATION_STRENGTH_INSUFFICIENT,
            "Only a person may make an assertion",
        )
    }

    private fun requireReason(command: RecordInformationRequestSubmissionAttestationCommand): String?
    {
        val reason = command.refusalReason?.trim()?.ifBlank { null }
        val valid = when (command.decision)
        {
            InformationRequestAttestationDecision.REFUSED -> reason != null && reason.length <= MAXIMUM_REASON_LENGTH
            InformationRequestAttestationDecision.ASSENTED -> reason == null
        }
        if (!valid)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_REFUSAL_REASON_INVALID,
                "A refusal states its reason in at most $MAXIMUM_REASON_LENGTH characters and an assent states none",
            )
        }
        return reason
    }

    private fun requireReference(
        policy: InformationRequestAttestationPolicy,
        command: RecordInformationRequestSubmissionAttestationCommand,
    ): String?
    {
        val reference = command.externalSignatureReference?.trim()?.ifBlank { null }
        if (reference != null &&
            (policy.externalSignatureReference == InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED ||
                reference.length > MAXIMUM_REFERENCE_LENGTH)
        )
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_SIGNATURE_REFERENCE_NOT_ACCEPTED,
                "This assertion accepts no external signature reference of that form",
            )
        }
        if (reference == null &&
            command.decision == InformationRequestAttestationDecision.ASSENTED &&
            policy.externalSignatureReference == InformationRequestExternalSignatureReferencePolicy.REQUIRED
        )
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ATTESTATION_SIGNATURE_REFERENCE_REQUIRED,
                "This assertion needs a reference to the signature made outside this platform",
            )
        }
        return reference
    }

    private companion object
    {
        const val ATTEST_OPERATION = "attest-information-request-submission"
        const val MAXIMUM_REASON_LENGTH = 2000
        const val MAXIMUM_REFERENCE_LENGTH = 512
    }
}
