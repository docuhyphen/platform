package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementConfigService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestAdHocCreationServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(actorId), AuthorizationContext(activeOrgId = organizationId))
    private val configuration = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "requested-records",
                title = "Requested records",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = "record-confirmation",
                        requirementType = InformationRequestRequirementType.RESPONSE_ATTESTATION,
                        prompt = "Confirm the current records are ready for review",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `ad hoc creation stores one private Template request pair and replays the command result`()
    {
        val fixture = fixture()
        val command = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "Current record request",
            description = "Collect current process records",
            configuration = configuration,
            gatesExchangeClosure = false,
            access = access,
            idempotencyKey = "create-current-record-request",
        )

        val first = fixture.service.createAdHoc(command)
        val second = fixture.service.createAdHoc(command)

        assertEquals(first.request.id, second.request.id)
        assertEquals(first.requestETag, second.requestETag)
        assertEquals(1, first.requirementCount)
        assertEquals(1, fixture.savedDefinitions.size)
        assertEquals(1, fixture.savedVersions.size)
        assertEquals(1, fixture.savedRequests.size)
        assertEquals(1, fixture.savedCapabilities.size)

        val definition = fixture.savedDefinitions.single()
        val version = fixture.savedVersions.single()
        val request = fixture.savedRequests.single()
        assertEquals(InformationRequestTemplateOriginKind.AD_HOC_REQUEST, definition.originKind)
        assertEquals(request.id, definition.originRequestId)
        assertEquals(InformationRequestTemplateScopeKind.ORGANIZATION, definition.scopeKind)
        assertEquals(organizationId, definition.scopeOrgId)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, definition.status)
        assertEquals(InformationRequestTemplateStatus.PUBLISHED, version.status)
        assertEquals(actorId, version.publishedByAppUserId)
        assertNotNull(version.publishedAt)
        assertEquals(version.id, request.templateVersionId)
        assertEquals(exchangeId, request.exchangeId)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, request.ownerType)
        assertEquals(organizationId, request.ownerOrganizationId)
        assertEquals(actorId, request.createdByAppUserId)
        assertEquals(false, request.gatesExchangeClosure)

        verify(fixture.authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        verify(fixture.configurationWriter).replaceConfiguration(version, configuration)
        val materializerAccess = argumentCaptor<FieldsAccessContext>()
        verify(fixture.materializer).materialize(org.mockito.kotlin.eq(request), materializerAccess.capture())
        assertEquals(access.principal, materializerAccess.firstValue.principal)
        assertEquals(access.authorization, materializerAccess.firstValue.authorization)
        verify(fixture.definitionRepository, times(1)).save(any())
        verify(fixture.requestRepository, times(1)).save(any())
    }

    @Test
    fun `creation is denied and nothing persisted when the entitlement guard refuses it`()
    {
        val deniedGuard = mock<InformationRequestEntitlementGuard>()
        whenever(deniedGuard.requireRequestMutation(any())).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "This capability is not yet released.",
                ),
            ),
        )
        val fixture = fixture(entitlementGuard = deniedGuard)
        val command = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "Current record request",
            configuration = configuration,
            access = access,
            idempotencyKey = "create-denied-by-entitlement-guard",
        )

        assertThrows<SubscriptionDenialException> { fixture.service.createAdHoc(command) }

        verify(deniedGuard).requireRequestMutation(any())
        verifyNoInteractions(fixture.authorizationService)
        verify(fixture.requestRepository, never()).save(any())
        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `creation is allowed by the base plan without an admin override`()
    {
        val fixture = fixture(entitlementGuard = realGuard(organizationId, commercialGrant = false))
        val command = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "Current record request",
            configuration = configuration,
            access = access,
            idempotencyKey = "create-without-admin-override",
        )

        val result = fixture.service.createAdHoc(command)

        assertNotNull(result.request.id)
        verify(fixture.requestRepository).save(any())
    }

    private fun realGuard(
        organizationId: UUID,
        commercialGrant: Boolean,
    ): InformationRequestEntitlementGuard
    {
        val policyService = mock<SubscriptionPolicyService>()
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(
            OrganizationSubscriptionPolicy().apply {
                tierCode = PlanCode.BUSINESS.name
                subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(organizationId))).thenReturn(
            if (commercialGrant) mapOf(PlanFeature.INFORMATION_REQUESTS to true) else emptyMap(),
        )
        return InformationRequestEntitlementGuard(
            SubscriptionAccessService(
                subscriptionPolicyService = policyService,
                subscriptionUsageService = SubscriptionUsageService(
                    mock<ExchangeUsageCounter>(),
                    mock<OrganizationSeatCounter>(),
                ),
                enforcementConfigService = SubscriptionEnforcementConfigService(SubscriptionEnforcementMode.ENFORCE.name),
            ),
        )
    }

    private data class Fixture(
        val service: InformationRequestAdHocCreationService,
        val definitionRepository: InformationRequestTemplateDefinitionRepository,
        val versionRepository: InformationRequestTemplateVersionRepository,
        val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
        val requestRepository: InformationRequestRepository,
        val configurationWriter: InformationRequestTemplateConfigurationWriter,
        val materializer: InformationRequestTemplateMaterializer,
        val authorizationService: AuthorizationService,
        val transitionHistory: InformationRequestTransitionHistoryService,
        val savedDefinitions: MutableList<InformationRequestTemplateDefinition>,
        val savedVersions: MutableList<InformationRequestTemplateVersion>,
        val savedCapabilities: MutableList<InformationRequestTemplateVersionCapability>,
        val savedRequests: MutableList<InformationRequest>,
    )

    private fun fixture(entitlementGuard: InformationRequestEntitlementGuard = mock()): Fixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = organizationId
            ownerUserId = null
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(exchange)

        val definitions = mutableListOf<InformationRequestTemplateDefinition>()
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        whenever(definitionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestTemplateDefinition>(0).also { definitions += it }
        }
        whenever(definitionRepository.update(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestTemplateDefinition>(0)
        }

        val versions = mutableListOf<InformationRequestTemplateVersion>()
        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestTemplateVersion>(0).also { versions += it }
        }
        whenever(versionRepository.update(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestTemplateVersion>(0)
        }

        val capabilities = mutableListOf<InformationRequestTemplateVersionCapability>()
        val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        whenever(capabilityRepository.findRequiredCapabilities(any())).thenReturn(
            listOf(InformationRequestCapability.RESPONSE_ATTESTATION),
        )
        whenever(capabilityRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestTemplateVersionCapability>(0).also { capabilities += it }
        }

        val requests = mutableListOf<InformationRequest>()
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequest>(0).also { requests += it }
        }
        whenever(requestRepository.findById(any())).thenAnswer { invocation ->
            requests.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }

        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_CREATE,
                ResourceRef.exchange(exchangeId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())

        val configurationWriter = mock<InformationRequestTemplateConfigurationWriter>()
        val materializer = mock<InformationRequestTemplateMaterializer>()
        whenever(materializer.materialize(any(), any())).thenReturn(
            InformationRequestMaterializationResult(requirementCount = 1),
        )
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        whenever(transitionHistory.record(any())).thenAnswer { invocation ->
            val command = invocation.getArgument<InformationRequestTransitionHistoryCommand>(0)
            InformationRequestTransition().apply {
                informationRequestId = command.request.id
                sequenceNumber = 1
                fromState = command.fromState
                toState = command.toState
                mutation = command.mutation
            }
        }

        val service = InformationRequestAdHocCreationService(
            exchangeRepository = exchangeRepository,
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            capabilityRepository = capabilityRepository,
            requestRepository = requestRepository,
            configurationWriter = configurationWriter,
            schemaCompatibility = mock(),
            materializer = materializer,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(InMemoryCommandReceiptStore()),
            transitionHistory = transitionHistory,
            entitlementGuard = entitlementGuard,
        )

        return Fixture(
            service = service,
            definitionRepository = definitionRepository,
            versionRepository = versionRepository,
            capabilityRepository = capabilityRepository,
            requestRepository = requestRepository,
            configurationWriter = configurationWriter,
            materializer = materializer,
            authorizationService = authorizationService,
            transitionHistory = transitionHistory,
            savedDefinitions = definitions,
            savedVersions = versions,
            savedCapabilities = capabilities,
            savedRequests = requests,
        )
    }

    private class InMemoryCommandReceiptStore : CommandReceiptStore
    {
        private val receipts = mutableListOf<CommandReceipt>()

        override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
            receipts.firstOrNull {
                it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
            }

        override fun insert(receipt: CommandReceipt): CommandReceipt
        {
            receipts += receipt
            return receipt
        }
    }
}
