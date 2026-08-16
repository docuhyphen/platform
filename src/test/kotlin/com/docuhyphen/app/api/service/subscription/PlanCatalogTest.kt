package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanCatalogTest
{
    @Test
    fun `free is an individual plan limited to the basic sharing flow`()
    {
        val free = PlanCatalog.definitionOf(PlanCode.FREE)

        assertEquals(SubscriptionOwnerType.USER, free.ownerType)
        assertEquals(
            setOf(PlanFeature.EXCHANGE_CREATE, PlanFeature.DOCUMENT_COMMENTS),
            free.features,
        )
        assertEquals(5L, free.limits.maxNewExchangesPerCalendarMonth)
        assertEquals(3L, free.limits.maxOpenExchanges)
        assertEquals(0L, free.limits.maxAdditionalParticipantsPerExchange)
        assertEquals(1L, free.limits.includedSeats)
        assertFalse(free.limits.seatsArePurchased)
        assertEquals(PlanCode.PERSONAL, free.upgradePlanCode)
    }

    @Test
    fun `personal is an individual plan without commercial exchange quotas`()
    {
        val personal = PlanCatalog.definitionOf(PlanCode.PERSONAL)

        assertEquals(SubscriptionOwnerType.USER, personal.ownerType)
        assertNull(personal.limits.maxNewExchangesPerCalendarMonth)
        assertNull(personal.limits.maxOpenExchanges)
        assertNull(personal.limits.maxAdditionalParticipantsPerExchange)
        assertEquals(1L, personal.limits.includedSeats)
        assertFalse(personal.limits.seatsArePurchased)
        assertEquals(PlanCode.BUSINESS, personal.upgradePlanCode)

        assertEquals(
            setOf(
                PlanFeature.EXCHANGE_CREATE,
                PlanFeature.MULTIPLE_PARTICIPANTS,
                PlanFeature.BLUEPRINT_USE,
                PlanFeature.BLUEPRINT_MANAGE,
                PlanFeature.DOCUMENT_LIBRARY_USE,
                PlanFeature.DOCUMENT_LIBRARY_MANAGE,
                PlanFeature.DOCUMENT_COMMENTS,
                PlanFeature.DOCUMENT_VERSION_HISTORY,
                PlanFeature.ADVANCED_ACCESS_CONTROLS,
                PlanFeature.VARIABLES_AND_SEQUENCES,
            ),
            personal.features,
        )
    }

    @Test
    fun `personal excludes every business only feature`()
    {
        val personal = PlanCatalog.definitionOf(PlanCode.PERSONAL)

        listOf(
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            PlanFeature.WORKFLOW_AUTOMATION,
            PlanFeature.ORGANIZATION_ADMINISTRATION,
            PlanFeature.AUDIT_GOVERNANCE,
            PlanFeature.IDENTITY_AND_INTEGRATIONS,
        ).forEach { feature ->
            assertFalse(personal.includes(feature), "Personal must not include $feature")
        }
    }

    @Test
    fun `reminders arrive with workflow authoring and no individual plan sells one`()
    {
        // A reminder only exists as an addon on a workflow step, so granting one to an
        // individual would grant workflow authoring with it. The feature name is historical and
        // does not mean the Personal plan owns it.
        PlanCatalog.plansFor(SubscriptionOwnerType.USER).forEach { plan ->
            assertFalse(
                plan.includes(PlanFeature.PERSONAL_REMINDERS),
                "${plan.planCode} is an individual plan and must not sell a reminder",
            )
            assertFalse(
                plan.includes(PlanFeature.WORKFLOW_AUTOMATION),
                "${plan.planCode} is an individual plan and must not sell workflow automation",
            )
        }

        assertTrue(PlanCatalog.definitionOf(PlanCode.BUSINESS).includes(PlanFeature.PERSONAL_REMINDERS))
    }

    @Test
    fun `business is the organization plan and includes every feature`()
    {
        val business = PlanCatalog.definitionOf(PlanCode.BUSINESS)

        assertEquals(SubscriptionOwnerType.ORGANIZATION, business.ownerType)
        assertEquals(PlanFeature.entries.toSet(), business.features)
        assertTrue(business.limits.seatsArePurchased)
        assertNull(business.limits.includedSeats)
        assertNull(business.upgradePlanCode)
    }

    @Test
    fun `business is a superset of personal which is a superset of free`()
    {
        val free = PlanCatalog.definitionOf(PlanCode.FREE).features
        val personal = PlanCatalog.definitionOf(PlanCode.PERSONAL).features
        val business = PlanCatalog.definitionOf(PlanCode.BUSINESS).features

        assertTrue(personal.containsAll(free))
        assertTrue(business.containsAll(personal))
    }

    @Test
    fun `individual plans cannot be assigned to an organization`()
    {
        listOf(PlanCode.FREE, PlanCode.PERSONAL).forEach { planCode ->
            assertFalse(PlanCatalog.isAssignableTo(planCode, SubscriptionOwnerType.ORGANIZATION))
            assertThrows(IllegalArgumentException::class.java) {
                PlanCatalog.requireAssignableTo(planCode, SubscriptionOwnerType.ORGANIZATION)
            }
        }
    }

    @Test
    fun `business cannot be assigned to an individual user`()
    {
        assertFalse(PlanCatalog.isAssignableTo(PlanCode.BUSINESS, SubscriptionOwnerType.USER))
        assertThrows(IllegalArgumentException::class.java) {
            PlanCatalog.requireAssignableTo(PlanCode.BUSINESS, SubscriptionOwnerType.USER)
        }
    }

    @Test
    fun `plans are grouped by the owner that can hold them`()
    {
        assertEquals(
            listOf(PlanCode.FREE, PlanCode.PERSONAL),
            PlanCatalog.plansFor(SubscriptionOwnerType.USER).map { it.planCode },
        )
        assertEquals(
            listOf(PlanCode.BUSINESS),
            PlanCatalog.plansFor(SubscriptionOwnerType.ORGANIZATION).map { it.planCode },
        )
        assertEquals(3, PlanCatalog.all().size)
    }

    @Test
    fun `provisioning defaults match the owner they are created for`()
    {
        assertEquals(PlanCode.FREE, PlanCatalog.DEFAULT_USER_PLAN)
        assertEquals(PlanCode.BUSINESS, PlanCatalog.DEFAULT_ORGANIZATION_PLAN)
        assertTrue(PlanCatalog.isAssignableTo(PlanCatalog.DEFAULT_USER_PLAN, SubscriptionOwnerType.USER))
        assertTrue(
            PlanCatalog.isAssignableTo(
                PlanCatalog.DEFAULT_ORGANIZATION_PLAN,
                SubscriptionOwnerType.ORGANIZATION,
            ),
        )
    }

    @Test
    fun `plan codes parse case insensitively and reject unknown values`()
    {
        assertEquals(PlanCode.BUSINESS, PlanCode.fromCode(" business "))
        assertNull(PlanCode.fromCodeOrNull("ENTERPRISE"))
        assertNull(PlanCode.fromCodeOrNull(null))
        assertThrows(IllegalArgumentException::class.java) { PlanCode.fromCode("ENTERPRISE") }
    }
}



