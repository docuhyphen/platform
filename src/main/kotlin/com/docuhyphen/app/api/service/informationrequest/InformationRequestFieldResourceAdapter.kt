package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.FieldResourceAdapter
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestFieldResourceAdapter @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val authorizationService: AuthorizationService,
    private val executionGrantService: InformationRequestExecutionGrantService,
    override val bindingPolicy: InformationRequestFieldBindingPolicy,
) : FieldResourceAdapter
{
    override val resourceType: String = ResourceType.INFORMATION_REQUEST.name

    override fun exists(resourceId: UUID): Boolean =
        requestRepository.findById(resourceId) != null

    override fun ownerScope(resourceId: UUID): ScopeReference? =
        requestRepository.findById(resourceId)?.ownerScope()

    override fun subscriptionContext(resourceId: UUID): SubscriptionContext? =
        requestRepository.findById(resourceId)?.subscriptionContext()

    /**
     * A request's Field writes answer to its frozen execution grant, not the owner's live plan, once
     * the request has been issued: the grant already required the Business Fields feature at that
     * moment for any Template that binds one, so re-checking a lapsed live plan on every later write
     * would strand an assigned respondent within limits that were already reserved for them.
     */
    override fun mutationEntitlementFrozen(resourceId: UUID): Boolean =
        executionGrantService.findForRequest(resourceId) != null

    override fun authorizeViewFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        authorize(principal, Action.INFORMATION_REQUEST_VIEW, resourceId, context, "Access denied to request fields")
    }

    override fun authorizeManageFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        if (isAllowed(principal, Action.INFORMATION_REQUEST_EDIT, ResourceRef.informationRequest(resourceId), context))
        {
            return
        }
        if (requirementRepository.findForRequest(resourceId).any { requirement ->
                isAllowed(
                    principal,
                    Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                    ResourceRef.informationRequestRequirement(requirement.id),
                    context,
                )
            })
        {
            return
        }
        throw ForbiddenException("Access denied to edit request fields")
    }

    override fun authorizeManageSchema(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        authorize(principal, Action.INFORMATION_REQUEST_EDIT, resourceId, context, "Access denied to manage request schema")
    }

    override fun schemaAssignmentMutable(resourceId: UUID): Boolean =
        requestRepository.findById(resourceId)?.state == InformationRequestState.DRAFT

    override fun validateSchemaVersionAssignment(resourceId: UUID, schemaVersionId: UUID)
    {
        val request = requestRepository.findById(resourceId)
            ?: throw FieldValidationException("Information Request not found: $resourceId")
        val templateVersion = templateVersionRepository.findById(request.templateVersionId)
            ?: throw FieldValidationException("Information Request Template Version not found: ${request.templateVersionId}")
        if (templateVersion.schemaVersionId != schemaVersionId)
        {
            throw FieldValidationException(
                "Schema Version must match the Information Request Template Version Schema Version",
            )
        }
    }

    override fun valuesEditable(resourceId: UUID): Boolean =
        requestRepository.findById(resourceId)?.state in setOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CHANGES_REQUESTED,
        )

    private fun authorize(
        principal: PrincipalRef,
        action: Action,
        resourceId: UUID,
        context: AuthorizationContext,
        message: String,
    )
    {
        if (!isAllowed(principal, action, ResourceRef.informationRequest(resourceId), context))
        {
            throw ForbiddenException(message)
        }
    }

    private fun isAllowed(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Boolean =
        authorizationService.authorize(principal, action, resource, context) !is Decision.Deny

    private fun InformationRequest.ownerScope(): ScopeReference? = when (ownerType)
    {
        InformationRequestOwnerType.ORGANIZATION -> ownerOrganizationId?.let(ScopeReference::Organization)
        InformationRequestOwnerType.USER -> ownerUserId?.let(ScopeReference::Personal)
    }

    private fun InformationRequest.subscriptionContext(): SubscriptionContext? = when (ownerType)
    {
        InformationRequestOwnerType.ORGANIZATION -> ownerOrganizationId?.let(SubscriptionContext::forOrganization)
        InformationRequestOwnerType.USER -> ownerUserId?.let(SubscriptionContext::forUser)
    }
}
