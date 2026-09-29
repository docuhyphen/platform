package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConfidence
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConflictState
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactRevocation
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRevocationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestAcceptedFactService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val loader: InformationRequestReviewLoader,
    private val lockService: InformationRequestSubmissionLockService,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val revocationRepository: InformationRequestAcceptedFactRevocationRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val facts: InformationRequestAcceptedFactStanding,
    private val evidenceReferences: InformationRequestAcceptedFactEvidenceService,
    private val restrictions: InformationRequestSubjectRestrictionService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun promote(command: PromoteInformationRequestAcceptedFactCommand): InformationRequestAcceptedFactView
    {
        val locked = gate.lock(command.requestId)
        val purpose = command.purposeKey.trim().lowercase()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = PROMOTE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    PROMOTE_OPERATION,
                    command.packageId,
                    command.submissionItemId,
                    purpose,
                    command.policyBasisKey?.trim()?.lowercase() ?: "",
                    command.evidenceVersionIds.sorted().joinToString(","),
                    command.visibility,
                    command.validFrom ?: "",
                    command.validTo ?: "",
                    command.expiresAt ?: "",
                    command.supersedesFactId ?: "",
                ).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val fact = promoteFact(locked, command, purpose)
                CommandMutationResult(fact, CommandResultReference(ResourceType.INFORMATION_REQUEST_ACCEPTED_FACT, fact.id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> facts.view(decision.response)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT), command.requestId)
                facts.view(requireFact(command.requestId, decision.result.resourceId))
            }
        }
    }

    @Transactional
    fun revoke(command: RevokeInformationRequestAcceptedFactCommand): InformationRequestAcceptedFactView
    {
        val locked = gate.lock(command.requestId)
        val reasonCode = command.reasonCode.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_ACCEPTED_FACT, command.factId),
            operation = REVOKE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex("$REVOKE_OPERATION|${command.factId}|$reasonCode"),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val fact = revokeFact(locked, command, reasonCode)
                CommandMutationResult(fact, CommandResultReference(ResourceType.INFORMATION_REQUEST_ACCEPTED_FACT, fact.id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> facts.view(decision.response)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT), command.requestId)
                facts.view(requireFact(command.requestId, decision.result.resourceId))
            }
        }
    }

    private fun promoteFact(
        locked: LockedInformationRequest,
        command: PromoteInformationRequestAcceptedFactCommand,
        purpose: String,
    ): InformationRequestAcceptedFact
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.PROMOTE_FACT)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT), request.id)
        if (!PURPOSE.matches(purpose)) throw InformationRequestCommandRequestException("A fact purpose is a lowercase machine key")
        val policyBasis = command.policyBasisKey?.trim()?.lowercase()
        if (policyBasis == null || !PURPOSE.matches(policyBasis))
        {
            throw InformationRequestCommandRequestException("A fact states its reuse policy basis as a lowercase machine key")
        }
        val submission = lockService.activePackages(request.id).firstOrNull { it.id == command.packageId }
            ?: refuse("An accepted fact is promoted from a current Submission Package")
        val view = packageReader.view(request.id, submission.id)
        val item = view.items.firstOrNull { it.id == command.submissionItemId }?.takeIf(::answersField)
            ?: refuse("An accepted fact is promoted from an answered typed-data item")
        val selectedEvidence = evidenceReferences.select(view, item.informationRequestRequirementId, command.evidenceVersionIds)
        val (confidence, reviewId) = acceptanceOf(request, submission.id, submission.reviewRequired, item)
        val subject = partyRepository.findActiveForRequestRole(request.id, InformationRequestShareRoleKey.SUBJECT)
            .mapNotNull { it.subjectIdentityRefId }
            .distinct()
            .singleOrNull()
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCEPTED_FACT_SUBJECT_REQUIRED,
                "An accepted fact is about the single subject of its source request",
            )
        if (restrictions.isRestricted(request.ownerType, ownerIdOf(request), subject))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUBJECT_RESTRICTED,
                "Processing for this subject is restricted, so no fact is promoted",
            )
        }
        val fieldDefinitionId = bindingRepository.findById(item.templateBindingId)?.collectedFieldDefinitionId
            ?: refuse("An accepted fact is promoted from an item that collects a Field")
        val value = fieldValueRevisions.valueOf(requireNotNull(item.fieldValueRevisionId))
            ?.takeIf { !it.cleared }
            ?: refuse("An accepted fact is promoted from an answer that holds a value")
        val now = clock.instant()
        val validFrom = command.validFrom ?: now
        if (command.validTo != null && !command.validTo.isAfter(validFrom))
        {
            throw InformationRequestCommandRequestException("A fact's valid period ends after it begins")
        }
        if (command.expiresAt != null && !command.expiresAt.isAfter(now))
        {
            throw InformationRequestCommandRequestException("A fact expires in the future")
        }
        command.supersedesFactId?.let(factRepository::findByIdForUpdate)
        val active = facts.activeForKey(request.ownerType, ownerIdOf(request), subject, listOf(fieldDefinitionId), purpose)
        command.supersedesFactId?.let { superseded ->
            if (active.none { it.id == superseded })
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.ACCEPTED_FACT_SUPERSESSION_INVALID,
                    "An accepted fact supersedes a current fact of the same subject, field, and purpose",
                )
            }
        }
        val canonical = value.value.toString()
        val conflicting = active.filter { it.id != command.supersedesFactId && it.canonicalValue != canonical }.maxByOrNull { it.promotedAt }
        val fact = factRepository.save(
            InformationRequestAcceptedFact().apply {
                ownerType = request.ownerType
                ownerOrganizationId = request.ownerOrganizationId
                ownerUserId = request.ownerUserId
                subjectIdentityRefId = subject
                purposeKey = purpose
                policyBasisKey = policyBasis
                this.fieldDefinitionId = fieldDefinitionId
                valueType = value.valueType
                canonicalValue = canonical
                sourceInformationRequestId = request.id
                sourcePackageId = submission.id
                sourceSubmissionItemId = item.id
                sourceRequirementId = item.informationRequestRequirementId
                sourceResponseId = requireNotNull(item.responseId)
                sourceResponseRevision = requireNotNull(item.responseRevision)
                sourceFieldValueRevisionId = requireNotNull(item.fieldValueRevisionId)
                sourceReviewId = reviewId
                visibility = command.visibility
                this.confidence = confidence
                this.validFrom = Timestamp.from(validFrom)
                validTo = command.validTo?.let(Timestamp::from)
                expiresAt = command.expiresAt?.let(Timestamp::from)
                supersedesFactId = command.supersedesFactId
                conflictState = if (conflicting == null) InformationRequestAcceptedFactConflictState.NONE
                else InformationRequestAcceptedFactConflictState.CONFLICTING
                conflictingFactId = conflicting?.id
                promotedByPrincipalKind = command.access.principal.kind
                promotedByPrincipalId = command.access.principal.id
                promotedAt = Timestamp.from(now)
            },
        )
        evidenceReferences.save(fact.id, selectedEvidence)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.PROMOTE_FACT,
                actor = command.access.principal,
                idempotencyKey = "information_request.fact_promotion|${fact.id}|${command.idempotencyKey}",
                details = mapOf(
                    "acceptedFactId" to fact.id.toString(),
                    "purposeKey" to purpose,
                    "submissionPackageId" to submission.id.toString(),
                    "submissionItemId" to item.id.toString(),
                    "factConfidence" to confidence.name,
                    "conflictState" to fact.conflictState.name,
                    "evidenceVersionIds" to selectedEvidence.joinToString(",") { it.evidenceVersionId.toString() },
                ),
            ),
        )
        return fact
    }

    private fun revokeFact(
        locked: LockedInformationRequest,
        command: RevokeInformationRequestAcceptedFactCommand,
        reasonCode: String,
    ): InformationRequestAcceptedFact
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.REVOKE_FACT)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT), request.id)
        if (reasonCode.isEmpty()) throw InformationRequestCommandRequestException("A revocation states its reason")
        val fact = requireFact(request.id, command.factId)
        factRepository.findByIdForUpdate(fact.id)
        if (revocationRepository.findForFacts(listOf(fact.id)).isNotEmpty())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCEPTED_FACT_REVOKED,
                "This accepted fact was already revoked",
            )
        }
        revocationRepository.save(
            InformationRequestAcceptedFactRevocation().apply {
                factId = fact.id
                this.reasonCode = reasonCode
                narrative = command.narrative?.trim()?.ifBlank { null }
                revokedByPrincipalKind = command.access.principal.kind
                revokedByPrincipalId = command.access.principal.id
                revokedAt = Timestamp.from(clock.instant())
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.REVOKE_FACT,
                actor = command.access.principal,
                reasonCode = reasonCode,
                idempotencyKey = "information_request.fact_revocation|${fact.id}|${command.idempotencyKey}",
                details = mapOf("acceptedFactId" to fact.id.toString()),
            ),
        )
        return fact
    }

    private fun acceptanceOf(
        request: InformationRequest,
        packageId: UUID,
        reviewRequired: Boolean,
        item: InformationRequestSubmissionItem,
    ): Pair<InformationRequestAcceptedFactConfidence, UUID?>
    {
        if (!reviewRequired) return InformationRequestAcceptedFactConfidence.DECLARED to null
        val review = loader.latestForPackage(request.id, packageId)?.takeIf { it.state.accepted }
            ?: refuse("An accepted fact is promoted from a package its review accepted")
        val outcome = loader.snapshot(review).standing.itemOutcomes[item.id]
            ?: return InformationRequestAcceptedFactConfidence.DECLARED to null
        if (!outcome.passing) refuse("An accepted fact is promoted from an item its review accepted")
        return InformationRequestAcceptedFactConfidence.REVIEWED to review.id
    }

    private fun answersField(item: InformationRequestSubmissionItem): Boolean =
        item.requirementType == InformationRequestRequirementType.FIELD &&
            item.completenessState == com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState.COMPLETE &&
            item.responseId != null &&
            item.fieldValueRevisionId != null

    private fun ownerIdOf(request: InformationRequest): UUID =
        if (request.ownerType == InformationRequestOwnerType.ORGANIZATION) requireNotNull(request.ownerOrganizationId)
        else requireNotNull(request.ownerUserId)

    private fun requireFact(requestId: UUID, factId: UUID): InformationRequestAcceptedFact =
        factRepository.findById(factId)?.takeIf { it.sourceInformationRequestId == requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Accepted fact not found")

    private fun refuse(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCEPTED_FACT_SOURCE_INVALID, message)

    private companion object
    {
        const val PROMOTE_OPERATION = "promote-information-request-accepted-fact"
        const val REVOKE_OPERATION = "revoke-information-request-accepted-fact"
        val PURPOSE = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    }
}
