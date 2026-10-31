package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactOffer
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestAcceptedFactQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestMutationGate,
    private val standing: InformationRequestAcceptedFactStanding,
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val restrictions: InformationRequestSubjectRestrictionService,
    private val disclosure: InformationRequestRequirementDisclosure,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val valueFit: InformationRequestFactValueFit,
)
{
    fun promotedFrom(requestId: UUID, access: RequestAccessContext): List<InformationRequestAcceptedFactView>
    {
        queryService.findById(requestId, access)
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_REVOKE_FACT), requestId)
        val readable = mutableMapOf<UUID, Boolean>()
        return standing.views(
            factRepository.findFromRequest(requestId).filter { fact ->
                readable.getOrPut(fact.sourceRequirementId) {
                    disclosure.permitsContent(
                        access,
                        fact.sourceRequirementId
                    )
                }
            },
        )
    }

    fun offers(requestId: UUID, access: RequestAccessContext): List<InformationRequestAcceptedFactOffer>
    {
        val request = queryService.findById(requestId, access)
        val purpose = versionRepository.findById(request.templateVersionId)?.factReusePurposeKey ?: return emptyList()
        val subject = partyRepository.findActiveForRequestRole(request.id, InformationRequestShareRoleKey.SUBJECT)
            .mapNotNull { it.subjectIdentityRefId }
            .distinct()
            .singleOrNull()
            ?: return emptyList()
        val ownerId =
            if (request.ownerType == InformationRequestOwnerType.ORGANIZATION) request.ownerOrganizationId else request.ownerUserId
        if (restrictions.isRestricted(request.ownerType, requireNotNull(ownerId), subject)) return emptyList()
        val inactive = requirementRepository.inactiveRequirementIds(request.id)
        val fieldRequirements =
            requirementRepository.findForRequest(request.id).filter { it.id !in inactive }.mapNotNull { requirement ->
            val binding = bindingRepository.findById(requirement.sourceTemplateBindingId) ?: return@mapNotNull null
            val fieldDefinitionId = binding.collectedFieldDefinitionId ?: return@mapNotNull null
                if (!takesFullAnswer(requirement.sourceTemplateBindingId)) return@mapNotNull null
                if (!disclosure.permitsContent(access, requirement.id)) return@mapNotNull null
            val key = templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)?.requirementKey
                ?: return@mapNotNull null
            Triple(requirement, fieldDefinitionId, key)
        }
        if (fieldRequirements.isEmpty()) return emptyList()
        val active = standing.eligibleForReuse(
            request.ownerType,
            requireNotNull(ownerId),
            subject,
            fieldRequirements.map { it.second }.toSet(),
            purpose,
        ).filter { it.sourceInformationRequestId != request.id }
            .filter { it.visibility == InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES }
            .let { reusable -> withVerifiedSources(reusable, access) }
            .filter { fact -> valueFit.fits(request, fact) }
        return fieldRequirements.mapNotNull { (requirement, fieldDefinitionId, key) ->
            val fact = active.filter { it.fieldDefinitionId == fieldDefinitionId }.maxByOrNull { it.promotedAt }
                ?: return@mapNotNull null
            InformationRequestAcceptedFactOffer(
                requirementId = requirement.id,
                requirementKey = key,
                fact = standing.view(fact),
                reconfirmationRequired = true,
            )
        }
    }

    private fun takesFullAnswer(bindingId: UUID): Boolean =
        dispositionRepository.findForBinding(bindingId)
            .any { it.disposition == InformationRequestResponseDisposition.PROVIDED }

    private fun withVerifiedSources(
        facts: List<InformationRequestAcceptedFact>,
        access: RequestAccessContext
    ): List<InformationRequestAcceptedFact> {
        if (access.authorization.mfaSatisfied || facts.isEmpty()) return facts
        val protected = disclosure.protectedRequirementIds(facts.map { it.sourceInformationRequestId })
        return facts.filter { it.sourceRequirementId !in protected }
    }
}
