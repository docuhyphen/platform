package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.PublishedExchangeGroupDtoTransformer
import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class TrustedExternalGroupQueryService @Inject constructor(
    private val validationService: TrustedRecipientValidationService,
    private val organizationGroupService: OrganizationGroupService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
    private val auditRecorder: AuditRecorder,
)
{
    fun listPublishedGroups(targetOrganizationId: UUID): List<PublishedExchangeGroupDto>
    {
        val caller = currentCaller()
        return try
        {
            requireAuthorized(caller)
            val validation = validationService.validateGroupDiscovery(caller.organizationId, targetOrganizationId)
            val groups = organizationGroupService.getPublishedExchangeGroups(validation.targetOrganization.id)
                .map(PublishedExchangeGroupDtoTransformer::toDto)
            record(caller, targetOrganizationId, AuditEventType.ORG_TRUST_PUBLISHED_GROUP_LISTED, AuditOutcome.SUCCESS)
            groups
        }
        catch (exception: RuntimeException)
        {
            record(caller, targetOrganizationId, AuditEventType.ORG_TRUST_PUBLISHED_GROUP_DENIED, AuditOutcome.DENIED)
            throw exception
        }
    }

    private fun currentCaller(): Caller
    {
        val organizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can discover published groups")
        }
        return Caller(principal.id, organizationId)
    }

    private fun requireAuthorized(caller: Caller)
    {
        val decision = authorizationService.authorize(
            PrincipalRef.user(caller.appUserId),
            Action.EXTERNAL_GROUP_DISCOVER,
            ResourceRef.organization(caller.organizationId),
            authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
    }

    private fun record(
        caller: Caller,
        targetOrganizationId: UUID,
        eventType: AuditEventType,
        outcome: AuditOutcome,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = AuditOwnerScope.Organization(caller.organizationId),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = caller.appUserId,
                actorKind = AuditActorKind.HUMAN,
                targetType = "ORGANIZATION",
                targetId = targetOrganizationId.toString(),
                payload = mapOf("targetOrganizationId" to targetOrganizationId.toString()),
            ),
        )
    }

    private data class Caller(val appUserId: UUID, val organizationId: UUID)
}
