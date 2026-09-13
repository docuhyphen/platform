package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionGrantRepository
import com.docuhyphen.app.api.service.subscription.BillingFrequency
import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.PlanLimits
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InformationRequestExecutionGrantServiceTest
{
    private val subscriptionAccessService: SubscriptionAccessService = mock()
    private val grantRepository: RequestExecutionGrantRepository = mock()
    private val requirementBindingRepository: InformationRequestTemplateRequirementBindingRepository = mock()
    private val service = InformationRequestExecutionGrantService(
        subscriptionAccessService,
        grantRepository,
        requirementBindingRepository,
    )

    init
    {
        whenever(requirementBindingRepository.findOrdered(org.mockito.kotlin.any())).thenReturn(emptyList())
    }

    @Test
    fun `issuing a grant for an organization owned request on an active paid plan freezes the owner and plan with no trial expiry`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(
                subscription(
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    ownerId = organizationId,
                    status = SubscriptionStatus.ACTIVE,
                    limits = planLimits(maxAdditionalParticipants = 4L),
                ),
            )
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(grantRepository.save(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        val grant = service.issueGrant(request, exchange)

        assertEquals(request.id, grant.requestId)
        assertEquals(SubscriptionOwnerType.ORGANIZATION.name, grant.ownerType)
        assertEquals(organizationId, grant.ownerOrganizationId)
        assertNull(grant.ownerUserId)
        assertEquals(PlanCode.BUSINESS.name, grant.planCode)
        assertEquals(SubscriptionStatus.ACTIVE.name, grant.subscriptionStatus)
        assertEquals(SubscriptionEnforcementMode.ENFORCE.name, grant.enforcementMode)
        assertNull(grant.trialExpiresAt)
        assertNull(grant.mutationAllowanceExpiresAt)
        assertEquals(4L, grant.additionalRecipientCap)
        assertNull(grant.revokedAt)
    }

    @Test
    fun `issuing a grant for a Template with no Field-bound Requirements never checks the Business Fields feature`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(requirementBindingRepository.findOrdered(request.templateVersionId)).thenReturn(
            listOf(fieldlessBinding()),
        )
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(
                subscription(
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    ownerId = organizationId,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(grantRepository.save(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        service.issueGrant(request, exchange)

        verify(subscriptionAccessService, never()).requireFeature(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS),
        )
    }

    @Test
    fun `issuing a grant for a Template with a Field-bound Requirement requires the Business Fields feature`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(requirementBindingRepository.findOrdered(request.templateVersionId)).thenReturn(
            listOf(fieldBoundBinding()),
        )
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(
                subscription(
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    ownerId = organizationId,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(grantRepository.save(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        service.issueGrant(request, exchange)

        verify(subscriptionAccessService).requireFeature(
            SubscriptionContext.forOrganization(organizationId),
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
        )
    }

    @Test
    fun `issuing a grant is refused when a Field-bound Requirement is present but the owner lacks the Business Fields feature`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(requirementBindingRepository.findOrdered(request.templateVersionId)).thenReturn(
            listOf(fieldBoundBinding()),
        )
        whenever(
            subscriptionAccessService.requireFeature(
                SubscriptionContext.forOrganization(organizationId),
                PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            ),
        ).thenThrow(IllegalStateException("Business Fields and schemas is not included"))

        assertThrows<IllegalStateException> { service.issueGrant(request, exchange) }
        verify(grantRepository, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `issuing a grant for a personally owned request while trialing freezes the trial end as the mutation allowance`()
    {
        val userId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = personalExchange(userId)
        val trialEnd = Instant.now().plus(10, ChronoUnit.DAYS)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forUser(userId)))
            .thenReturn(
                subscription(
                    ownerType = SubscriptionOwnerType.USER,
                    ownerId = userId,
                    status = SubscriptionStatus.TRIALING,
                    currentPeriodEnd = trialEnd,
                ),
            )
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(grantRepository.save(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        val grant = service.issueGrant(request, exchange)

        assertEquals(SubscriptionOwnerType.USER.name, grant.ownerType)
        assertEquals(userId, grant.ownerUserId)
        assertEquals(trialEnd, grant.trialExpiresAt?.toInstant())
        assertEquals(trialEnd, grant.mutationAllowanceExpiresAt?.toInstant())
    }

    @Test
    fun `issuing a grant for a past due owner freezes the grace period end as the mutation allowance`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        val graceEnd = Instant.now().plus(3, ChronoUnit.DAYS)
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(
                subscription(
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    ownerId = organizationId,
                    status = SubscriptionStatus.PAST_DUE,
                    gracePeriodEnd = graceEnd,
                ),
            )
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)
        whenever(grantRepository.save(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        val grant = service.issueGrant(request, exchange)

        assertNull(grant.trialExpiresAt)
        assertEquals(graceEnd, grant.mutationAllowanceExpiresAt?.toInstant())
    }

    @Test
    fun `issuing a grant twice for the same request returns the original grant rather than re-pricing it`()
    {
        val organizationId = UUID.randomUUID()
        val request = requestFor(UUID.randomUUID())
        val exchange = organizationExchange(organizationId)
        val existing = RequestExecutionGrant().apply { requestId = request.id }
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(existing)

        val grant = service.issueGrant(request, exchange)

        assertSame(existing, grant)
        verify(subscriptionAccessService, never()).resolve(org.mockito.kotlin.any())
        verify(grantRepository, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `an exchange with no owner cannot be granted`()
    {
        val request = requestFor(UUID.randomUUID())
        val exchange = Exchange().apply { id = UUID.randomUUID() }
        whenever(grantRepository.findByRequestId(request.id)).thenReturn(null)

        assertThrows<IllegalStateException> { service.issueGrant(request, exchange) }
    }

    @Test
    fun `revoking an issued grant records the reason and a revocation timestamp`()
    {
        val requestId = UUID.randomUUID()
        val existing = RequestExecutionGrant().apply { this.requestId = requestId }
        whenever(grantRepository.findByRequestId(requestId)).thenReturn(existing)
        whenever(grantRepository.update(org.mockito.kotlin.any())).thenAnswer { it.getArgument(0) }

        val revoked = service.revoke(requestId, "fraud-investigation")

        assertEquals("fraud-investigation", revoked.revokedReason)
        assertEquals(true, revoked.revokedAt != null)
        verify(grantRepository).update(existing)
    }

    @Test
    fun `revoking an already revoked grant is idempotent and keeps the original reason and timestamp`()
    {
        val requestId = UUID.randomUUID()
        val originalRevokedAt = java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))
        val existing = RequestExecutionGrant().apply {
            this.requestId = requestId
            revokedAt = originalRevokedAt
            revokedReason = "original-reason"
        }
        whenever(grantRepository.findByRequestId(requestId)).thenReturn(existing)

        val revoked = service.revoke(requestId, "second-reason")

        assertEquals("original-reason", revoked.revokedReason)
        assertEquals(originalRevokedAt, revoked.revokedAt)
        verify(grantRepository, never()).update(org.mockito.kotlin.any())
    }

    @Test
    fun `revoking a request that was never issued a grant throws`()
    {
        val requestId = UUID.randomUUID()
        whenever(grantRepository.findByRequestId(requestId)).thenReturn(null)

        assertThrows<IllegalStateException> { service.revoke(requestId, "any-reason") }
    }

    private fun requestFor(id: UUID) = InformationRequest().apply {
        this.id = id
        templateVersionId = UUID.randomUUID()
    }

    private fun fieldBoundBinding() = InformationRequestTemplateRequirementBinding().apply {
        collectedFieldDefinitionId = UUID.randomUUID()
    }

    private fun fieldlessBinding() = InformationRequestTemplateRequirementBinding().apply {
        collectedFieldDefinitionId = null
    }

    private fun organizationExchange(owner: UUID) = Exchange().apply {
        id = UUID.randomUUID()
        ownerOrganizationId = owner
    }

    private fun personalExchange(owner: UUID) = Exchange().apply {
        id = UUID.randomUUID()
        ownerUserId = owner
    }

    private fun planLimits(maxAdditionalParticipants: Long? = null) = PlanLimits(
        maxNewExchangesPerCalendarMonth = null,
        maxOpenExchanges = null,
        maxAdditionalParticipantsPerExchange = maxAdditionalParticipants,
        includedSeats = null,
        seatsArePurchased = false,
    )

    private fun subscription(
        ownerType: SubscriptionOwnerType,
        ownerId: UUID,
        status: SubscriptionStatus,
        limits: PlanLimits = planLimits(),
        currentPeriodEnd: Instant? = null,
        gracePeriodEnd: Instant? = null,
    ) = EffectiveSubscription(
        planCode = if (ownerType == SubscriptionOwnerType.ORGANIZATION) PlanCode.BUSINESS else PlanCode.PERSONAL,
        ownerType = ownerType,
        ownerId = ownerId,
        status = status,
        features = emptySet(),
        limits = limits,
        billingFrequency = BillingFrequency.MONTHLY,
        currentPeriodStart = null,
        currentPeriodEnd = currentPeriodEnd,
        gracePeriodEnd = gracePeriodEnd,
        purchasedSeats = null,
        upgradePlanCode = null,
    )
}
