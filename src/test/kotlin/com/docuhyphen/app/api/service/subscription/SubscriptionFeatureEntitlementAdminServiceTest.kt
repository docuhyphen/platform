package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionFeatureEntitlement
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * One writer records the platform-administered feature decisions of either kind of subscription
 * owner. The owner kind decides which column the decision is stored in and which rows a replacement
 * is allowed to touch, so an organization and an individual account never overwrite each other.
 */
class SubscriptionFeatureEntitlementAdminServiceTest
{
    private val organizationId: UUID = UUID.randomUUID()
    private val appUserId: UUID = UUID.randomUUID()
    private val actorId: UUID = UUID.randomUUID()

    private val repository: SubscriptionFeatureEntitlementRepository = mock()
    private val service = SubscriptionFeatureEntitlementAdminService(repository)

    @Test
    fun `a decision recorded for a person names the person as its owner`()
    {
        whenever(repository.findByAppUserId(appUserId)).thenReturn(emptyList())

        service.replaceDecisions(
            owner = SubscriptionContext.forUser(appUserId),
            requested = listOf(FeatureEntitlementDecision("PROCESS_CAPABILITY", true)),
            actorId = actorId,
        )

        val saved = argumentCaptor<SubscriptionFeatureEntitlement>()
        verify(repository).save(saved.capture())
        assertEquals(SubscriptionOwnerType.USER.name, saved.firstValue.ownerType)
        assertEquals(appUserId, saved.firstValue.appUserId)
        assertNull(saved.firstValue.organizationId)
        assertEquals(actorId, saved.firstValue.updatedByAppUserId)
    }

    @Test
    fun `a decision recorded for an organization names the organization as its owner`()
    {
        whenever(repository.findByOrganizationId(organizationId)).thenReturn(emptyList())

        service.replaceDecisions(
            owner = SubscriptionContext.forOrganization(organizationId),
            requested = listOf(FeatureEntitlementDecision("PROCESS_CAPABILITY", false)),
            actorId = actorId,
        )

        val saved = argumentCaptor<SubscriptionFeatureEntitlement>()
        verify(repository).save(saved.capture())
        assertEquals(SubscriptionOwnerType.ORGANIZATION.name, saved.firstValue.ownerType)
        assertEquals(organizationId, saved.firstValue.organizationId)
        assertNull(saved.firstValue.appUserId)
    }

    @Test
    fun `a replacement reads and rewrites only the decisions of the owner it names`()
    {
        whenever(repository.findByAppUserId(appUserId)).thenReturn(emptyList())

        service.replaceDecisions(
            owner = SubscriptionContext.forUser(appUserId),
            requested = listOf(FeatureEntitlementDecision("PROCESS_CAPABILITY", true)),
            actorId = actorId,
        )

        verify(repository, never()).findByOrganizationId(any())
    }

    @Test
    fun `codes are normalized and decisions the request omits are withdrawn`()
    {
        val removed = personalDecision("RETIRED_CAPABILITY", true)
        val retained = personalDecision("PROCESS_CAPABILITY", false)
        whenever(repository.findByAppUserId(appUserId)).thenReturn(listOf(removed, retained))

        service.replaceDecisions(
            owner = SubscriptionContext.forUser(appUserId),
            requested = listOf(FeatureEntitlementDecision("  process_capability  ", true)),
            actorId = actorId,
        )

        verify(repository).delete(removed)
        verify(repository).update(retained)
        verify(repository, never()).save(any())
        assertTrue(retained.isEnabled)
    }

    @Test
    fun `duplicate normalized codes are refused before anything is written`()
    {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.replaceDecisions(
                owner = SubscriptionContext.forUser(appUserId),
                requested = listOf(
                    FeatureEntitlementDecision("PROCESS_CAPABILITY", true),
                    FeatureEntitlementDecision("process_capability", false),
                ),
                actorId = actorId,
            )
        }

        assertEquals("Feature entitlement codes must be unique", exception.message)
        verify(repository, never()).save(any())
        verify(repository, never()).update(any())
        verify(repository, never()).delete(any())
    }

    @Test
    fun `a code outside the stored shape is refused before anything is written`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            service.replaceDecisions(
                owner = SubscriptionContext.forUser(appUserId),
                requested = listOf(FeatureEntitlementDecision("process capability", true)),
                actorId = actorId,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `more decisions than one owner may hold are refused before anything is written`()
    {
        val requested = (1..101).map { FeatureEntitlementDecision("PROCESS_CAPABILITY_$it", true) }

        assertThrows(IllegalArgumentException::class.java) {
            service.replaceDecisions(
                owner = SubscriptionContext.forUser(appUserId),
                requested = requested,
                actorId = actorId,
            )
        }

        verify(repository, never()).save(any())
    }

    @Test
    fun `the owner's stored decisions are read from the column that belongs to the owner kind`()
    {
        val personal = personalDecision("PROCESS_CAPABILITY", true)
        whenever(repository.findByAppUserId(appUserId)).thenReturn(listOf(personal))

        val decisions = service.findDecisions(SubscriptionContext.forUser(appUserId))

        assertEquals(listOf(personal), decisions)
        verify(repository, never()).findByOrganizationId(any())
    }

    @Test
    fun `a replacement reports the state on either side of it in code order`()
    {
        val withdrawn = personalDecision("WITHDRAWN_CAPABILITY", true)
        val granted = personalDecision("PROCESS_CAPABILITY", false)
        whenever(repository.findByAppUserId(appUserId))
            .thenReturn(listOf(withdrawn, granted))
            .thenReturn(listOf(granted))

        val replacement = service.replaceDecisions(
            owner = SubscriptionContext.forUser(appUserId),
            requested = listOf(FeatureEntitlementDecision("PROCESS_CAPABILITY", true)),
            actorId = actorId,
        )

        assertEquals(
            "PROCESS_CAPABILITY=false,WITHDRAWN_CAPABILITY=true",
            replacement.beforeSnapshot,
        )
        assertEquals("PROCESS_CAPABILITY=true", replacement.afterSnapshot)
        assertEquals(listOf(granted), replacement.entitlements)
    }

    private fun personalDecision(code: String, enabled: Boolean): SubscriptionFeatureEntitlement =
        SubscriptionFeatureEntitlement().apply {
            ownerType = SubscriptionOwnerType.USER.name
            appUserId = this@SubscriptionFeatureEntitlementAdminServiceTest.appUserId
            featureCode = code
            isEnabled = enabled
            updatedByAppUserId = actorId
        }
}
