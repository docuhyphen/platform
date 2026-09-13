package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * A resource takes grants from another resource only when its kind has explicitly registered
 * that it does, the two resources share one owner, the reference is exactly one level deep, and
 * the parent's own facts can be produced. Every other case either inherits nothing or refuses
 * the decision.
 *
 * The child used here is a Document whose resolved context names its Exchange, because a Document
 * Share role currently maps to no capability of its own, so anything the child can do had to
 * arrive through inheritance.
 */
class ParentGrantInheritanceTest
{
    private val userId = UUID.randomUUID()
    private val ownerOrgId = UUID.randomUUID()
    private val documentId = UUID.randomUUID()
    private val exchangeId = UUID.randomUUID()
    private val grandParentExchangeId = UUID.randomUUID()

    private val principal = PrincipalRef.user(userId)
    private val document = ResourceRef.document(documentId)
    private val exchange = ResourceRef.exchange(exchangeId)
    private val context = AuthorizationContext(mfaSatisfied = true)

    @Test
    fun `a named parent contributes nothing while no policy is registered for the kind`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ownedContext(),
            inheritedKinds = emptySet(),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
        assertTrue(service.capabilities(principal, document, context).isEmpty())
    }

    @Test
    fun `a registered kind inherits its parent's grants`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ownedContext(),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertTrue(decision.isAllowed) { "a registered child must reach its parent's grant" }
        val grants = service.grantsOn(principal, document, context)
        assertEquals(listOf(exchange), grants.mapNotNull { it.inheritedFrom })
    }

    @Test
    fun `inheritance never walks past the immediate parent`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ownedContext(parentRef = ResourceRef.exchange(grandParentExchangeId)),
            sharedResource = ResourceRef.exchange(grandParentExchangeId),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `a parent owned by somebody else refuses the decision`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ResourceAuthorizationContext(
                ownerContext = OwnerContext.Organization(UUID.randomUUID()),
            ),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_PARENT_OWNER_MISMATCH, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `a resource that names itself as its parent is refused as a cycle`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = document),
            parentContext = ownedContext(),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_PARENT_INHERITANCE_CYCLE, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `two resources naming each other are refused as a cycle`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ownedContext(parentRef = document),
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_PARENT_INHERITANCE_CYCLE, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `an unresolvable parent fails closed instead of falling back to the child's own grants`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = null,
        )

        val decision = service.authorize(principal, Action.DOCUMENT_VIEW, document, context)

        assertEquals(Decision.REASON_PARENT_CONTEXT_UNRESOLVED, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `an inherited grant still answers to the parent Share's own constraints`()
    {
        val service = serviceWith(
            childContext = ownedContext(parentRef = exchange),
            parentContext = ownedContext(),
            shareConstraintsJson = """{"require_mfa":true}""",
        )

        val decision = service.authorize(
            principal,
            Action.DOCUMENT_VIEW,
            document,
            AuthorizationContext(mfaSatisfied = false),
        )

        assertEquals(Decision.REASON_MFA_REQUIRED, (decision as Decision.Deny).reasonCode)
    }

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private fun ownedContext(parentRef: ResourceRef? = null) = ResourceAuthorizationContext(
        ownerContext = OwnerContext.Organization(ownerOrgId),
        parentRef = parentRef,
    )

    private fun serviceWith(
        childContext: ResourceAuthorizationContext?,
        parentContext: ResourceAuthorizationContext?,
        inheritedKinds: Set<ResourceKind> = setOf(ResourceKind.DOCUMENT),
        sharedResource: ResourceRef = exchange,
        shareConstraintsJson: String? = null,
    ): DefaultAuthorizationService
    {
        val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        whenever(contextRegistry.kindOf(any())).thenAnswer { invocation ->
            when ((invocation.arguments[0] as ResourceRef).type)
            {
                ResourceType.DOCUMENT -> ResourceKind.DOCUMENT
                ResourceType.EXCHANGE -> ResourceKind.EXCHANGE
                else -> null
            }
        }
        whenever(contextRegistry.resolution(any<ResourceRef>())).thenAnswer { invocation ->
            val ref = invocation.arguments[0] as ResourceRef
            val resolved = when (ref.type)
            {
                ResourceType.DOCUMENT -> childContext
                else -> parentContext
            }
            resolved?.let(ResourceContextResolution::Resolved) ?: ResourceContextResolution.Unresolved
        }

        val policyRegistry = mock<ParentGrantInheritancePolicyRegistry>()
        inheritedKinds.forEach { kind ->
            whenever(policyRegistry.policyFor(eq(kind))).thenReturn(passThroughPolicy(kind))
        }

        val share = Share().apply {
            id = UUID.randomUUID()
            principalKind = PrincipalKind.USER
            principalId = userId
            resourceType = sharedResource.type
            resourceId = sharedResource.id
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
            constraintsJson = shareConstraintsJson
        }
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenAnswer { invocation ->
                val type = invocation.arguments[2] as ResourceType
                val id = invocation.arguments[3] as UUID
                if (type == sharedResource.type && id == sharedResource.id) listOf(share) else emptyList<Share>()
            }
        whenever(shareRepository.findById(eq(share.id!!))).thenReturn(share)

        return DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
            },
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = contextRegistry,
            parentGrantInheritanceResolver = ParentGrantInheritanceResolver(policyRegistry, contextRegistry),
            resourcePolicyEvaluatorRegistry = mock<ResourcePolicyEvaluatorRegistry>(),
        )
    }

    private fun passThroughPolicy(kind: ResourceKind) = object : ParentGrantInheritancePolicy
    {
        override val supportedKind: ResourceKind = kind
    }
}
