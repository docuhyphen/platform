package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.service.auth.AuthAuditService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationExchangePolicyUserTest
{
    private val senderOrganizationId = UUID.randomUUID()
    private val receiverOrganizationId = UUID.randomUUID()
    private val secondReceiverOrganizationId = UUID.randomUUID()
    private val initiatorAppUserId = UUID.randomUUID()
    private val recipientAppUserId = UUID.randomUUID()
    private val organizationService = mock<OrganizationService>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val trustExchangePolicyService = mock<OrganizationTrustExchangePolicyService>()
    private val service = OrganizationExchangePolicyService(
        organizationService,
        membershipService,
        trustExchangePolicyService,
        mock<AuthAuditService>(),
    )

    @Test
    fun `allows B2B user only when one current recipient organization permits the Exchange`()
    {
        configureSender(requireTrust = true)
        whenever(membershipService.activeOrganizationIds(recipientAppUserId))
            .thenReturn(setOf(receiverOrganizationId, secondReceiverOrganizationId))
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(receiverOrganizationId),
                any(),
            ),
        )
            .thenReturn(false)
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(secondReceiverOrganizationId),
                any(),
            ),
        )
            .thenReturn(true)

        assertDoesNotThrow {
            service.assertCanShareWithUser(senderOrganizationId, initiatorAppUserId, recipientAppUserId)
        }
    }

    @Test
    fun `rejects B2B user when every current recipient organization denies the Exchange`()
    {
        configureSender(requireTrust = true)
        whenever(membershipService.activeOrganizationIds(recipientAppUserId))
            .thenReturn(setOf(receiverOrganizationId, secondReceiverOrganizationId))
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(receiverOrganizationId),
                any(),
            ),
        )
            .thenReturn(false)
        whenever(
            trustExchangePolicyService.permitsExchange(
                eq(senderOrganizationId),
                eq(secondReceiverOrganizationId),
                any(),
            ),
        )
            .thenReturn(false)

        assertThrows(IllegalArgumentException::class.java) {
            service.assertCanShareWithUser(senderOrganizationId, initiatorAppUserId, recipientAppUserId)
        }
    }

    @Test
    fun `B2B requirement opt-out permits a member without evaluating trust policy`()
    {
        configureSender(requireTrust = false)
        whenever(membershipService.activeOrganizationIds(recipientAppUserId))
            .thenReturn(setOf(receiverOrganizationId))

        assertDoesNotThrow {
            service.assertCanShareWithUser(senderOrganizationId, initiatorAppUserId, recipientAppUserId)
        }
        verifyNoInteractions(trustExchangePolicyService)
    }

    @Test
    fun `internal membership cannot be substituted into a B2B denial`()
    {
        configureSender(requireTrust = true)
        whenever(membershipService.activeOrganizationIds(recipientAppUserId))
            .thenReturn(setOf(senderOrganizationId, receiverOrganizationId))

        assertDoesNotThrow {
            service.assertCanShareWithUser(senderOrganizationId, initiatorAppUserId, recipientAppUserId)
        }
        verifyNoInteractions(trustExchangePolicyService)
    }

    private fun configureSender(requireTrust: Boolean)
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
