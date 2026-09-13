package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.communication.templates.RenderedEmailTemplate
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService.PreparedPersonResolution
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.*
import java.util.*

class ExchangeAccessManagementServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val callerId = UUID.randomUUID()
    private val activeOrganizationId = UUID.randomUUID()

    private val exchangeRepository = mock<ExchangeRepository>()
    private val shareRepository = mock<ShareRepository>()
    private val shareService = mock<ShareService>()
    private val shareQueryService = mock<ShareQueryService>()
    private val appUserService = mock<AppUserService>()
    private val externalParticipantRepository = mock<ExternalParticipantRepository>()
    private val organizationGroupService = mock<OrganizationGroupService>()
    private val exchangeRecipientService = mock<ExchangeRecipientService>()
    private val selectionResolver = mock<ExchangeRecipientSelectionResolver>()
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val identityResolutionService = mock<ExternalIdentityResolutionService>()
    private val authTokenContext = mock<AuthTokenContext>()
    private val authorizationService = mock<AuthorizationService>()
    private val authorizationContextFactory = mock<AuthorizationContextFactory>()
    private val organizationExchangePolicyService = mock<OrganizationExchangePolicyService>()
    private val notificationDeliveryService = mock<ExchangeNotificationDeliveryService>()
    private val emailTemplateService = mock<EmailTemplateService>()
    private val otpService = mock<OtpService>()
    private val noAuthExchangeAccessTokenService = mock<NoAuthExchangeAccessTokenService>()
    private val configurationService = mock<ConfigurationService>()
    private val auditRecorder = mock<AuditRecorder>()
    private val auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
        whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
    }

    private val service = ExchangeAccessManagementService(
        exchangeRepository,
        shareRepository,
        shareService,
        shareQueryService,
        appUserService,
        externalParticipantRepository,
        organizationGroupService,
        exchangeRecipientService,
        selectionResolver,
        attestationService,
        identityResolutionService,
        authTokenContext,
        authorizationService,
        authorizationContextFactory,
        organizationExchangePolicyService,
        emailTemplateService,
        otpService,
        noAuthExchangeAccessTokenService,
        configurationService,
        auditRecorder,
        auditOwnerScopeResolver,
        notificationDeliveryService,
        mock<ExchangeFeatureSubscriptionGuard>(),
    )

    private fun grantOwnerAuthorization(activeOrgId: UUID? = activeOrganizationId)
    {
        val authToken = mock<AuthToken>()
        val caller = mock<AppUser>()
        whenever(caller.id).thenReturn(callerId)
        whenever(caller.email).thenReturn("caller@example.test")
        whenever(authToken.appUser).thenReturn(caller)
        whenever(authTokenContext.authToken).thenReturn(authToken)
        whenever(authTokenContext.activeOrganizationId).thenReturn(activeOrgId)
        whenever(authorizationContextFactory.currentPrincipal())
            .thenReturn(PrincipalRef(PrincipalKind.USER, callerId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(mock())
        whenever(authorizationService.authorize(any(), eq(Action.EXCHANGE_MANAGE_ACCESS), any(), any()))
            .thenReturn(Decision.Allow())
        whenever(configurationService.emailSubjectTitle).thenReturn("DocuHyphen")
        whenever(
            emailTemplateService.renderExchangeCreatedRecipientEmail(
                any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(),
            ),
        ).thenReturn("email body")
    }

    private fun draftExchange(ownerOrganizationId: UUID? = activeOrganizationId) = Exchange().apply {
        status = ExchangeStatus.INITIATED
        requireRecipientSignIn = false
        name = "Recovery Exchange"
        this.ownerOrganizationId = ownerOrganizationId
    }

    private fun pendingPrimary(shareId: UUID, selectionType: ExchangeRecipientSelectionType) =
        ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = shareId
            purpose = ExchangeRecipientPurpose.PRIMARY
            this.selectionType = selectionType
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }

    private fun directShare(kind: PrincipalKind, principalId: UUID) = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = kind
        this.principalId = principalId
        roleName = ExchangeShareRoleName.VIEWER.name
        source = ShareSource.DIRECT
        status = ShareStatus.PENDING_APPROVAL
    }

    @Test
    fun `replaces a pending trusted-person primary with a fresh pending invitation`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        val oldShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        val currentPrimary = pendingPrimary(oldShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(currentPrimary)
        whenever(shareService.getById(oldShare.id)).thenReturn(oldShare)

        val resolvedUser = mock<AppUser>()
        val resolvedUserId = UUID.randomUUID()
        val targetOrganizationId = UUID.randomUUID()
        whenever(resolvedUser.id).thenReturn(resolvedUserId)
        val resolutionEntity = mock<ExternalIdentityResolution>()
        val resolutionId = UUID.randomUUID()
        whenever(resolutionEntity.id).thenReturn(resolutionId)
        whenever(resolutionEntity.targetOrganizationId).thenReturn(targetOrganizationId)
        val prepared = PreparedPersonResolution(resolutionEntity, resolvedUser, mock(), mock())
        val resolved = ResolvedExchangeRecipientSelection(
            recipientType = ExchangeRecipientType.APP_USER,
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
            appUser = resolvedUser,
            targetOrganizationId = targetOrganizationId,
            preparedPersonResolution = prepared,
        )
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(resolved)

        val newShare = directShare(PrincipalKind.USER, resolvedUserId)
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(newShare)
        val newPrimary = pendingPrimary(newShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON)
        whenever(
            exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()),
        ).thenReturn(newPrimary)

        val selection = TrustedPersonRecipientSelectionRequest(resolutionId = resolutionId.toString())
        service.replacePrimaryRecipient(exchangeId, selection)

        verify(exchangeRecipientService).deleteBinding(currentPrimary)
        verify(shareService).revoke(eq(oldShare.id), eq(callerId), eq(session.name))

        val statusCaptor = argumentCaptor<ShareStatus>()
        verify(shareService).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(),
            statusCaptor.capture(), anyOrNull(),
        )
        assertEquals(ShareStatus.PENDING_APPROVAL, statusCaptor.firstValue)

        val purposeCaptor = argumentCaptor<ExchangeRecipientPurpose>()
        val acceptanceCaptor = argumentCaptor<ExchangeRecipientAcceptanceStatus>()
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId), eq(newShare), purposeCaptor.capture(), any(), anyOrNull(), acceptanceCaptor.capture(),
        )
        assertEquals(ExchangeRecipientPurpose.PRIMARY, purposeCaptor.firstValue)
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, acceptanceCaptor.firstValue)

        verify(identityResolutionService).consumeForExchange(
            resolutionId = eq(resolutionId),
            actorAppUserId = eq(callerId),
            callerOrganizationId = eq(activeOrganizationId),
            targetOrganizationId = eq(targetOrganizationId),
            exchangeId = eq(exchangeId),
            now = any(),
        )
        verify(attestationService).createPersonAttestation(eq(newPrimary), eq(prepared), any())
        // Trusted selections force recipient sign-in.
        assertEquals(true, session.requireRecipientSignIn)
        verify(exchangeRepository).update(session)
    }

    @Test
    fun `rejects replacement when the Exchange is not a draft`()
    {
        grantOwnerAuthorization()
        val session = draftExchange().apply { status = ExchangeStatus.ACCEPTED_STARTED }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)

        assertThrows(IllegalArgumentException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(resolutionId = UUID.randomUUID().toString()),
            )
        }
        verify(exchangeRecipientService, never()).deleteBinding(any())
    }

    @Test
    fun `rejects an external-email selection as a replaced primary recipient`()
    {
        grantOwnerAuthorization()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())

        val unsupportedSelections = listOf<ExchangeRecipientSelectionRequest>(
            ExternalEmailRecipientSelectionRequest(
                email = "person@acme.example",
                firstName = "A",
                lastName = "B",
            ),
            RegisteredUserRecipientSelectionRequest(appUserId = UUID.randomUUID().toString()),
            InternalGroupRecipientSelectionRequest(groupId = UUID.randomUUID().toString()),
            PersonalGroupRecipientSelectionRequest(groupId = UUID.randomUUID().toString()),
        )

        assertAll(
            unsupportedSelections.map { selection ->
                {
                    assertThrows(IllegalArgumentException::class.java) {
                        service.replacePrimaryRecipient(exchangeId, selection)
                    }
                }
            },
        )
        verify(selectionResolver, never()).resolve(any(), any(), anyOrNull())
        verify(exchangeRecipientService, never()).findPrimary(any())
        verify(exchangeRecipientService, never()).deleteBinding(any())
        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
    }

    @Test
    fun `replaces a pending trusted-group primary with a pending Share and recipient decision`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        val oldShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        val currentPrimary = pendingPrimary(oldShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(currentPrimary)
        whenever(shareService.getById(oldShare.id)).thenReturn(oldShare)

        val group = mock<PrincipalGroup>()
        val groupId = UUID.randomUUID()
        val targetOrganizationId = UUID.randomUUID()
        whenever(group.id).thenReturn(groupId)
        val validation = mock<com.docuhyphen.app.api.service.organization.TrustedGroupValidation>()
        val resolved = ResolvedExchangeRecipientSelection(
            recipientType = ExchangeRecipientType.GROUP,
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
            group = group,
            targetOrganizationId = targetOrganizationId,
            trustedGroupValidation = validation,
        )
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(resolved)

        val newShare = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val previousParticipantBinding = ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = newShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.REJECTED
        }
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(newShare)
        whenever(exchangeRecipientService.findByDirectShareId(newShare.id)).thenReturn(previousParticipantBinding)
        val newPrimary = pendingPrimary(newShare.id, ExchangeRecipientSelectionType.TRUSTED_GROUP)
        whenever(
            exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()),
        ).thenReturn(newPrimary)

        service.replacePrimaryRecipient(
            exchangeId,
            TrustedGroupRecipientSelectionRequest(
                organizationId = targetOrganizationId.toString(),
                groupId = groupId.toString(),
            ),
        )

        verify(shareService).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(),
            eq(ShareStatus.PENDING_APPROVAL), anyOrNull(),
        )
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId),
            eq(newShare),
            eq(ExchangeRecipientPurpose.PRIMARY),
            eq(ExchangeRecipientSelectionType.TRUSTED_GROUP),
            eq(targetOrganizationId),
            eq(ExchangeRecipientAcceptanceStatus.PENDING),
        )
        verify(exchangeRecipientService).deleteBinding(previousParticipantBinding)
        verify(attestationService).createGroupAttestation(eq(newPrimary), eq(validation), any())
    }

    @Test
    fun `resends a no-auth primary recipient invitation with a fresh link and code`()
    {
        grantOwnerAuthorization()
        val recipientId = UUID.randomUUID()
        val recipient = AppUser().apply {
            id = recipientId
            email = "recipient@example.test"
            isTemporary = true
        }
        val initiator = AppUser().apply {
            id = callerId
            email = "owner@example.test"
        }
        val exchange = draftExchange().apply {
            this.initiator = initiator
            initialShareMessage = "Please upload these documents."
            noAuthAccessValidityDays = 3
            documents = mutableListOf(
                Document().apply {
                    title = "Passport"
                    isDeleted = false
                },
            )
        }
        val primaryShare = directShare(PrincipalKind.USER, recipientId).apply {
            status = ShareStatus.ACTIVE
        }
        val primaryRecipient = pendingPrimary(primaryShare.id, ExchangeRecipientSelectionType.EXTERNAL_EMAIL)

        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(primaryRecipient)
        whenever(shareService.getById(primaryShare.id)).thenReturn(primaryShare)
        whenever(appUserService.getById(recipientId)).thenReturn(recipient)
        whenever(otpService.generateEmailOtp()).thenReturn("123456")
        whenever(otpService.hashOtp("123456")).thenReturn("hashed-code")
        whenever(noAuthExchangeAccessTokenService.issue(exchange)).thenReturn("fresh-token")
        whenever(
            emailTemplateService.renderExchangeCreatedNoAuthRecipientEmail(
                any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), any(),
            ),
        ).thenReturn(RenderedEmailTemplate(subject = "Document request", body = "Email body"))

        service.resendNoAuthPrimaryRecipientInvitation(exchangeId)

        assertEquals("hashed-code", exchange.recipientOtpHash)
        verify(exchangeRepository).update(exchange)
        verify(noAuthExchangeAccessTokenService).issue(exchange)
        verify(emailTemplateService).renderExchangeCreatedNoAuthRecipientEmail(
            exchangeId = eq(exchangeId.toString()),
            name = eq(exchange.name.orEmpty()),
            initiatorName = eq("owner@example.test"),
            initiatorOrganization = isNull(),
            sessionMessage = eq(exchange.initialShareMessage),
            documents = eq(listOf("Passport")),
            otp = eq("123456"),
            accessToken = eq("fresh-token"),
            expiryLabel = eq("3 days"),
        )
        val emailCaptor = argumentCaptor<List<ExchangeEmailDelivery>>()
        verify(notificationDeliveryService).scheduleAfterCommit(
            eq(exchangeId),
            eq(exchange.status.name),
            emailCaptor.capture(),
            any(),
            any(),
        )
        assertEquals("recipient@example.test", emailCaptor.firstValue.single().to)
        assertEquals("Document request", emailCaptor.firstValue.single().subject)
    }

    @Test
    fun `resend no-auth invitation rejects sign-in required Exchanges`()
    {
        grantOwnerAuthorization()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(
            draftExchange().apply { requireRecipientSignIn = true },
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.resendNoAuthPrimaryRecipientInvitation(exchangeId)
        }

        verify(exchangeRecipientService, never()).findPrimary(any())
        verify(notificationDeliveryService, never()).scheduleAfterCommit(any(), any(), any(), any(), any())
    }

    @Test
    fun `rejects a resolver selection-type substitution before replacing the current primary`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        val oldShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        val currentPrimary = pendingPrimary(oldShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(currentPrimary)
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
                appUser = AppUser().apply { id = UUID.randomUUID() },
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
            )
        }
        verify(exchangeRecipientService, never()).deleteBinding(any())
        verify(shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `consumption failure propagates after writes so the replacement transaction rolls back`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        val oldShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        val currentPrimary = pendingPrimary(oldShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(currentPrimary)
        whenever(shareService.getById(oldShare.id)).thenReturn(oldShare)

        val resolvedUser = AppUser().apply { id = UUID.randomUUID() }
        val targetOrganizationId = UUID.randomUUID()
        val resolutionId = UUID.randomUUID()
        val resolutionEntity = mock<ExternalIdentityResolution>()
        whenever(resolutionEntity.id).thenReturn(resolutionId)
        whenever(resolutionEntity.targetOrganizationId).thenReturn(targetOrganizationId)
        val prepared = PreparedPersonResolution(resolutionEntity, resolvedUser, mock(), mock())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = resolvedUser,
                targetOrganizationId = targetOrganizationId,
                preparedPersonResolution = prepared,
            ),
        )

        val newShare = directShare(PrincipalKind.USER, resolvedUser.id)
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(newShare)
        whenever(
            exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()),
        ).thenReturn(pendingPrimary(newShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON))
        doThrow(IllegalStateException("resolution consumption failed"))
            .whenever(identityResolutionService)
            .consumeForExchange(
                resolutionId = eq(resolutionId),
                actorAppUserId = eq(callerId),
                callerOrganizationId = eq(activeOrganizationId),
                targetOrganizationId = eq(targetOrganizationId),
                exchangeId = eq(exchangeId),
                now = any(),
            )

        assertThrows(IllegalStateException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(resolutionId.toString()),
            )
        }

        verify(exchangeRecipientService).deleteBinding(currentPrimary)
        verify(shareService).revoke(eq(oldShare.id), eq(callerId), eq(session.name))
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId), eq(newShare), eq(ExchangeRecipientPurpose.PRIMARY),
            eq(ExchangeRecipientSelectionType.TRUSTED_PERSON), eq(targetOrganizationId),
            eq(ExchangeRecipientAcceptanceStatus.PENDING),
        )
        verify(attestationService, never()).createPersonAttestation(any(), any(), any())
        verify(shareQueryService, never()).getSessionAccessView(exchangeId)
        verify(notificationDeliveryService, never()).scheduleAfterCommit(any(), any(), any(), any(), any())
    }

    @Test
    fun `authorization denial occurs before replacement state is read or mutated`()
    {
        val principal = PrincipalRef(PrincipalKind.USER, callerId)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(mock())
        whenever(authorizationService.authorize(any(), eq(Action.EXCHANGE_MANAGE_ACCESS), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied"))
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())

        assertThrows(ForbiddenException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
            )
        }
        verify(exchangeRecipientService, never()).findPrimary(any())
        verify(selectionResolver, never()).resolve(any(), any(), anyOrNull())
        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
    }

    @Test
    fun `denied EXCHANGE_MANAGE_ACCESS records AUTHORIZATION_DENIED under the Exchange's real owner, not Platform`()
    {
        val ownerUserId = UUID.randomUUID()
        val principal = PrincipalRef(PrincipalKind.USER, callerId)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(mock())
        whenever(authorizationService.authorize(any(), eq(Action.EXCHANGE_MANAGE_ACCESS), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied"))
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())
        whenever(auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchangeId))
            .thenReturn(AuditOwnerScope.Personal(ownerUserId))

        assertThrows(ExchangeNotFoundException::class.java) {
            service.getSessionAccessView(exchangeId)
        }

        val draftCaptor = argumentCaptor<com.docuhyphen.app.api.service.audit.AuditEventDraft>()
        verify(auditRecorder).record(draftCaptor.capture())
        assertEquals(AuditOwnerScope.Personal(ownerUserId), draftCaptor.firstValue.owner)
    }

    @Test
    fun `rejects replacement when the current primary is not pending`()
    {
        grantOwnerAuthorization()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())
        val oldShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        val acceptedPrimary = pendingPrimary(oldShare.id, ExchangeRecipientSelectionType.TRUSTED_PERSON).apply {
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
        }
        whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(acceptedPrimary)

        assertThrows(IllegalArgumentException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(resolutionId = UUID.randomUUID().toString()),
            )
        }
        verify(exchangeRecipientService, never()).deleteBinding(any())
    }

    @Test
    fun `trusted person participant remains pending until its independent acceptance`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        val resolvedUser = AppUser().apply {
            id = UUID.randomUUID()
            email = "trusted.member@example.test"
        }
        val targetOrganizationId = UUID.randomUUID()
        val resolutionId = UUID.randomUUID()
        val resolutionEntity = mock<ExternalIdentityResolution>()
        whenever(resolutionEntity.id).thenReturn(resolutionId)
        whenever(resolutionEntity.targetOrganizationId).thenReturn(targetOrganizationId)
        val prepared = PreparedPersonResolution(resolutionEntity, resolvedUser, mock(), mock())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = resolvedUser,
                targetOrganizationId = targetOrganizationId,
                preparedPersonResolution = prepared,
            ),
        )
        val pendingShare = directShare(PrincipalKind.USER, resolvedUser.id)
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(pendingShare)
        val participant = ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = pendingShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
        whenever(exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(participant)
        whenever(shareQueryService.getSessionAccessView(exchangeId)).thenReturn(emptyList())
        whenever(
            emailTemplateService.renderExchangeCreatedRecipientEmail(
                exchangeId.toString(),
                session.name.orEmpty(),
                "caller@example.test",
                null,
                "You have a trusted invitation that remains inactive until you accept.",
                emptyList(),
                true,
                "Document Exchange invitation",
                "You have been invited to access a Document Exchange from caller@example.test.",
            ),
        ).thenReturn("email body")

        service.inviteTrustedParticipant(
            exchangeId = exchangeId,
            selection = TrustedPersonRecipientSelectionRequest(resolutionId.toString()),
            roleName = ExchangeShareRoleName.VIEWER,
        )

        verify(shareService).grant(
            eq(ResourceType.EXCHANGE),
            eq(exchangeId),
            eq(PrincipalKind.USER),
            eq(resolvedUser.id),
            eq(ExchangeShareRoleName.VIEWER),
            eq(callerId),
            eq(ShareSource.DIRECT),
            anyOrNull(),
            anyOrNull(),
            eq(ShareStatus.PENDING_APPROVAL),
            eq(session.name),
        )
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId),
            eq(pendingShare),
            eq(ExchangeRecipientPurpose.PARTICIPANT),
            eq(ExchangeRecipientSelectionType.TRUSTED_PERSON),
            eq(targetOrganizationId),
            eq(ExchangeRecipientAcceptanceStatus.PENDING),
        )
        verify(identityResolutionService).consumeForExchange(
            resolutionId = eq(resolutionId),
            actorAppUserId = eq(callerId),
            callerOrganizationId = eq(activeOrganizationId),
            targetOrganizationId = eq(targetOrganizationId),
            exchangeId = eq(exchangeId),
            now = any(),
        )
        verify(attestationService).createPersonAttestation(eq(participant), eq(prepared), any())
    }

    @Test
    fun `trusted participant authorization denial occurs before resolution or writes`()
    {
        val principal = PrincipalRef(PrincipalKind.USER, callerId)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(mock())
        whenever(authorizationService.authorize(any(), eq(Action.EXCHANGE_MANAGE_ACCESS), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied"))
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())

        assertThrows(ForbiddenException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verify(selectionResolver, never()).resolve(any(), any(), anyOrNull())
        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
    }

    @Test
    fun `reinviting a revoked trusted participant replaces the old binding and attestation`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        val resolvedUser = mock<AppUser>()
        val resolvedUserId = UUID.randomUUID()
        val targetOrganizationId = UUID.randomUUID()
        whenever(resolvedUser.id).thenReturn(resolvedUserId)
        val resolutionEntity = mock<ExternalIdentityResolution>()
        val resolutionId = UUID.randomUUID()
        whenever(resolutionEntity.id).thenReturn(resolutionId)
        whenever(resolutionEntity.targetOrganizationId).thenReturn(targetOrganizationId)
        val prepared = PreparedPersonResolution(resolutionEntity, resolvedUser, mock(), mock())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = resolvedUser,
                targetOrganizationId = targetOrganizationId,
                preparedPersonResolution = prepared,
            ),
        )
        val revokedShare = directShare(PrincipalKind.USER, resolvedUserId).apply {
            status = ShareStatus.REVOKED
        }
        val previousRecipient = ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = revokedShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
        whenever(
            shareService.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                resolvedUserId,
                ResourceType.EXCHANGE,
                exchangeId,
            ),
        ).thenReturn(revokedShare)
        whenever(exchangeRecipientService.findByDirectShareId(revokedShare.id)).thenReturn(previousRecipient)
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(revokedShare)
        val replacementRecipient = ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = revokedShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
        whenever(exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(replacementRecipient)
        whenever(shareQueryService.getSessionAccessView(exchangeId)).thenReturn(emptyList())

        service.inviteTrustedParticipant(
            exchangeId,
            TrustedPersonRecipientSelectionRequest(resolutionId.toString()),
            ExchangeShareRoleName.VIEWER,
        )

        verify(exchangeRecipientService).deleteBinding(previousRecipient)
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId),
            eq(revokedShare),
            eq(ExchangeRecipientPurpose.PARTICIPANT),
            eq(ExchangeRecipientSelectionType.TRUSTED_PERSON),
            eq(targetOrganizationId),
            eq(ExchangeRecipientAcceptanceStatus.PENDING),
        )
        verify(attestationService).createPersonAttestation(eq(replacementRecipient), eq(prepared), any())
    }

    @Test
    fun `active organization mismatch denies a trusted participant before resolution or writes`()
    {
        val ownerOrganizationId = UUID.randomUUID()
        grantOwnerAuthorization(activeOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange(ownerOrganizationId))

        assertThrows(ForbiddenException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verifyNoInteractions(
            selectionResolver,
            shareService,
            exchangeRecipientService,
            attestationService,
            identityResolutionService,
            organizationExchangePolicyService,
            notificationDeliveryService,
            auditRecorder,
        )
    }

    @Test
    fun `active organization mismatch denies primary replacement before recipient state or writes`()
    {
        val ownerOrganizationId = UUID.randomUUID()
        grantOwnerAuthorization(activeOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange(ownerOrganizationId))

        assertThrows(ForbiddenException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedGroupRecipientSelectionRequest(
                    organizationId = UUID.randomUUID().toString(),
                    groupId = UUID.randomUUID().toString(),
                ),
            )
        }

        verifyNoInteractions(
            selectionResolver,
            shareService,
            exchangeRecipientService,
            attestationService,
            identityResolutionService,
            organizationExchangePolicyService,
            notificationDeliveryService,
            auditRecorder,
        )
    }

    @Test
    fun `active organization mismatch denies an ordinary B2B grant before policy or writes`()
    {
        val ownerOrganizationId = UUID.randomUUID()
        grantOwnerAuthorization(activeOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange(ownerOrganizationId))

        assertThrows(ForbiddenException::class.java) {
            service.grantAccess(
                exchangeId = exchangeId,
                principalKind = PrincipalKind.USER.name,
                principalId = UUID.randomUUID().toString(),
                roleName = ExchangeShareRoleName.VIEWER,
            )
        }

        verifyNoInteractions(
            appUserService,
            externalParticipantRepository,
            organizationExchangePolicyService,
            shareService,
            exchangeRecipientService,
            notificationDeliveryService,
            auditRecorder,
        )
    }

    @Test
    fun `granting access to an unknown user email creates a temporary user invitation`()
    {
        grantOwnerAuthorization(activeOrgId = null)
        val session = draftExchange(ownerOrganizationId = null).apply { id = exchangeId }
        val recipientEmail = "new.person@example.test"
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(appUserService.getAppUserByEmail(recipientEmail)).thenReturn(null)

        val createdUserCaptor = argumentCaptor<AppUser>()
        whenever(appUserService.create(createdUserCaptor.capture())).thenAnswer { invocation -> invocation.getArgument(0) }
        whenever(appUserService.getById(any())).thenAnswer { createdUserCaptor.firstValue }
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            directShare(
                invocation.getArgument<PrincipalKind>(2),
                invocation.getArgument<UUID>(3),
            ).apply { status = ShareStatus.ACTIVE }
        }

        service.grantAccess(
            exchangeId = exchangeId,
            principalKind = PrincipalKind.USER.name,
            principalId = recipientEmail,
            roleName = ExchangeShareRoleName.VIEWER,
        )

        val createdUser = createdUserCaptor.firstValue
        assertEquals(recipientEmail, createdUser.email)
        assertEquals(false, createdUser.isActive)
        assertEquals(true, createdUser.isTemporary)
        verify(organizationExchangePolicyService).assertCanShareWithUser(null, callerId, null)
        verify(shareService).grant(
            eq(ResourceType.EXCHANGE),
            eq(exchangeId),
            eq(PrincipalKind.USER),
            eq(createdUser.id),
            eq(ExchangeShareRoleName.VIEWER),
            eq(callerId),
            eq(ShareSource.DIRECT),
            anyOrNull(),
            anyOrNull(),
            eq(ShareStatus.ACTIVE),
            eq(session.name),
        )
        verify(exchangeRecipientService).createBinding(
            eq(exchangeId),
            any(),
            eq(ExchangeRecipientPurpose.PARTICIPANT),
            eq(ExchangeRecipientSelectionType.EXTERNAL_EMAIL),
            anyOrNull(),
            eq(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED),
        )

        val emailDeliveries = argumentCaptor<List<ExchangeEmailDelivery>>()
        verify(notificationDeliveryService).scheduleAfterCommit(
            eq(exchangeId),
            eq(session.status.name),
            emailDeliveries.capture(),
            any(),
            any(),
        )
        val email = emailDeliveries.firstValue.single()
        assertEquals(recipientEmail, email.to)
        assertEquals("Document Exchange invitation", email.subject)
        assertEquals(null, email.preferenceAppUserId)
        verify(emailTemplateService).renderExchangeCreatedRecipientEmail(
            exchangeId.toString(),
            session.name.orEmpty(),
            "caller@example.test",
            null,
            "Create your account to access this Exchange.",
            emptyList(),
            true,
            "Document Exchange invitation",
            "You have been invited to access a Document Exchange from caller@example.test.",
        )
    }

    @Test
    fun `personal Exchange rejects a trusted participant even when an organization is active`()
    {
        grantOwnerAuthorization(activeOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange(ownerOrganizationId = null))

        assertThrows(IllegalArgumentException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verifyNoInteractions(selectionResolver, shareService, exchangeRecipientService, attestationService)
    }

    @Test
    fun `personal Exchange rejects trusted primary replacement before recipient state`()
    {
        grantOwnerAuthorization(activeOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange(ownerOrganizationId = null))

        assertThrows(IllegalArgumentException::class.java) {
            service.replacePrimaryRecipient(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
            )
        }

        verifyNoInteractions(selectionResolver, shareService, exchangeRecipientService, attestationService)
    }

    @Test
    fun `trusted participant rejects resolver type tampering before writes`()
    {
        grantOwnerAuthorization()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
                appUser = AppUser().apply { id = UUID.randomUUID() },
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
        verify(exchangeRecipientService, never()).createBinding(any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `stale trusted participant resolution leaves no partial state`()
    {
        grantOwnerAuthorization()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(draftExchange())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId)))
            .thenThrow(IllegalArgumentException("Trusted relationship is stale"))

        assertThrows(IllegalArgumentException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
        verify(exchangeRecipientService, never()).createBinding(any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `trusted participant consumption failure propagates for transaction rollback`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        val resolvedUser = AppUser().apply {
            id = UUID.randomUUID()
            email = "trusted.member@example.test"
        }
        val targetOrganizationId = UUID.randomUUID()
        val resolutionId = UUID.randomUUID()
        val resolutionEntity = mock<ExternalIdentityResolution>()
        whenever(resolutionEntity.id).thenReturn(resolutionId)
        whenever(resolutionEntity.targetOrganizationId).thenReturn(targetOrganizationId)
        val prepared = PreparedPersonResolution(resolutionEntity, resolvedUser, mock(), mock())
        whenever(selectionResolver.resolve(any(), any(), eq(activeOrganizationId))).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = resolvedUser,
                targetOrganizationId = targetOrganizationId,
                preparedPersonResolution = prepared,
            ),
        )
        val pendingShare = directShare(PrincipalKind.USER, resolvedUser.id)
        whenever(
            shareService.grant(
                any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
            ),
        ).thenReturn(pendingShare)
        val participant = ExchangeRecipient().apply {
            exchangeId = this@ExchangeAccessManagementServiceTest.exchangeId
            directShareId = pendingShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
        whenever(exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(participant)
        doThrow(IllegalStateException("resolution consumption failed"))
            .whenever(identityResolutionService)
            .consumeForExchange(
                resolutionId = eq(resolutionId),
                actorAppUserId = eq(callerId),
                callerOrganizationId = eq(activeOrganizationId),
                targetOrganizationId = eq(targetOrganizationId),
                exchangeId = eq(exchangeId),
                now = any(),
            )

        assertThrows(IllegalStateException::class.java) {
            service.inviteTrustedParticipant(
                exchangeId,
                TrustedPersonRecipientSelectionRequest(resolutionId.toString()),
                ExchangeShareRoleName.VIEWER,
            )
        }

        verify(exchangeRecipientService).createBinding(
            eq(exchangeId),
            eq(pendingShare),
            eq(ExchangeRecipientPurpose.PARTICIPANT),
            eq(ExchangeRecipientSelectionType.TRUSTED_PERSON),
            eq(targetOrganizationId),
            eq(ExchangeRecipientAcceptanceStatus.PENDING),
        )
        verify(attestationService, never()).createPersonAttestation(any(), any(), any())
        verify(shareQueryService, never()).getSessionAccessView(exchangeId)
        verify(notificationDeliveryService, never()).scheduleAfterCommit(any(), any(), any(), any(), any())
    }

    @Test
    fun `directional policy denial leaves manage-access grant without partial state`()
    {
        grantOwnerAuthorization()
        val session = draftExchange()
        val recipientId = UUID.randomUUID()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(session)
        whenever(appUserService.getById(recipientId)).thenReturn(AppUser().apply { id = recipientId })
        doThrow(IllegalArgumentException("B2B policy denied"))
            .whenever(organizationExchangePolicyService)
            .assertCanShareWithUser(activeOrganizationId, callerId, recipientId)

        assertThrows(IllegalArgumentException::class.java) {
            service.grantAccess(
                exchangeId = exchangeId,
                principalKind = PrincipalKind.USER.name,
                principalId = recipientId.toString(),
                roleName = ExchangeShareRoleName.VIEWER,
            )
        }

        verify(shareService, never()).grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )
        verify(exchangeRecipientService, never()).createBinding(any(), any(), any(), any(), anyOrNull(), any())
    }
}
