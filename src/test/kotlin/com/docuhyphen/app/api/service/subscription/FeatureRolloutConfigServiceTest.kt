package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

/**
 * Covers the operational readiness decision: which owners a capability under controlled release
 * has been turned on for in this deployment. It is deliberately not the commercial decision about
 * whether an owner bought the capability.
 */
class FeatureRolloutConfigServiceTest
{
    private val appUserId: UUID = UUID.fromString("2f6bd1f8-7c4a-4d3e-9a55-0b1c2d3e4f50")
    private val organizationId: UUID = UUID.fromString("8c1a2b3d-4e5f-4061-8273-94a5b6c7d8e9")

    private fun service(grants: String) = FeatureRolloutConfigService(Optional.of(grants))

    @Test
    fun `no configured grant leaves every owner denied`()
    {
        val service = service("")

        assertFalse(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertFalse(
            service.isGranted(
                SubscriptionContext.forOrganization(organizationId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertEquals(
            emptySet<PlanFeature>(),
            service.grantedFeatures(SubscriptionContext.forUser(appUserId)),
        )
    }

    @Test
    fun `a grant names the capability the owner kind and the owner`()
    {
        val service = service("INFORMATION_REQUESTS:USER:$appUserId")

        assertTrue(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertEquals(
            setOf(PlanFeature.INFORMATION_REQUESTS),
            service.grantedFeatures(SubscriptionContext.forUser(appUserId)),
        )
    }

    @Test
    fun `an organization grant is never read by an individual account with the same id`()
    {
        val sharedId = UUID.fromString("11111111-2222-4333-8444-555555555555")
        val service = service("INFORMATION_REQUESTS:ORGANIZATION:$sharedId")

        assertTrue(
            service.isGranted(
                SubscriptionContext.forOrganization(sharedId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertFalse(
            service.isGranted(
                SubscriptionContext.forUser(sharedId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
            "An individual account must not inherit an organization's rollout grant",
        )
    }

    @Test
    fun `a grant opens only the capability it names`()
    {
        val service = service("INFORMATION_REQUESTS:USER:$appUserId")

        assertFalse(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            ),
        )
    }

    @Test
    fun `several grants are read independently of each other`()
    {
        val otherUserId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        val service = service(
            "INFORMATION_REQUESTS:USER:$appUserId," +
                "INFORMATION_REQUESTS:ORGANIZATION:$organizationId",
        )

        assertTrue(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertTrue(
            service.isGranted(
                SubscriptionContext.forOrganization(organizationId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
        assertFalse(
            service.isGranted(
                SubscriptionContext.forUser(otherUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
    }

    @Test
    fun `an entry is read regardless of casing and surrounding whitespace`()
    {
        val service = service("  information_requests : user : ${appUserId.toString().uppercase()}  ")

        assertTrue(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
    }

    @Test
    fun `an entry that cannot be read grants nothing`()
    {
        val malformed = listOf(
            "NOT_A_FEATURE:USER:$appUserId",
            "INFORMATION_REQUESTS:NOT_AN_OWNER_KIND:$appUserId",
            "INFORMATION_REQUESTS:USER:not-a-uuid",
            "INFORMATION_REQUESTS:USER",
            "INFORMATION_REQUESTS:USER:$appUserId:extra",
            ":::",
        )

        malformed.forEach { entry ->
            assertFalse(
                service(entry).isGranted(
                    SubscriptionContext.forUser(appUserId),
                    PlanFeature.INFORMATION_REQUESTS,
                ),
                "'$entry' cannot be read as a grant and must not open the capability",
            )
        }
    }

    @Test
    fun `an unreadable entry never withdraws a readable one beside it`()
    {
        val service = service("NOT_A_FEATURE:USER:$appUserId,,INFORMATION_REQUESTS:USER:$appUserId,")

        assertTrue(
            service.isGranted(
                SubscriptionContext.forUser(appUserId),
                PlanFeature.INFORMATION_REQUESTS,
            ),
        )
    }
}
