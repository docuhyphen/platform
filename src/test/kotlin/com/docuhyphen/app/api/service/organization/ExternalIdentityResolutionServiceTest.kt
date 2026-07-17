package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.ExternalIdentityResolutionUnavailableException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.ExternalIdentityResolutionDtoTransformer
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.repository.ExternalIdentityResolutionRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class ExternalIdentityResolutionServiceTest
{
    private val repository = mock<ExternalIdentityResolutionRepository>()
    private val validationService = mock<TrustedRecipientValidationService>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val appUserService = mock<AppUserService>()
    private val guardService = mock<ExternalIdentityLookupGuardService>()
    private val authorizationService = mock<AuthorizationService>()
    private val contextFactory = mock<AuthorizationContextFactory>()
    private val tokenContext = AuthTokenContext()
    private val auditRecorder = mock<AuditRecorder>()
    private val configService = OrganizationTrustConfigService(14, 7, 365, 10, 24)
    private val service = ExternalIdentityResolutionService(
        repository,
        validationService,
        membershipService,
        appUserService,
        guardService,
        configService,
        ExternalIdentityResolutionDtoTransformer(),
        authorizationService,
        contextFactory,
        tokenContext,
        auditRecorder,
    )
    private val actorId = UUID.randomUUID()
    private val callerOrganizationId = UUID.randomUUID()
    private val targetOrganizationId = UUID.randomUUID()
    private val relationshipId = UUID.randomUUID()
    private val membershipId = UUID.randomUUID()
    private val resolvedUserId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-16T11:00:00Z")

    @Test
    fun `exact normalized match returns permitted profile and persists a short lived binding`()
    {
        configureCaller()
        val validation = validation(shareDisplayName = true)
        whenever(validationService.relationshipIdForLookup(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationshipId)
        whenever(validationService.validatePersonResolution(callerOrganizationId, targetOrganizationId, now))
            .thenReturn(validation)
        val membership = membership()
        whenever(membershipService.findActiveMembershipsByOrganizationAndExactEmail(targetOrganizationId, "member@example.test"))
            .thenReturn(listOf(membership))
        whenever(appUserService.getById(resolvedUserId)).thenReturn(appUser())
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as ExternalIdentityResolution }

        val result = service.resolve(targetOrganizationId, "  MEMBER@EXAMPLE.TEST ", now)

        assertEquals("member@example.test", result.email)
        assertEquals("Ada Lovelace", result.displayName)
        assertEquals(Timestamp.from(now.plusSeconds(600)), result.expiresAt)
        verify(guardService).enforce(
            actorId,
            callerOrganizationId,
            targetOrganizationId,
            relationshipId,
            "member@example.test",
            null,
        )
        val saved = argumentCaptor<ExternalIdentityResolution>()
        verify(repository).save(saved.capture())
        assertEquals(actorId, saved.firstValue.actorAppUserId)
        assertEquals(membershipId, saved.firstValue.resolvedMembershipId)
        assertEquals(2, saved.firstValue.senderPolicyRevision)
        assertEquals(4, saved.firstValue.targetPolicyRevision)
    }

    @Test
    fun `target policy can withhold display name without withholding exact membership result`()
    {
        configureCaller()
        whenever(validationService.relationshipIdForLookup(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationshipId)
        whenever(validationService.validatePersonResolution(callerOrganizationId, targetOrganizationId, now))
            .thenReturn(validation(shareDisplayName = false))
        whenever(membershipService.findActiveMembershipsByOrganizationAndExactEmail(targetOrganizationId, "member@example.test"))
            .thenReturn(listOf(membership()))
        whenever(appUserService.getById(resolvedUserId)).thenReturn(appUser())
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as ExternalIdentityResolution }

        val result = service.resolve(targetOrganizationId, "member@example.test", now)

        assertNull(result.displayName)
    }

    @Test
    fun `missing or inactive target membership has one generic denied result`()
    {
        configureCaller()
        whenever(validationService.relationshipIdForLookup(callerOrganizationId, targetOrganizationId))
            .thenReturn(relationshipId)
        whenever(validationService.validatePersonResolution(callerOrganizationId, targetOrganizationId, now))
            .thenReturn(validation())
        whenever(membershipService.findActiveMembershipsByOrganizationAndExactEmail(targetOrganizationId, "missing@example.test"))
            .thenReturn(emptyList())

        val exception = assertThrows<ExternalIdentityResolutionUnavailableException> {
            service.resolve(targetOrganizationId, "missing@example.test", now)
        }

        assertEquals("Trusted member could not be verified", exception.message)
    }

    @Test
    fun `partial email input cannot perform a directory lookup`()
    {
        configureCaller()

        assertThrows<OrganizationTrustValidationException> {
            service.resolve(targetOrganizationId, "member", now)
        }

        verify(membershipService, never()).findActiveMembershipsByOrganizationAndExactEmail(any(), any())
    }

    @Test
    fun `expired resolution and replay are rejected under row lock`()
    {
        val expired = resolution().apply { expiresAt = Timestamp.from(now.minusSeconds(1)) }
        whenever(repository.findForUpdate(expired.id)).thenReturn(expired)
        assertThrows<ExternalIdentityResolutionUnavailableException> {
            service.consumeForExchange(
                expired.id,
                actorId,
                callerOrganizationId,
                targetOrganizationId,
                UUID.randomUUID(),
                now,
            )
        }

        val consumed = resolution().apply {
            consumedAt = Timestamp.from(now.minusSeconds(1))
            consumedByExchangeId = UUID.randomUUID()
        }
        whenever(repository.findForUpdate(consumed.id)).thenReturn(consumed)
        assertThrows<ExternalIdentityResolutionUnavailableException> {
            service.consumeForExchange(
                consumed.id,
                actorId,
                callerOrganizationId,
                targetOrganizationId,
                UUID.randomUUID(),
                now,
            )
        }
    }

    @Test
    fun `wrong actor caller organization or target cannot consume a resolution`()
    {
        val resolution = resolution()
        whenever(repository.findForUpdate(resolution.id)).thenReturn(resolution)
        listOf(
            Triple(UUID.randomUUID(), callerOrganizationId, targetOrganizationId),
            Triple(actorId, UUID.randomUUID(), targetOrganizationId),
            Triple(actorId, callerOrganizationId, UUID.randomUUID()),
        ).forEach { (actor, caller, target) ->
            assertThrows<ExternalIdentityResolutionUnavailableException> {
                service.consumeForExchange(resolution.id, actor, caller, target, UUID.randomUUID(), now)
            }
        }
    }

    @Test
    fun `valid consumption binds the resolution to exactly one Exchange`()
    {
        val resolution = resolution()
        val exchangeId = UUID.randomUUID()
        whenever(repository.findForUpdate(resolution.id)).thenReturn(resolution)
        whenever(repository.update(any())).thenAnswer { it.arguments[0] as ExternalIdentityResolution }

        val consumed = service.consumeForExchange(
            resolution.id,
            actorId,
            callerOrganizationId,
            targetOrganizationId,
            exchangeId,
            now,
        )

        assertEquals(exchangeId, consumed.consumedByExchangeId)
        assertEquals(Timestamp.from(now), consumed.consumedAt)
    }

    @Test
    fun `cleanup removes only resolutions beyond the configured retention period`()
    {
        val expired = resolution()
        whenever(repository.findExpiredBefore(now.minusSeconds(24 * 60 * 60))).thenReturn(listOf(expired))

        val removed = service.cleanupExpired(now)

        assertEquals(1, removed)
        verify(repository).deleteAll(listOf(expired))
    }

    private fun configureCaller()
    {
        tokenContext.activeOrganizationId = callerOrganizationId
        whenever(contextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(actorId))
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = callerOrganizationId))
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
    }

    private fun validation(shareDisplayName: Boolean = true): TrustedExchangePolicyValidation
    {
        val relationship = OrganizationTrustRelationship().apply { id = relationshipId }
        val senderPolicy = OrganizationTrustPartyPolicy().apply { revision = 2 }
        val targetPolicy = OrganizationTrustPartyPolicy().apply {
            revision = 4
            shareMemberDisplayName = shareDisplayName
        }
        return TrustedExchangePolicyValidation(
            relationship,
            organization(callerOrganizationId, "Caller"),
            organization(targetOrganizationId, "Target Organization"),
            senderPolicy,
            targetPolicy,
            now.plusSeconds(3600),
        )
    }

    private fun membership(): OrganizationMembership = OrganizationMembership().apply {
        id = membershipId
        appUserId = resolvedUserId
        organizationId = targetOrganizationId
    }

    private fun appUser(): AppUser = AppUser().apply {
        id = resolvedUserId
        email = "member@example.test"
        person = Person().apply {
            firstName = "Ada"
            lastName = "Lovelace"
        }
    }

    private fun resolution(): ExternalIdentityResolution = ExternalIdentityResolution().apply {
        actorAppUserId = actorId
        callerOrganizationId = this@ExternalIdentityResolutionServiceTest.callerOrganizationId
        targetOrganizationId = this@ExternalIdentityResolutionServiceTest.targetOrganizationId
        relationshipId = this@ExternalIdentityResolutionServiceTest.relationshipId
        resolvedAppUserId = resolvedUserId
        resolvedMembershipId = membershipId
        normalizedEmail = "member@example.test"
        createdAt = Timestamp.from(now.minusSeconds(60))
        expiresAt = Timestamp.from(now.plusSeconds(60))
    }

    private fun organization(id: UUID, value: String): Organization = Organization().apply {
        this.id = id
        name = value
        registrationNumber = id.toString()
        isActive = true
        verificationComplete = true
    }
}
