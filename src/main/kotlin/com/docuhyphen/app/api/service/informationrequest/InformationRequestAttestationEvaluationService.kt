package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicyEvaluator
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestingParty
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionContent
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionAttestationRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestAttestationEvaluationService @Inject constructor(
    private val contentCollector: InformationRequestSubmissionContentCollector,
    private val stages: InformationRequestSubmissionStages,
    private val policyLoader: InformationRequestAttestationPolicyLoader,
    private val attestationRepository: InformationRequestSubmissionAttestationRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val clock: Clock,
)
{
    fun evaluate(request: InformationRequest): Map<UUID, InformationRequestAttestationRequirementEvaluation>
    {
        val stageOrder = stages.stageOrder(request)
        val scopes: List<String?> = stageOrder.ifEmpty { listOf(null) }
        return scopes.flatMap { stageKey -> evaluate(contentCollector.collect(request, stageKey)).values }
            .associateBy { it.requirementId }
    }

    fun evaluate(content: InformationRequestSubmissionContent): Map<UUID, InformationRequestAttestationRequirementEvaluation>
    {
        val assertions = content.items.filter { it.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION }
        if (assertions.isEmpty()) return emptyMap()
        val policies = policyLoader.forVersion(content.version.id)
        val attestationsByRequirement = attestationRepository.findForRequest(content.request.id)
            .groupBy { it.attestationRequirementId }
        val parties = attestingParties(content.request.id)
        val now = clock.instant()
        return assertions.mapNotNull { item ->
            val policy = policies[item.binding.id] ?: return@mapNotNull null
            InformationRequestAttestationRequirementEvaluation(
                requirementId = item.requirement.id,
                bindingId = item.binding.id,
                stageKey = content.stageKey,
                contentHash = content.contentHash,
                policy = policy,
                evaluation = InformationRequestAttestationPolicyEvaluator.evaluate(
                    policy,
                    content.contentHash,
                    attestationsByRequirement[item.requirement.id].orEmpty()
                        .filter { it.requirementRevisionId == item.revision.id },
                    parties,
                    now,
                ),
            )
        }.associateBy { it.requirementId }
    }

    fun attestationsFor(requestId: UUID, requirementId: UUID): List<InformationRequestSubmissionAttestation> =
        attestationRepository.findForRequest(requestId).filter { it.attestationRequirementId == requirementId }

    fun attestingParties(requestId: UUID): List<InformationRequestAttestingParty> =
        partyRepository.findActiveForRequest(requestId).mapNotNull { party ->
            party.roleKey.toContributorRole()?.let { InformationRequestAttestingParty(party.id, it) }
        }

    private fun InformationRequestShareRoleKey.toContributorRole(): InformationRequestContributorRole? = when (this)
    {
        InformationRequestShareRoleKey.SUBJECT -> InformationRequestContributorRole.SUBJECT
        InformationRequestShareRoleKey.CONTRIBUTOR -> InformationRequestContributorRole.CONTRIBUTOR
        InformationRequestShareRoleKey.PREPARER -> InformationRequestContributorRole.PREPARER
        InformationRequestShareRoleKey.ATTESTOR -> InformationRequestContributorRole.ATTESTOR
        InformationRequestShareRoleKey.REVIEWER,
        InformationRequestShareRoleKey.DECISION_MAKER,
        -> null
    }
}
