package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class AuditOwnerScopeResolverTest
{
    private val registry: ResourceAuthorizationContextRegistry = mock()
    private val resolver = AuditOwnerScopeResolver(registry)

    @Test
    fun `personally owned resource resolves to a Personal audit owner, never Platform`()
    {
        val resourceId = UUID.randomUUID()
        val ownerUserId = UUID.randomUUID()
        whenever(registry.resolve(ResourceRef(ResourceType.EXCHANGE, resourceId))).thenReturn(
            ResourceAuthorizationContext(
                ownerContext = OwnerContext.Personal(ownerUserId),
                isArchived = false,
                isSuspended = false,
            ),
        )

        val scope = resolver.resolve(ResourceType.EXCHANGE, resourceId)

        assertEquals(AuditOwnerScope.Personal(ownerUserId), scope)
    }

    @Test
    fun `organization owned resource resolves to an Organization audit owner`()
    {
        val resourceId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        whenever(registry.resolve(ResourceRef(ResourceType.EXCHANGE, resourceId))).thenReturn(
            ResourceAuthorizationContext(
                ownerContext = OwnerContext.Organization(organizationId),
                isArchived = false,
                isSuspended = false,
            ),
        )

        val scope = resolver.resolve(ResourceType.EXCHANGE, resourceId)

        assertEquals(AuditOwnerScope.Organization(organizationId), scope)
    }

    @Test
    fun `unresolvable resource falls back to Platform`()
    {
        val resourceId = UUID.randomUUID()
        whenever(registry.resolve(ResourceRef(ResourceType.EXCHANGE, resourceId))).thenReturn(null)

        val scope = resolver.resolve(ResourceType.EXCHANGE, resourceId)

        assertEquals(AuditOwnerScope.Platform, scope)
    }
}

