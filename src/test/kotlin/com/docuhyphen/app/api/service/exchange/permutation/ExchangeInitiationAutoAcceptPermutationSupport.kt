package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.ExchangeRequestDocumentRequest
import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.exchange.ExchangeInitiationService
import com.docuhyphen.app.api.service.exchange.ExchangeNotificationDeliveryService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientAttestationService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientSelectionResolver
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.NoAuthExchangeAccessTokenService
import com.docuhyphen.app.api.service.exchange.ResolvedExchangeRecipientSelection
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.variable.InterpolationResult
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
import jakarta.persistence.EntityManager
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class ExchangeInitiationAutoAcceptFixture
{
    val organizationId: UUID = UUID.randomUUID()
    val initiator: AppUser = appUser("initiator@example.test")
    val recipient: AppUser = appUser("recipient@example.test")
    val exchangeRepository: ExchangeRepository = mock()
    val recipientService: ExchangeRecipientService = mock()
    val workflowEngineService: WorkflowEngineService = mock()
    val emailTemplateService: EmailTemplateService = mock()
    val recipientSelectionResolver: ExchangeRecipientSelectionResolver = mock()
    val service: ExchangeInitiationService

    init
    {
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = initiator }
            activeOrganizationId = organizationId
        }
        val organization = Organization().apply {
            id = organizationId
            name = "Auto-accept organization"
            registrationNumber = "AUTO-ACCEPT"
            settings = OrganizationSettings().apply {
                requireRecipientAcceptance = false
            }
        }
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        val authorizationService = mock<AuthorizationService>()
        val appUserService = mock<AppUserService>()
        val organizationService = mock<OrganizationService>()
        val shareService = mock<ShareService>()
        val templateInterpolator = mock<TemplateVariableInterpolator>()
        val configurationService = mock<ConfigurationService>()
        val entityManager = mock<EntityManager>()
        val principal = PrincipalRef.user(initiator.id)

        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(organizationService.getOrganizationById(organizationId)).thenReturn(organization)
        whenever(appUserService.getById(recipient.id)).thenReturn(recipient)
        whenever(
            recipientSelectionResolver.resolve(
                RegisteredUserRecipientSelectionRequest(recipient.id.toString()),
                initiator,
                organizationId,
            ),
        ).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = com.docuhyphen.app.api.model.entity.ExchangeRecipientType.APP_USER,
                selectionType = com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType.REGISTERED_USER,
                appUser = recipient,
            ),
        )
        whenever(templateInterpolator.interpolateWithSequences(eq("Auto-start Exchange"), any()))
            .thenReturn(InterpolationResult("Auto-start Exchange"))
        whenever(templateInterpolator.interpolate(any(), any())).thenAnswer {
            InterpolationResult(it.getArgument(0))
        }
        whenever(entityManager.merge(any<AppUser>())).thenAnswer { it.getArgument(0) }
        whenever(exchangeRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(exchangeRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(workflowEngineService.trigger(any())).thenReturn(null)
        whenever(
            shareService.grant(
                any(),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            Share().apply {
                resourceType = invocation.getArgument(0)
                resourceId = invocation.getArgument(1)
                principalKind = invocation.getArgument(2)
                principalId = invocation.getArgument(3)
                roleName = invocation.getArgument(4)
                source = invocation.getArgument(6)
                status = invocation.getArgument(9)
            }
        }
        whenever(recipientService.createBinding(any(), any(), any(), any(), any(), any()))
            .thenReturn(ExchangeRecipient())
        whenever(configurationService.emailSubjectTitle).thenReturn("DocuHyphen")
        whenever(emailTemplateService.renderExchangeCreatedRecipientEmail(
            any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(),
        )).thenReturn("Recipient message")
        whenever(emailTemplateService.renderExchangeCreatedInitiatorEmail(
            any(), any(), any(), any(),
        )).thenReturn("Initiator message")

        service = ExchangeInitiationService(
            exchangeRepository = exchangeRepository,
            appUserRepository = mock<AppUserRepository>(),
            appUserService = appUserService,
            emailTemplateService = emailTemplateService,
            otpService = mock<OtpService>(),
            authTokenContext = authTokenContext,
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            authenticationService = mock<AuthenticationService>(),
            authRateLimitService = mock<AuthRateLimitService>(),
            configurationService = configurationService,
            authAuditService = mock<AuthAuditService>(),
            shareService = shareService,
            exchangeRecipientService = recipientService,
            exchangeRecipientAttestationService = mock<ExchangeRecipientAttestationService>(),
            externalIdentityResolutionService = mock<ExternalIdentityResolutionService>(),
            exchangeRecipientSelectionResolver = recipientSelectionResolver,
            organizationGroupService = mock<OrganizationGroupService>(),
            workflowEngineService = workflowEngineService,
            organizationService = organizationService,
            templateVariableInterpolator = templateInterpolator,
            documentLibraryService = mock<DocumentLibraryService>(),
            fileStorageService = mock<FileStorageService>(),
            schemaAssignmentService = mock<SchemaAssignmentService>(),
            noAuthExchangeAccessTokenService = mock<NoAuthExchangeAccessTokenService>(),
            exchangeNotificationDeliveryService = mock<ExchangeNotificationDeliveryService>(),
        )
        ExchangeInitiationService::class.java.getDeclaredField("entityManager").apply {
            isAccessible = true
            set(service, entityManager)
        }
    }

    fun initiate(
        primaryRecipient: ExchangeRecipientSelectionRequest =
            RegisteredUserRecipientSelectionRequest(recipient.id.toString()),
        requestRecipientSignIn: Boolean = false,
    ): Exchange
    {
        if (primaryRecipient is ExternalEmailRecipientSelectionRequest)
        {
            whenever(
                recipientSelectionResolver.resolve(
                    primaryRecipient,
                    initiator,
                    organizationId,
                ),
            ).thenReturn(
                ResolvedExchangeRecipientSelection(
                    recipientType = com.docuhyphen.app.api.model.entity.ExchangeRecipientType.EMAIL,
                    selectionType = com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType.EXTERNAL_EMAIL,
                    appUser = recipient,
                ),
            )
        }
        val document = ExchangeRequestDocumentRequest().apply {
            title = "Requested document"
            required = true
        }
        return service.initiateExchange(
            ExchangeInitiationDto(
                name = "Auto-start Exchange",
                primaryRecipient = primaryRecipient,
                requestRecipientSignIn = requestRecipientSignIn,
                exchangeDocuments = listOf(document),
            ),
        )
    }

    private fun appUser(emailAddress: String): AppUser = AppUser().apply {
        id = UUID.randomUUID()
        email = emailAddress
        isActive = true
    }
}
