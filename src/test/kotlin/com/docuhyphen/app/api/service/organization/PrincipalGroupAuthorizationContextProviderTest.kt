package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * A Principal Group states its own owner so an authorization decision about it no longer has to
 * fall back to whichever organization the caller has selected. A group with no single owner, and
 * a group that does not exist, resolve to nothing and are refused.
 */
class PrincipalGroupAuthorizationContextProviderTest
{
    @Test
    fun `an organization owned group resolves to its owning organization`()
    {
        val groupId = UUID.randomUUID()
        val organizationId = UUID.randomUUID()
        val provider = providerFor(
            groupId,
            group(groupId) {
                scope = PrincipalGroupScope.ORG
                ownerOrganizationId = organizationId
            },
        )

        val resolved = provider.resolve(groupId)!!

        assertEquals(OwnerContext.Organization(organizationId), resolved.ownerContext)
        assertEquals(false, resolved.isArchived)
        assertEquals(false, resolved.isSuspended)
    }

    @Test
    fun `a personally owned group resolves to its owning user`()
    {
        val groupId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        val provider = providerFor(
            groupId,
            group(groupId) {
                scope = PrincipalGroupScope.PERSONAL
                ownerAppUserId = appUserId
            },
        )

        assertEquals(OwnerContext.Personal(appUserId), provider.resolve(groupId)!!.ownerContext)
    }

    @Test
    fun `a co-owned group has no single owner and resolves to nothing`()
    {
        val groupId = UUID.randomUUID()
        val provider = providerFor(groupId, group(groupId) { scope = PrincipalGroupScope.SHARED_PROJECT })

        assertNull(provider.resolve(groupId))
    }

    @Test
    fun `an unknown group resolves to nothing`()
    {
        val repository = mock<PrincipalGroupRepository>()
        whenever(repository.findById(any())).thenReturn(null)

        assertNull(providerWith(repository).resolve(UUID.randomUUID()))
    }

    @Test
    fun `the provider serves the principal group kind`()
    {
        assertEquals(ResourceKind.PRINCIPAL_GROUP, providerWith(mock<PrincipalGroupRepository>()).supportedKind)
    }

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private fun group(groupId: UUID, configure: PrincipalGroup.() -> Unit) = PrincipalGroup().apply {
        id = groupId
        name = "process group"
        configure()
    }

    private fun providerFor(groupId: UUID, group: PrincipalGroup): PrincipalGroupAuthorizationContextProvider
    {
        val repository = mock<PrincipalGroupRepository>()
        whenever(repository.findById(groupId)).thenReturn(group)
        return providerWith(repository)
    }

    private fun providerWith(repository: PrincipalGroupRepository): PrincipalGroupAuthorizationContextProvider
    {
        val provider = PrincipalGroupAuthorizationContextProvider()
        PrincipalGroupAuthorizationContextProvider::class.java
            .getDeclaredField("principalGroupRepository")
            .apply { isAccessible = true }
            .set(provider, repository)
        return provider
    }
}

