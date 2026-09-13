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
 * A resource kind may narrow an allowed decision with facts only that kind understands, but it
 * can never widen one. A kind with no registered evaluator changes nothing, an evaluator that
 * cannot read its facts refuses the decision, and an evaluator is asked only about a decision the
 * central capability union already allowed.
 */
class ResourcePolicyEvaluatorTest
{
    private val userId = UUID.randomUUID()
    private val ownerOrgId = UUID.randomUUID()
    private val exchangeId = UUID.randomUUID()

    private val principal = PrincipalRef.user(userId)
    private val exchange = ResourceRef.exchange(exchangeId)
    private val context = AuthorizationContext()

    private val deniedReason = "PROCESS_STAGE_CLOSED"

    @Test
    fun `a kind with no registered evaluator is unchanged`()
    {
        val service = serviceWith(evaluator = null)

        assertTrue(service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context).isAllowed)
    }

    @Test
    fun `a registered evaluator can deny an otherwise allowed decision`()
    {
        val service = serviceWith(
            evaluator = evaluatorReturning(
                ResourcePolicyOutcome.Deny(deniedReason, "The stage this resource belongs to is closed"),
            ),
        )

        val decision = service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context)

        assertEquals(deniedReason, (decision as Decision.Deny).reasonCode)
    }

    @Test
    fun `an evaluator that cannot read its facts fails closed`()
    {
        val service = serviceWith(
            evaluator = evaluatorReturning(ResourcePolicyOutcome.FactsUnavailable("assignment unreadable")),
        )

        val decision = service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context)

        assertEquals(
            Decision.REASON_RESOURCE_POLICY_FACTS_UNAVAILABLE,
            (decision as Decision.Deny).reasonCode,
        )
    }

    @Test
    fun `an evaluator's obligations are added to the allowed decision`()
    {
        val service = serviceWith(
            evaluator = evaluatorReturning(
                ResourcePolicyOutcome.Permit(ShareObligations(watermark = true)),
            ),
        )

        val decision = service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context)

        assertTrue(decision.isAllowed)
        assertTrue(decision.obligations.watermark)
    }

    @Test
    fun `an evaluator is never asked about a decision the central stack already refused`()
    {
        val asked = mutableListOf<Action>()
        val evaluator = object : ResourcePolicyEvaluator
        {
            override val supportedKind: ResourceKind = ResourceKind.EXCHANGE

            override fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome
            {
                asked += request.action
                return ResourcePolicyOutcome.Permit()
            }
        }
        val service = serviceWith(evaluator = evaluator, shared = false)

        val decision = service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context)

        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
        assertTrue(asked.isEmpty()) { "a refused decision must not reach a resource-kind evaluator" }
    }

    @Test
    fun `the evaluator sees the capabilities the central stack resolved`()
    {
        var observed: Set<Capability> = emptySet()
        val evaluator = object : ResourcePolicyEvaluator
        {
            override val supportedKind: ResourceKind = ResourceKind.EXCHANGE

            override fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome
            {
                observed = request.capabilities
                return ResourcePolicyOutcome.Permit()
            }
        }
        val service = serviceWith(evaluator = evaluator)

        service.authorize(principal, Action.EXCHANGE_VIEW, exchange, context)

        assertTrue(observed.contains(Capability.EXCHANGE_READ))
    }

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private fun evaluatorReturning(outcome: ResourcePolicyOutcome) = object : ResourcePolicyEvaluator
    {
        override val supportedKind: ResourceKind = ResourceKind.EXCHANGE

        override fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome = outcome
    }

    private fun serviceWith(
        evaluator: ResourcePolicyEvaluator?,
        shared: Boolean = true,
    ): DefaultAuthorizationService
    {
        val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        whenever(contextRegistry.kindOf(any())).thenReturn(ResourceKind.EXCHANGE)
        whenever(contextRegistry.resolution(any<ResourceRef>())).thenReturn(
            ResourceContextResolution.Resolved(
                ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(ownerOrgId)),
            ),
        )

        val share = Share().apply {
            id = UUID.randomUUID()
            principalKind = PrincipalKind.USER
            principalId = userId
            resourceType = ResourceType.EXCHANGE
            resourceId = exchangeId
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
        }
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenReturn(if (shared) listOf(share) else emptyList())
        whenever(shareRepository.findById(eq(share.id!!))).thenReturn(share)

        val evaluatorRegistry = mock<ResourcePolicyEvaluatorRegistry>()
        whenever(evaluatorRegistry.evaluatorFor(any())).thenReturn(evaluator)

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
            parentGrantInheritanceResolver = ParentGrantInheritanceResolver(
                mock<ParentGrantInheritancePolicyRegistry>(),
                contextRegistry,
            ),
            resourcePolicyEvaluatorRegistry = evaluatorRegistry,
        )
    }
}
