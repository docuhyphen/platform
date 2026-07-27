package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.ExchangeRepositoryPostgreSQLResource
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.service.notification.UserNotificationPreferenceService
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

@QuarkusTest
@QuarkusTestResource(ExchangeRepositoryPostgreSQLResource::class, restrictToAnnotatedClass = true)
class ExchangeNotificationTransactionIntegrationTest
{
    enum class AudienceCase(
        val selectionType: ExchangeRecipientSelectionType,
        val purpose: ExchangeRecipientPurpose,
    )
    {
        TRUSTED_PERSON_PRIMARY(ExchangeRecipientSelectionType.TRUSTED_PERSON, ExchangeRecipientPurpose.PRIMARY),
        TRUSTED_GROUP_PRIMARY(ExchangeRecipientSelectionType.TRUSTED_GROUP, ExchangeRecipientPurpose.PRIMARY),
        TRUSTED_PERSON_PARTICIPANT(
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            ExchangeRecipientPurpose.PARTICIPANT,
        ),
        TRUSTED_GROUP_PARTICIPANT(
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
            ExchangeRecipientPurpose.PARTICIPANT,
        ),
    }

    enum class FailureStage
    {
        RECIPIENT_BINDING,
        RESOLUTION_CONSUMPTION,
        ATTESTATION,
        FINAL_TRANSACTIONAL,
    }

    @Inject
    lateinit var service: ExchangeAccessManagementService

    private lateinit var exchangeRepository: ExchangeRepository
    private lateinit var shareService: ShareService
    private lateinit var shareQueryService: ShareQueryService
    private lateinit var appUserService: AppUserService
    private lateinit var organizationGroupService: OrganizationGroupService
    private lateinit var exchangeRecipientService: ExchangeRecipientService
    private lateinit var selectionResolver: ExchangeRecipientSelectionResolver
    private lateinit var attestationService: ExchangeRecipientAttestationService
    private lateinit var identityResolutionService: ExternalIdentityResolutionService
    private lateinit var authTokenContext: AuthTokenContext
    private lateinit var authorizationService: DefaultAuthorizationService
    private lateinit var authorizationContextFactory: AuthorizationContextFactory
    private lateinit var emailTemplateService: EmailTemplateService
    private lateinit var configurationService: ConfigurationService
    private lateinit var emailService: EmailService
    private lateinit var inAppNotificationService: InAppNotificationService
    private lateinit var preferenceService: UserNotificationPreferenceService
    private lateinit var realtimeEventService: RealtimeEventService

    private lateinit var exchange: Exchange
    private lateinit var caller: AppUser
    private lateinit var recipient: AppUser
    private lateinit var group: PrincipalGroup
    private lateinit var preparedResolution: ExternalIdentityResolutionService.PreparedPersonResolution
    private lateinit var groupValidation: TrustedGroupValidation
    private lateinit var pendingShare: Share
    private lateinit var recipientBinding: ExchangeRecipient
    private lateinit var currentPrimary: ExchangeRecipient
    private lateinit var currentPrimaryShare: Share

    private val exchangeId = UUID.fromString("71000000-0000-0000-0000-000000000001")
    private val callerId = UUID.fromString("72000000-0000-0000-0000-000000000001")
    private val callerOrganizationId = UUID.fromString("73000000-0000-0000-0000-000000000001")
    private val targetOrganizationId = UUID.fromString("73000000-0000-0000-0000-000000000002")
    private val recipientId = UUID.fromString("74000000-0000-0000-0000-000000000001")
    private val groupId = UUID.fromString("75000000-0000-0000-0000-000000000001")
    private val resolutionId = UUID.fromString("76000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp()
    {
        installFreshMocks()
        createFixtures()
        stubCommonBehavior()
    }

    @ParameterizedTest
    @EnumSource(AudienceCase::class)
    fun `committed trusted invitation delivers each audience exactly once`(audience: AudienceCase)
    {
        val selection = stubSelection(audience.selectionType)
        stubSuccessfulMutation(audience)

        invokeMutation(audience.purpose, selection)

        verify(emailService, times(1)).sendEmail(
            eq(recipient.email),
            any(),
            eq("email body"),
            eq(true),
        )
        verify(inAppNotificationService, times(1)).publishAfterCommitIfEnabled(
            eq(recipientId),
            eq(UserNotificationPreference.EXCHANGE_INITIATED),
            eq("exchange.recipient_invitation"),
            eq("Trusted participant invitation"),
            any(),
            eq(mapOf("exchangeId" to exchangeId.toString())),
        )
        verify(realtimeEventService, times(1)).broadcastToUser(eq(recipientId), any(), anyOrNull())
        verify(preferenceService, times(1)).isEnabled(
            recipientId,
            UserNotificationPreference.EXCHANGE_INITIATED,
            NotificationChannelType.EMAIL,
        )
        verifyNoMoreInteractions(emailService, inAppNotificationService, preferenceService, realtimeEventService)
    }

    @ParameterizedTest
    @EnumSource(FailureStage::class)
    fun `rolled back trusted invitation delivers no channel after transactional failure`(stage: FailureStage)
    {
        val selectionType = if (stage == FailureStage.ATTESTATION)
            ExchangeRecipientSelectionType.TRUSTED_GROUP
        else
            ExchangeRecipientSelectionType.TRUSTED_PERSON
        val selection = stubSelection(selectionType)
        val audience = AudienceCase.entries.first {
            it.selectionType == selectionType && it.purpose == ExchangeRecipientPurpose.PARTICIPANT
        }
        stubSuccessfulMutation(audience)
        stubFailure(stage)

        assertThrows(IllegalStateException::class.java) {
            invokeMutation(ExchangeRecipientPurpose.PARTICIPANT, selection)
        }

        verifyFailureWasReached(stage)
        verifyNoInteractions(emailService, inAppNotificationService, preferenceService, realtimeEventService)
    }

    private fun invokeMutation(
        purpose: ExchangeRecipientPurpose,
        selection: ExchangeRecipientSelectionRequest,
    )
    {
        if (purpose == ExchangeRecipientPurpose.PRIMARY)
        {
            service.replacePrimaryRecipient(exchangeId, selection)
        }
        else
        {
            service.inviteTrustedParticipant(
                exchangeId = exchangeId,
                selection = selection,
                roleName = ExchangeShareRoleName.VIEWER,
            )
        }
    }

    private fun stubSelection(selectionType: ExchangeRecipientSelectionType): ExchangeRecipientSelectionRequest
    {
        val resolved = if (selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON)
        {
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = selectionType,
                appUser = recipient,
                targetOrganizationId = targetOrganizationId,
                preparedPersonResolution = preparedResolution,
            )
        }
        else
        {
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.GROUP,
                selectionType = selectionType,
                group = group,
                targetOrganizationId = targetOrganizationId,
                trustedGroupValidation = groupValidation,
            )
        }
        whenever(selectionResolver.resolve(any(), eq(caller), eq(callerOrganizationId))).thenReturn(resolved)
        return if (selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON)
        {
            TrustedPersonRecipientSelectionRequest(resolutionId.toString())
        }
        else
        {
            TrustedGroupRecipientSelectionRequest(targetOrganizationId.toString(), groupId.toString())
        }
    }

    private fun stubSuccessfulMutation(audience: AudienceCase)
    {
        pendingShare.principalKind = if (audience.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP)
            PrincipalKind.PRINCIPAL_GROUP
        else
            PrincipalKind.USER
        pendingShare.principalId = if (audience.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP)
            groupId
        else
            recipientId
        recipientBinding.purpose = audience.purpose
        recipientBinding.selectionType = audience.selectionType
        whenever(shareService.grant(
            any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
        )).thenReturn(pendingShare)
        whenever(exchangeRecipientService.createBinding(any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(recipientBinding)
        whenever(shareQueryService.getSessionAccessView(exchangeId)).thenReturn(emptyList())
        if (audience.purpose == ExchangeRecipientPurpose.PRIMARY)
        {
            whenever(exchangeRecipientService.findPrimary(exchangeId)).thenReturn(currentPrimary)
            whenever(shareService.getById(currentPrimaryShare.id)).thenReturn(currentPrimaryShare)
            whenever(exchangeRecipientService.findByDirectShareId(pendingShare.id)).thenReturn(null)
        }
    }

    private fun stubFailure(stage: FailureStage)
    {
        when (stage)
        {
            FailureStage.RECIPIENT_BINDING ->
                doThrow(IllegalStateException("recipient binding failed"))
                    .whenever(exchangeRecipientService)
                    .createBinding(any(), any(), any(), any(), anyOrNull(), any())

            FailureStage.RESOLUTION_CONSUMPTION ->
                doThrow(IllegalStateException("resolution consumption failed"))
                    .whenever(identityResolutionService)
                    .consumeForExchange(any(), any(), any(), any(), any(), any())

            FailureStage.ATTESTATION ->
                doThrow(IllegalStateException("attestation failed"))
                    .whenever(attestationService)
                    .createGroupAttestation(any(), any(), any())

            FailureStage.FINAL_TRANSACTIONAL ->
                whenever(shareQueryService.getSessionAccessView(exchangeId))
                    .thenThrow(IllegalStateException("final transactional read failed"))
        }
    }

    private fun verifyFailureWasReached(stage: FailureStage)
    {
        when (stage)
        {
            FailureStage.RECIPIENT_BINDING ->
                verify(exchangeRecipientService).createBinding(any(), any(), any(), any(), anyOrNull(), any())

            FailureStage.RESOLUTION_CONSUMPTION ->
                verify(identityResolutionService).consumeForExchange(any(), any(), any(), any(), any(), any())

            FailureStage.ATTESTATION ->
                verify(attestationService).createGroupAttestation(any(), any(), any())

            FailureStage.FINAL_TRANSACTIONAL ->
            {
                verify(shareQueryService).getSessionAccessView(exchangeId)
                verify(attestationService).createPersonAttestation(any(), any(), any())
            }
        }
    }

    private fun installFreshMocks()
    {
        exchangeRepository = installMock()
        shareService = installMock()
        shareQueryService = installMock()
        appUserService = installMock()
        organizationGroupService = installMock()
        exchangeRecipientService = installMock()
        selectionResolver = installMock()
        attestationService = installMock()
        identityResolutionService = installMock()
        authTokenContext = installMock()
        authorizationService = installMock()
        authorizationContextFactory = installMock()
        emailTemplateService = installMock()
        configurationService = installMock()
        emailService = installMock()
        inAppNotificationService = installMock()
        preferenceService = installMock()
        realtimeEventService = installMock()
    }

    private inline fun <reified T : Any> installMock(): T
    {
        val mock = mock<T>()
        QuarkusMock.installMockForType(mock, T::class.java)
        return mock
    }

    private fun createFixtures()
    {
        exchange = Exchange().apply {
            id = exchangeId
            name = "Transaction boundary Exchange"
            status = ExchangeStatus.INITIATED
            ownerOrganizationId = callerOrganizationId
            requireRecipientSignIn = true
        }
        caller = AppUser().apply {
            id = callerId
            email = "caller@example.test"
        }
        recipient = AppUser().apply {
            id = recipientId
            email = "trusted-recipient@example.test"
        }
        group = PrincipalGroup().apply {
            id = groupId
            name = "Trusted decision group"
            ownerOrganizationId = targetOrganizationId
        }
        val resolution = mock<ExternalIdentityResolution>()
        whenever(resolution.id).thenReturn(resolutionId)
        whenever(resolution.targetOrganizationId).thenReturn(targetOrganizationId)
        preparedResolution = ExternalIdentityResolutionService.PreparedPersonResolution(
            resolution,
            recipient,
            mock(),
            mock(),
        )
        groupValidation = mock()
        pendingShare = Share().apply {
            resourceType = ResourceType.EXCHANGE
            resourceId = exchangeId
            principalKind = PrincipalKind.USER
            principalId = recipientId
            roleName = ExchangeShareRoleName.VIEWER
            source = ShareSource.DIRECT
            status = ShareStatus.PENDING_APPROVAL
        }
        recipientBinding = ExchangeRecipient().apply {
            this.exchangeId = this@ExchangeNotificationTransactionIntegrationTest.exchangeId
            directShareId = pendingShare.id
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            targetOrganizationId = this@ExchangeNotificationTransactionIntegrationTest.targetOrganizationId
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
        currentPrimaryShare = Share().apply {
            resourceType = ResourceType.EXCHANGE
            resourceId = exchangeId
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
            roleName = ExchangeShareRoleName.VIEWER
            source = ShareSource.DIRECT
            status = ShareStatus.PENDING_APPROVAL
        }
        currentPrimary = ExchangeRecipient().apply {
            this.exchangeId = this@ExchangeNotificationTransactionIntegrationTest.exchangeId
            directShareId = currentPrimaryShare.id
            purpose = ExchangeRecipientPurpose.PRIMARY
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
        }
    }

    private fun stubCommonBehavior()
    {
        val authToken = mock<AuthToken>()
        whenever(authToken.appUser).thenReturn(caller)
        whenever(authTokenContext.authToken).thenReturn(authToken)
        whenever(authTokenContext.activeOrganizationId).thenReturn(callerOrganizationId)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(authorizationContextFactory.currentPrincipal())
            .thenReturn(PrincipalRef(PrincipalKind.USER, callerId))
        whenever(authorizationContextFactory.currentContext()).thenReturn(mock())
        whenever(
            authorizationService.authorize(
                any(),
                eq(Action.EXCHANGE_MANAGE_ACCESS),
                any(),
                any(),
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            emailTemplateService.renderExchangeCreatedRecipientEmail(
                any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(),
            ),
        ).thenReturn("email body")
        whenever(configurationService.emailSubjectTitle).thenReturn("DocuHyphen")
        whenever(appUserService.getById(recipientId)).thenReturn(recipient)
        whenever(organizationGroupService.activeOwnerOrManagerUserIds(groupId)).thenReturn(setOf(recipientId))
        whenever(
            preferenceService.isEnabled(
                recipientId,
                UserNotificationPreference.EXCHANGE_INITIATED,
                NotificationChannelType.EMAIL,
            ),
        ).thenReturn(true)
    }
}
