package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestRecipientPreview
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementPolicyEvaluator
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestRecipientPreviewService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestMutationGate,
    private val partyQueryService: InformationRequestPartyQueryService,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateProjectionLoader: InformationRequestTemplateProjectionLoader,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val requirementPolicy: InformationRequestRequirementPolicyEvaluator,
) {
    fun preview(requestId: UUID, partyId: UUID, access: RequestAccessContext): InformationRequestRecipientPreview {
        val request = queryService.findById(requestId, access)
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_MANAGE_PARTIES), requestId)
        val party = answeringParty(request, partyId)
        val templateVersion = templateVersionOf(request)
        val disclosedBindingIds = bindingRepository.findOrdered(templateVersion.id)
            .filter { disclosesTo(request, it, party) }
            .map { it.id }
            .toSet()
        return InformationRequestRecipientPreview(
            partyId = party.id,
            roleKey = party.roleKey,
            templateVersion = InformationRequestRecipientTemplateProjection.forParticipant(
                InformationRequestRecipientTemplateProjection.recipientSafe(
                    templateVersion,
                    disclosedBindingIds,
                    emptySet()
                ),
            ),
        )
    }

    private fun answeringParty(request: InformationRequest, partyId: UUID): InformationRequestParty {
        val party = partyQueryService.findParty(partyId)
            ?.takeIf { it.informationRequestId == request.id && it.active }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request party not found"
            )
        if (party.roleKey !in ANSWERING_ROLES) {
            throw InformationRequestCommandRequestException("Preview a party who answers this request")
        }
        return party
    }

    private fun templateVersionOf(request: InformationRequest): InformationRequestTemplateVersionDto {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")
        return templateProjectionLoader.loadVersion(version)
    }

    private fun disclosesTo(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
        party: InformationRequestParty,
    ): Boolean {
        val facts = requirementContext.authoredContextFor(request, binding)?.policyFacts
                as? InformationRequestRequirementPolicyFacts
            ?: return false
        return requirementPolicy.disclosesToParty(facts, party.id)
    }

    private companion object {
        val ANSWERING_ROLES = setOf(
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
    }
}
