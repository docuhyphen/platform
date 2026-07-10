package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * Fail-closed vs degraded routing selects the correct behavior per event class.
 */
class AuditFailurePolicyResolverTest
{
    @Test
    fun `defaults every category to DEGRADED when no fail-closed categories are configured`()
    {
        val resolver = AuditFailurePolicyResolver(failClosedCategoriesConfig = Optional.of(""))

        AuditCategory.entries.forEach { category ->
            assertEquals(AuditFailurePolicy.DEGRADED, resolver.resolve(category))
        }
    }

    @Test
    fun `routes a configured category to FAIL_CLOSED and leaves others DEGRADED`()
    {
        val resolver = AuditFailurePolicyResolver(failClosedCategoriesConfig = Optional.of("EXCHANGE"))

        assertEquals(AuditFailurePolicy.FAIL_CLOSED, resolver.resolve(AuditCategory.EXCHANGE))
        assertEquals(AuditFailurePolicy.DEGRADED, resolver.resolve(AuditCategory.DOCUMENT))
    }

    @Test
    fun `parses comma-separated categories case-insensitively and trims whitespace`()
    {
        val resolver = AuditFailurePolicyResolver(failClosedCategoriesConfig = Optional.of(" exchange ,Security"))

        assertEquals(AuditFailurePolicy.FAIL_CLOSED, resolver.resolve(AuditCategory.EXCHANGE))
        assertEquals(AuditFailurePolicy.FAIL_CLOSED, resolver.resolve(AuditCategory.SECURITY))
        assertEquals(AuditFailurePolicy.DEGRADED, resolver.resolve(AuditCategory.ORGANIZATION))
    }

    @Test
    fun `ignores unknown category names instead of failing`()
    {
        val resolver = AuditFailurePolicyResolver(failClosedCategoriesConfig = Optional.of("NOT_A_REAL_CATEGORY,EXCHANGE"))

        assertEquals(AuditFailurePolicy.FAIL_CLOSED, resolver.resolve(AuditCategory.EXCHANGE))
    }
}
