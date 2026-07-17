package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.service.auth.AuthAuditService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationExchangePolicyGroupTest
{
    private val senderOrganizationId = UUID.randomUUID()
    private val targetOrganizationId = UUID.randomUUID()
    private val linkService = mock<OrganizationExchangeLinkService>()
    private val authTokenContext = AuthTokenContext().apply {
        activeOrganizationId = senderOrganizationId
    }
    private val service = OrganizationExchangePolicyService(
        organizationService = mock<OrganizationService>(),
        authTokenContext = authTokenContext,
        organizationMembershipService = mock<OrganizationMembershipService>(),
        organizationExchangeLinkService = linkService,
        authAuditService = mock<AuthAuditService>(),
    )

    @Test
    fun `allows an active published group from a paired organization`()
    {
        val group = externalGroup()
        whenever(linkService.hasAcceptedLink(senderOrganizationId, targetOrganizationId)).thenReturn(true)

        assertDoesNotThrow {
            service.assertCanShareWithGroup(UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects an unpublished external organization group`()
    {
        val group = externalGroup().apply { externallyPublished = false }
        whenever(linkService.hasAcceptedLink(senderOrganizationId, targetOrganizationId)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects an inactive external organization group`()
    {
        val group = externalGroup().apply { isActive = false }
        whenever(linkService.hasAcceptedLink(senderOrganizationId, targetOrganizationId)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects a published group from an unpaired organization`()
    {
        val group = externalGroup()
        whenever(linkService.hasAcceptedLink(senderOrganizationId, targetOrganizationId)).thenReturn(false)

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects a personal group owned by another user`()
    {
        val initiatorId = UUID.randomUUID()
        val group = PrincipalGroup().apply {
            scope = PrincipalGroupScope.PERSONAL
            ownerAppUserId = UUID.randomUUID()
            isActive = true
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(initiatorId, group)
        }
    }

    private fun externalGroup() = PrincipalGroup().apply {
        scope = PrincipalGroupScope.ORG
        ownerOrganizationId = targetOrganizationId
        externallyPublished = true
        isActive = true
    }
}
