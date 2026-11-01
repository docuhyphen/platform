package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.shareRoleKey
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestFieldAudienceGuard @Inject constructor(
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val fields: FieldsReadFacade,
    private val organizationMemberships: OrganizationMembershipService,
    private val principalGroups: PrincipalGroupService,
) {
    fun requireAnswerableByParties(request: InformationRequest, templateVersionId: UUID = request.templateVersionId) {
        val restricted = restrictedCollections(templateVersionId)
        if (restricted.isEmpty()) return
        partyRepository.findActiveForRequest(request.id).forEach { party ->
            val kind = party.principalKind ?: return@forEach
            val id = party.principalId ?: return@forEach
            requireAnswerable(request, restricted, party.roleKey, PrincipalRef(kind, id))
        }
    }

    fun requireAnswerableBy(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef
    ) {
        requireAnswerable(request, restrictedCollections(request.templateVersionId), roleKey, principal)
    }

    private fun requireAnswerable(
        request: InformationRequest,
        restricted: List<InformationRequestTemplateRequirementBinding>,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
    ) {
        val binding = restricted.firstOrNull { it.contributorRole.shareRoleKey() == roleKey } ?: return
        if (!isOutside(request, principal)) return
        val requirementKey = templateRequirementRepository.findById(binding.templateRequirementId)?.requirementKey
            ?: binding.prompt
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE,
            "Requirement $requirementKey collects a Field that is not marked Public, so a " +
                    "${roleKey.name.lowercase()} outside the owner of this request could not answer it",
        )
    }

    private fun restrictedCollections(templateVersionId: UUID): List<InformationRequestTemplateRequirementBinding> {
        val collecting = bindingRepository.findOrdered(templateVersionId)
            .filter { it.collectedFieldDefinitionId != null && it.responseMode in ANSWERED_MODES }
        if (collecting.isEmpty()) return emptyList()
        val schemaVersionId = versionRepository.findById(templateVersionId)?.schemaVersionId ?: return emptyList()
        val public = fields.schemaBindings(schemaVersionId)
            .filter { it.visibility == FieldDataClassification.PUBLIC }
            .map { it.fieldDefinitionId }
            .toSet()
        return collecting.filter { it.collectedFieldDefinitionId !in public }
    }

    private fun isOutside(request: InformationRequest, principal: PrincipalRef): Boolean =
        if (principal.kind == PrincipalKind.PRINCIPAL_GROUP) {
            principalGroups.activeMembers(principal.id).any { member ->
                InformationRequestRespondentAudience.isOutside(
                    request,
                    PrincipalRef(member.principalKind, member.principalId),
                    organizationMemberships::isMember,
                )
            }
        } else {
            InformationRequestRespondentAudience.isOutside(request, principal, organizationMemberships::isMember)
        }

    private companion object {
        val ANSWERED_MODES = setOf(InformationRequestResponseMode.PROVIDE, InformationRequestResponseMode.PROVIDE_ONCE)
    }
}
