package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestMaterializationResult
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.creation.model.AdHocCreationFixture
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.template.*
import com.docuhyphen.app.api.service.subscription.*
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.util.*

class InformationRequestAdHocCreationServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(actorId), AuthorizationContext(activeOrgId = organizationId))
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
    fun `a retried ad hoc creation answers the request with the ETag it has after a later change`() {
        val fixture = fixture()
        val command = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "Current record request",
            description = "Collect current process records",
            configuration = configuration,
            gatesExchangeClosure = false,
            access = access,
            idempotencyKey = "create-before-a-later-change",
        )
        val first = fixture.service.createAdHoc(command)
        fixture.savedRequests.single().aggregateRevision += 1

        val replay = fixture.service.createAdHoc(command)

        assertNotEquals(first.requestETag, replay.requestETag)
        assertEquals(InformationRequestETag.aggregateOf(fixture.savedRequests.single()), replay.requestETag)
        assertEquals(1, fixture.savedRequests.size)
    }

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
        assertEquals(PrincipalKind.USER, version.publishedByPrincipalKind)
        assertEquals(actorId, version.publishedByPrincipalId)
        assertNotNull(version.publishedAt)
        assertEquals(version.id, request.templateVersionId)
        assertEquals(exchangeId, request.exchangeId)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, request.ownerType)
        assertEquals(organizationId, request.ownerOrganizationId)
        assertEquals(PrincipalKind.USER, request.createdByPrincipalKind)
        assertEquals(actorId, request.createdByPrincipalId)
        assertEquals(false, request.gatesExchangeClosure)

        verify(fixture.authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        verify(fixture.configurationWriter).replaceConfiguration(version, configuration)
        val materializerAccess = argumentCaptor<FieldsAccessContext>()
        verify(fixture.materializer).materialize(eq(request), materializerAccess.capture())
        assertEquals(access.principal, materializerAccess.firstValue.principal)
        assertEquals(access.authorization, materializerAccess.firstValue.authorization)
        verify(fixture.definitionRepository, times(1)).save(any())
        verify(fixture.requestRepository, times(1)).save(any())
    }

    @Test
    fun `an ad hoc request whose name or description is too long is refused before anything is stored`() {
        val fixture = fixture()
        val longName = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "n".repeat(256),
            description = null,
            configuration = configuration,
            gatesExchangeClosure = false,
            access = access,
            idempotencyKey = "create-long-name",
        )
        val longDescription = longName.copy(
            displayName = "Current record request",
            description = "d".repeat(1025),
            idempotencyKey = "create-long-description",
        )

        assertThrows<InformationRequestTemplateValidationException> { fixture.service.createAdHoc(longName) }
        assertThrows<InformationRequestTemplateValidationException> { fixture.service.createAdHoc(longDescription) }

        assertEquals(0, fixture.savedDefinitions.size)
        assertEquals(0, fixture.savedRequests.size)
    }

    @Test
    fun `creation is denied and nothing persisted when the entitlement guard refuses it`()
    {
        val deniedGuard = mock<InformationRequestEntitlementGuard>()
        whenever(deniedGuard.requireRequestCreation(any())).thenThrow(
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

        verify(deniedGuard).requireRequestCreation(any())
        verify(fixture.authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(exchangeId),
            access.authorization,
        )
        verify(fixture.requestRepository, never()).save(any())
        verify(fixture.definitionRepository, never()).save(any())
    }

    @Test
    fun `a caller who may not create on the Exchange learns nothing about the owner's plan`() {
        val deniedGuard = mock<InformationRequestEntitlementGuard>()
        whenever(deniedGuard.requireRequestCreation(any())).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.SUBSCRIPTION_PAST_DUE,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "The owner's subscription is past due.",
                ),
            ),
        )
        val fixture = fixture(entitlementGuard = deniedGuard)
        whenever(fixture.authorizationService.authorize(any(), eq(Action.INFORMATION_REQUEST_CREATE), any(), any()))
            .thenReturn(Decision.Deny("DENIED", "denied"))

        assertThrows<ForbiddenException> {
            fixture.service.createAdHoc(
                CreateAdHocInformationRequestCommand(
                    exchangeId = exchangeId,
                    displayName = "Current record request",
                    configuration = configuration,
                    access = access,
                    idempotencyKey = "create-by-an-outsider",
                ),
            )
        }

        verify(deniedGuard, never()).requireRequestCreation(any())
        verify(fixture.requestRepository, never()).save(any())
    }

    @ParameterizedTest
    @EnumSource(value = PrincipalKind::class, names = ["USER", "APPLICATION", "SERVICE_ACCOUNT"])
    fun `private Template creation and publication preserve the authorized principal`(kind: PrincipalKind) {
        val actor = PrincipalRef(kind, UUID.randomUUID())
        val actorAccess = RequestAccessContext(actor, access.authorization)
        val fixture = fixture(actorAccess = actorAccess)
        fixture.service.createAdHoc(
            CreateAdHocInformationRequestCommand(
                exchangeId = exchangeId,
                displayName = "Current record confirmation",
                configuration = configuration,
                access = actorAccess,
                idempotencyKey = "create-with-$kind",
            )
        )

        val definition = fixture.savedDefinitions.single()
        val version = fixture.savedVersions.single()
        assertEquals(actor.kind, definition.createdByPrincipalKind)
        assertEquals(actor.id, definition.createdByPrincipalId)
        assertEquals(actor.kind, version.createdByPrincipalKind)
        assertEquals(actor.id, version.createdByPrincipalId)
        assertEquals(actor.kind, version.publishedByPrincipalKind)
        assertEquals(actor.id, version.publishedByPrincipalId)
        val request = fixture.savedRequests.single()
        assertEquals(actor.kind, request.createdByPrincipalKind)
        assertEquals(actor.id, request.createdByPrincipalId)
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
            requestRepository = mock(),
            subscriptionAccessService = SubscriptionAccessService(
                subscriptionPolicyService = policyService,
                subscriptionUsageService = SubscriptionUsageService(
                    mock<ExchangeUsageCounter>(),
                    mock<OrganizationSeatCounter>(),
                ),
                enforcementConfigService = SubscriptionEnforcementConfigService(SubscriptionEnforcementMode.ENFORCE.name),
            ),
        )
    }

    private fun fixture(
        entitlementGuard: InformationRequestEntitlementGuard = mock(),
        actorAccess: RequestAccessContext = access,
    ): AdHocCreationFixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = organizationId
            ownerUserId = null
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val exchangeLookup = mock<ExchangeLookupService>()
        whenever(exchangeLookup.lockExchangeForUpdate(exchangeId)).thenReturn(exchange)

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
                actorAccess.principal,
                Action.INFORMATION_REQUEST_CREATE,
                ResourceRef.exchange(exchangeId),
                actorAccess.authorization,
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

        val schemaCompatibility = mock<InformationRequestTemplateSchemaCompatibility>()
        val versionPublisher = InformationRequestPrivateVersionPublisher(
            versionRepository = versionRepository,
            configurationWriter = configurationWriter,
            schemaCompatibility = schemaCompatibility,
            freezer = InformationRequestTemplateVersionFreezer(
                definitionRepository = definitionRepository,
                versionRepository = versionRepository,
                capabilityRepository = capabilityRepository,
                schemaCompatibility = schemaCompatibility,
                projectionLoader = mock(),
                conditionContracts = mock(),
                fieldMembership = mock(),
            ),
        )
        val service = InformationRequestAdHocCreationService(
            exchangeLookup = exchangeLookup,
            definitionRepository = definitionRepository,
            versionPublisher = versionPublisher,
            requestRepository = requestRepository,
            materializer = materializer,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(InMemoryCommandReceiptStore(), CommandReceiptScope()),
            transitionHistory = transitionHistory,
            entitlementGuard = entitlementGuard,
        )

        return AdHocCreationFixture(
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

        override fun lockCommand(request: CommandReceiptRequest) = Unit

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
