package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.AudienceFieldBindingPolicy
import com.docuhyphen.app.api.service.fields.FieldBindingAccess
import com.docuhyphen.app.api.service.fields.FieldBindingDecision
import com.docuhyphen.app.api.service.fields.FieldBindingDenial
import com.docuhyphen.app.api.service.fields.FieldValueOperation
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * The Information Request domain's per-binding Fields policy.
 *
 * A binding that answers a Requirement is addressed to that Requirement's own runtime occurrence,
 * so this policy asks central authorization for that exact `INFORMATION_REQUEST_REQUIREMENT`
 * resource rather than the whole request: the same assigned-party, response-mode, confidentiality,
 * correction-scope, and request-state rules that already govern direct Requirement actions then
 * govern the Field a caller may read or write for it. A binding no Requirement answers (a question
 * the request itself asks, not one of its Requirements) has no narrower occurrence to consult: an
 * owner-side caller is left to the shared audience rule alone, but a respondent has no explicitly
 * authorized purpose for it and is refused regardless of the binding's classification.
 */
@ApplicationScoped
class InformationRequestFieldBindingPolicy @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
    private val authorizationService: AuthorizationService,
) : AudienceFieldBindingPolicy()
{
    /**
     * A caller is external when they reach the request as a respondent rather than as a member of
     * the organization or user that owns it.
     *
     * - PARTICIPANT / PUBLIC_LINK: always external (a bootstrap-session or magic-link respondent).
     * - USER: external unless they are an active member of the organization that owns the request,
     *   or are the personal owner themselves.
     * - All other principal kinds are treated as internal.
     */
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext): Boolean
    {
        val principal = access.principal
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT, PrincipalKind.PUBLIC_LINK -> true
            PrincipalKind.USER ->
            {
                val request = requestRepository.findById(resource.resourceId) ?: return true
                val ownerOrgId = request.ownerOrganizationId
                if (ownerOrgId != null)
                    organizationMembershipRepository.findActiveByUserAndOrg(principal.id, ownerOrgId) == null
                else
                    request.ownerUserId != principal.id
            }
            else -> false
        }
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

    /**
     * The Requirement a binding answers at the exact occurrence [valueSet] addresses, or null if no
     * Requirement at that occurrence collects this Field Definition. A repeated Field is bound once
     * per occurrence, so matching the Field Definition alone is not enough: the occurrence itself
     * must agree, or a sibling occurrence's Requirement could be authorized in its place.
     */
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
