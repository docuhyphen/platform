package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactOffer
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

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
)
{
    fun promotedFrom(requestId: UUID, access: RequestAccessContext): List<InformationRequestAcceptedFactView>
    {
        queryService.findById(requestId, access)
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_PROMOTE_FACT), requestId)
        return standing.views(factRepository.findFromRequest(requestId))
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
        val requestingSide = gate.permitsRequest(access, Action.INFORMATION_REQUEST_PROMOTE_FACT, request.id)
        val fieldRequirements = requirementRepository.findForRequest(request.id).mapNotNull { requirement ->
            val binding = bindingRepository.findById(requirement.sourceTemplateBindingId) ?: return@mapNotNull null
            val fieldDefinitionId = binding.collectedFieldDefinitionId ?: return@mapNotNull null
            if (!requestingSide &&
                !gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirement.id))
            {
                return@mapNotNull null
            }
            val key = templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)?.requirementKey
                ?: return@mapNotNull null
            Triple(requirement, fieldDefinitionId, key)
        }
        if (fieldRequirements.isEmpty()) return emptyList()
        val ownerId = if (request.ownerType == InformationRequestOwnerType.ORGANIZATION) request.ownerOrganizationId else request.ownerUserId
        val active = standing.activeForKey(
            request.ownerType,
            requireNotNull(ownerId),
            subject,
            fieldRequirements.map { it.second }.toSet(),
            purpose,
        ).filter { it.sourceInformationRequestId != request.id }
            .filter { requestingSide || it.visibility == InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES }
        return fieldRequirements.mapNotNull { (requirement, fieldDefinitionId, key) ->
            val fact = active.filter { it.fieldDefinitionId == fieldDefinitionId }.maxByOrNull { it.promotedAt } ?: return@mapNotNull null
            InformationRequestAcceptedFactOffer(
                requirementId = requirement.id,
                requirementKey = key,
                fact = standing.view(fact),
                reconfirmationRequired = true,
            )
        }
    }
}
