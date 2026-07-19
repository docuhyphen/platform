package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.service.auth.AuthAuditService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationExchangePolicyGroupTest
{
    private val senderOrganizationId = UUID.randomUUID()
    private val targetOrganizationId = UUID.randomUUID()
    private val organizationService = mock<OrganizationService>()
    private val trustExchangePolicyService = mock<OrganizationTrustExchangePolicyService>()
    private val service = OrganizationExchangePolicyService(
        organizationService = organizationService,
        organizationMembershipService = mock<OrganizationMembershipService>(),
        organizationTrustExchangePolicyService = trustExchangePolicyService,
        authAuditService = mock<AuthAuditService>(),
    )

    @Test
    fun `allows an active published group from a trusted organization`()
    {
        val group = externalGroup()
        configureSenderSettings(requireTrust = true)
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(targetOrganizationId),
                any(),
            ),
        )
            .thenReturn(true)

        assertDoesNotThrow {
            service.assertCanShareWithGroup(senderOrganizationId, UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects an unpublished external organization group`()
    {
        val group = externalGroup().apply { externallyPublished = false }

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(senderOrganizationId, UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects an inactive external organization group`()
    {
        val group = externalGroup().apply { isActive = false }

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(senderOrganizationId, UUID.randomUUID(), group)
        }
    }

    @Test
    fun `rejects a published group from an untrusted organization`()
    {
        val group = externalGroup()
        configureSenderSettings(requireTrust = true)
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(targetOrganizationId),
                any(),
            ),
        )
            .thenReturn(false)

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithGroup(senderOrganizationId, UUID.randomUUID(), group)
        }
    }

    @Test
    fun `allows a published external group when the B2B trust requirement is disabled`()
    {
        val group = externalGroup()
        configureSenderSettings(requireTrust = false)

        assertDoesNotThrow {
            service.assertCanShareWithGroup(senderOrganizationId, UUID.randomUUID(), group)
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
            service.assertCanShareWithGroup(senderOrganizationId, initiatorId, group)
        }
    }

    private fun externalGroup() = PrincipalGroup().apply {
        scope = PrincipalGroupScope.ORG
        ownerOrganizationId = targetOrganizationId
        externallyPublished = true
        isActive = true
    }

    private fun configureSenderSettings(requireTrust: Boolean)
    {
        whenever(organizationService.getOrganizationById(senderOrganizationId)).thenReturn(
            Organization().apply {
                id = senderOrganizationId
                name = "Sender"
                registrationNumber = "sender"
                settings = OrganizationSettings().apply {
                    requireTrustedOrganizationForB2b = requireTrust
                }
            },
        )
    }
}
