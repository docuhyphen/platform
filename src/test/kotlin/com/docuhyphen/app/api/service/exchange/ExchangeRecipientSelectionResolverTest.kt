package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedExchangePolicyValidation
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeRecipientSelectionResolverTest
{
    private val appUserService = mock<AppUserService>()
    private val organizationGroupService = mock<OrganizationGroupService>()
    private val organizationExchangePolicyService = mock<OrganizationExchangePolicyService>()
    private val validationService = mock<TrustedRecipientValidationService>()
    private val resolutionService = mock<ExternalIdentityResolutionService>()
    private val auditService = mock<TrustedRecipientAuditService>()
    private val resolver = ExchangeRecipientSelectionResolver(
        appUserService,
        organizationGroupService,
        organizationExchangePolicyService,
        validationService,
        resolutionService,
        auditService,
    )
    private val initiator = AppUser().apply { id = UUID.randomUUID() }
    private val callerOrganizationId = UUID.randomUUID()
    private val targetOrganizationId = UUID.randomUUID()

    @Test
    fun `trusted group validation records allowed audit evidence`()
    {
        val groupId = UUID.randomUUID()
        val group = PrincipalGroup().apply { id = groupId }
        val targetOrganization = Organization().apply { id = targetOrganizationId }
        val validation = mock<TrustedGroupValidation>()
        whenever(validation.group).thenReturn(group)
        whenever(validation.targetOrganization).thenReturn(targetOrganization)
        whenever(
            validationService.validateGroupSelection(
                eq(callerOrganizationId),
                eq(targetOrganizationId),
                eq(groupId),
                any(),
            ),
        ).thenReturn(validation)

        val resolved = resolver.resolve(
            TrustedGroupRecipientSelectionRequest(targetOrganizationId.toString(), groupId.toString()),
            initiator,
            callerOrganizationId,
        )

        assertEquals(groupId, resolved.group?.id)
        verify(auditService).recordValidationAllowed(
            initiator.id,
            callerOrganizationId,
            targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            groupId,
        )
    }

    @Test
    fun `trusted group validation denial records denied audit evidence`()
    {
        val groupId = UUID.randomUUID()
        whenever(
            validationService.validateGroupSelection(
                eq(callerOrganizationId),
                eq(targetOrganizationId),
                eq(groupId),
                any(),
            ),
        ).thenThrow(OrganizationTrustNotFoundException("Unavailable"))

        assertThrows<OrganizationTrustNotFoundException> {
            resolver.resolve(
                TrustedGroupRecipientSelectionRequest(targetOrganizationId.toString(), groupId.toString()),
                initiator,
                callerOrganizationId,
            )
        }

        verify(auditService).recordValidationDenied(
            initiator.id,
            callerOrganizationId,
            targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            groupId,
        )
        verify(auditService, never()).recordValidationAllowed(any(), any(), any(), any(), any())
    }

    @Test
    fun `trusted person validation records the resolved account without the resolution id`()
    {
        val resolutionId = UUID.randomUUID()
        val resolvedAppUser = AppUser().apply { id = UUID.randomUUID() }
        val resolution = ExternalIdentityResolution().apply {
            id = resolutionId
            targetOrganizationId = this@ExchangeRecipientSelectionResolverTest.targetOrganizationId
        }
        val prepared = ExternalIdentityResolutionService.PreparedPersonResolution(
            resolution,
            resolvedAppUser,
            mock<OrganizationMembership>(),
            mock<TrustedExchangePolicyValidation>(),
        )
        whenever(
            resolutionService.prepareForInitiation(
                eq(resolutionId),
                eq(initiator.id),
                eq(callerOrganizationId),
                any(),
            ),
        ).thenReturn(prepared)

        resolver.resolve(
            TrustedPersonRecipientSelectionRequest(resolutionId.toString()),
            initiator,
            callerOrganizationId,
        )

        verify(auditService).recordValidationAllowed(
            initiator.id,
            callerOrganizationId,
            targetOrganizationId,
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            resolvedAppUser.id,
        )
    }
}
