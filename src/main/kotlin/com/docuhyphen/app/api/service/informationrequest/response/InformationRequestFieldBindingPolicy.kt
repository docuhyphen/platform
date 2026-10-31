package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.AudienceFieldBindingPolicy
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestFieldBindingPolicy @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val organizationMemberships: OrganizationMembershipService,
    private val authorizationService: AuthorizationService,
) : AudienceFieldBindingPolicy()
{
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext): Boolean
    {
        val request =
            if (access.principal.kind == PrincipalKind.USER) requestRepository.findById(resource.resourceId) else null
        return InformationRequestRespondentAudience.isOutside(
            request,
            access.principal,
            organizationMemberships::isMember
        )
    }

    override fun decide(request: FieldBindingAccess): FieldBindingDecision
    {
        val sharedDecision = super.decide(request)
        if (sharedDecision is FieldBindingDecision.Deny) return sharedDecision

        val requirementId = requirementIdAnswering(
            request.resource.resourceId,
            request.binding.fieldDefinitionId,
            request.valueSet,
        )
        if (requirementId == null)
        {
            return if (isExternalCaller(request.resource, request.access))
                FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE)
            else
                sharedDecision
        }

        val action = when (request.operation)
        {
            FieldValueOperation.READ -> Action.INFORMATION_REQUEST_REQUIREMENT_VIEW
            FieldValueOperation.WRITE -> Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND
        }
        val decision = authorizationService.authorize(
            request.access.principal,
            action,
            ResourceRef.informationRequestRequirement(requirementId),
            request.access.authorization,
        )
        return if (decision is Decision.Deny)
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE)
        else
            sharedDecision
    }

    private fun requirementIdAnswering(requestId: UUID, fieldDefinitionId: UUID, valueSet: FieldValueSetRef): UUID? =
        requirementRepository.findForRequest(requestId)
            .firstOrNull { requirement ->
                answersOccurrence(requirement.occurrencePath, valueSet) &&
                        templateBindingRepository.findById(requirement.sourceTemplateBindingId)
                            ?.collectedFieldDefinitionId == fieldDefinitionId
            }
            ?.id

    private fun answersOccurrence(occurrencePath: String, valueSet: FieldValueSetRef): Boolean = when (valueSet)
    {
        FieldValueSetRef.Root -> InformationRequestOccurrencePath.isRoot(occurrencePath)
        is FieldValueSetRef.Occurrence -> occurrencePath == valueSet.occurrencePath
    }
}
