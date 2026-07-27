package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientSelectionResolver
import com.docuhyphen.app.api.service.exchange.ResolvedExchangeRecipientSelection
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedExchangePolicyValidation
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

internal class ExchangeRecipientSelectionProbe
{
    val initiator: AppUser = AppUser().apply {
        id = UUID.randomUUID()
        isActive = true
    }
    val callerOrganizationId: UUID = UUID.randomUUID()
    val targetOrganizationId: UUID = UUID.randomUUID()
    val appUserService: AppUserService = mock()
    val groupService: OrganizationGroupService = mock()
    val policyService: OrganizationExchangePolicyService = mock()
    val validationService: TrustedRecipientValidationService = mock()
    val resolutionService: ExternalIdentityResolutionService = mock()
    val auditService: TrustedRecipientAuditService = mock()
    private val resolver = ExchangeRecipientSelectionResolver(
        appUserService,
        groupService,
        policyService,
        validationService,
        resolutionService,
        auditService,
    )

    fun resolve(
        request: ExchangeRecipientSelectionRequest,
        activeOrganizationId: UUID? = callerOrganizationId,
    ): ResolvedExchangeRecipientSelection = resolver.resolve(request, initiator, activeOrganizationId)

    fun appUser(active: Boolean = true, deprovisioned: Boolean = false): AppUser = AppUser().apply {
        id = UUID.randomUUID()
        isActive = active
        if (deprovisioned)
        {
            deprovisionedAt = java.sql.Timestamp.from(Instant.now())
        }
    }

    fun denyUserPolicy(userId: UUID?)
    {
        doThrow(IllegalArgumentException("Sharing policy denied"))
            .whenever(policyService)
            .assertCanShareWithUser(callerOrganizationId, initiator.id, userId)
    }

    fun denyGroupPolicy(group: PrincipalGroup)
    {
        doThrow(IllegalArgumentException("Sharing policy denied"))
            .whenever(policyService)
            .assertCanShareWithGroup(callerOrganizationId, initiator.id, group)
    }

    fun registerUser(user: AppUser)
    {
        whenever(appUserService.getById(user.id)).thenReturn(user)
    }

    fun registerEmail(email: String, user: AppUser?)
    {
        whenever(appUserService.getAppUserByEmail(email)).thenReturn(user)
    }

    fun registerGroup(group: PrincipalGroup)
    {
        whenever(groupService.getById(group.id.toString())).thenReturn(group)
    }

    fun registerTrustedGroup(group: PrincipalGroup, eligible: Boolean = true)
    {
        if (!eligible)
        {
            whenever(
                validationService.validateGroupSelection(
                    eq(callerOrganizationId),
                    eq(targetOrganizationId),
                    eq(group.id),
                    any(),
                ),
            ).thenThrow(IllegalArgumentException("Trusted group is unavailable"))
            return
        }
        val targetOrganization = Organization().apply { id = targetOrganizationId }
        val validation = TrustedGroupValidation(
            relationship = mock<OrganizationTrustRelationship>(),
            callerOrganization = Organization().apply { id = callerOrganizationId },
            targetOrganization = targetOrganization,
            senderPolicy = mock<OrganizationTrustPartyPolicy>(),
            targetPolicy = mock<OrganizationTrustPartyPolicy>(),
            verificationExpiresAt = Instant.now().plusSeconds(3600),
            group = group,
        )
        whenever(
            validationService.validateGroupSelection(
                eq(callerOrganizationId),
                eq(targetOrganizationId),
                eq(group.id),
                any(),
            ),
        ).thenReturn(validation)
    }

    fun registerTrustedPerson(resolutionId: UUID, user: AppUser, eligible: Boolean = true)
    {
        if (!eligible)
        {
            whenever(
                resolutionService.prepareForInitiation(
                    eq(resolutionId),
                    eq(initiator.id),
                    eq(callerOrganizationId),
                    any(),
                ),
            ).thenThrow(IllegalArgumentException("Trusted member is unavailable"))
            return
        }
        val resolution = ExternalIdentityResolution().apply {
            id = resolutionId
            targetOrganizationId = this@ExchangeRecipientSelectionProbe.targetOrganizationId
        }
        val prepared = ExternalIdentityResolutionService.PreparedPersonResolution(
            resolution = resolution,
            appUser = user,
            membership = mock<OrganizationMembership>(),
            validation = mock<TrustedExchangePolicyValidation>(),
        )
        whenever(
            resolutionService.prepareForInitiation(
                eq(resolutionId),
                eq(initiator.id),
                eq(callerOrganizationId),
                any(),
            ),
        ).thenReturn(prepared)
    }
}
