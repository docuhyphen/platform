package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestMaterializationResult
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.creation.model.TemplateInstantiationFixture
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.template.ForeignTemplateVersionFixture
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTemplateInstantiationServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val templateVersionId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.user(actorId),
        AuthorizationContext(activeOrgId = organizationId),
    )

    private fun command(
        key: String = "create-from-version",
        actorAccess: RequestAccessContext = access,
    ) = CreateInformationRequestFromTemplateVersionCommand(
        templateVersionId = templateVersionId,
        exchangeId = exchangeId,
        gatesExchangeClosure = false,
        access = actorAccess,
        idempotencyKey = key,
    )

    @ParameterizedTest
    @EnumSource(value = PrincipalKind::class, names = ["USER", "APPLICATION", "SERVICE_ACCOUNT"])
    fun `the draft records the authorized principal as its creator`(kind: PrincipalKind) {
        val actorAccess = RequestAccessContext(PrincipalRef(kind, UUID.randomUUID()), access.authorization)
        val fixture =
            fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, actorAccess = actorAccess)

        val created = fixture.service.createFromTemplateVersion(command("create-as-$kind", actorAccess))

        assertEquals(kind, created.request.createdByPrincipalKind)
        assertEquals(actorAccess.principal.id, created.request.createdByPrincipalId)
    }

    @Test
    fun `a retried creation from a Version answers the request with the ETag it has after a later change`() {
        val fixture = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId)
        val first = fixture.service.createFromTemplateVersion(command())
        fixture.saved.single().aggregateRevision += 1

        val replay = fixture.service.createFromTemplateVersion(command())

        assertNotEquals(first.requestETag, replay.requestETag)
        assertEquals(InformationRequestETag.aggregateOf(fixture.saved.single()), replay.requestETag)
        assertEquals(1, fixture.saved.size)
    }

    @Test
    fun `a published Version the owner holds becomes a pinned draft once per key`()
    {
        val fixture = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId)

        val first = fixture.service.createFromTemplateVersion(command())
        val replay = fixture.service.createFromTemplateVersion(command())

        assertEquals(first.request.id, replay.request.id)
        assertEquals(1, fixture.saved.size)
        assertEquals(templateVersionId, first.request.templateVersionId)
        assertEquals(InformationRequestState.DRAFT, first.request.state)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, first.request.ownerType)
        assertEquals(organizationId, first.request.ownerOrganizationId)
        assertEquals(false, first.request.gatesExchangeClosure)
        assertEquals(PrincipalKind.USER, first.request.createdByPrincipalKind)
        assertEquals(actorId, first.request.createdByPrincipalId)
        assertEquals(3, first.requirementCount)
        val recorded = argumentCaptor<InformationRequestTransitionHistoryCommand>()
        verify(fixture.transitionHistory).record(recorded.capture())
        assertEquals(InformationRequestMutation.CREATE_DRAFT, recorded.firstValue.mutation)
        verify(fixture.entitlementGuard).requireRequestCreation(fixture.exchange)
        verify(fixture.materializer).materialize(any(), any())
    }

    @Test
    fun `a platform Version is refused until the owner copies it into its own Templates`()
    {
        val fixture = fixture(InformationRequestTemplateScopeKind.PLATFORM, null)

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.createFromTemplateVersion(command())
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.PLATFORM_COPY_REQUIRED, refusal.code)
        verify(fixture.requestRepository, never()).save(any())
    }

    @ParameterizedTest
    @EnumSource(value = InformationRequestTemplateScopeKind::class, names = ["ORGANIZATION", "PERSONAL"])
    fun `another owner's Version is refused as not found before that owner's plan or the Version's lifecycle is described`(
        ownerScope: InformationRequestTemplateScopeKind,
    )
    {
        val foreign = ForeignTemplateVersionFixture(ownerScope, templateVersionId)
        val fixture = fixture(
            InformationRequestTemplateScopeKind.ORGANIZATION,
            organizationId,
            referenceService = foreign.references,
        )

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.createFromTemplateVersion(command())
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, refusal.code)
        verifyNoInteractions(foreign.entitlementGuard)
        verify(fixture.requestRepository, never()).save(any())
    }

    @Test
    fun `an owner without the entitlement or a caller who may not create on the Exchange creates nothing`()
    {
        val unentitled = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId)
        whenever(unentitled.entitlementGuard.requireRequestCreation(any())).thenThrow(IllegalStateException("not included"))
        val unauthorized = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, allowed = false)

        assertThrows<IllegalStateException> { unentitled.service.createFromTemplateVersion(command()) }
        assertThrows<ForbiddenException> { unauthorized.service.createFromTemplateVersion(command()) }

        verify(unentitled.requestRepository, never()).save(any())
        verify(unauthorized.requestRepository, never()).save(any())
    }

    @Test
    fun `a caller who may not create on the Exchange learns nothing about the owner's plan or the Exchange state`() {
        val outsider = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, allowed = false)
        whenever(outsider.entitlementGuard.requireRequestCreation(any())).thenThrow(IllegalStateException("past due"))
        outsider.exchange.status = ExchangeStatus.ENDED

        assertThrows<ForbiddenException> { outsider.service.createFromTemplateVersion(command()) }

        verify(outsider.entitlementGuard, never()).requireRequestCreation(any())
        verify(outsider.requestRepository, never()).save(any())
    }

    private fun fixture(
        templateScope: InformationRequestTemplateScopeKind,
        templateOrganizationId: UUID?,
        allowed: Boolean = true,
        actorAccess: RequestAccessContext = access,
        referenceService: InformationRequestTemplateReferenceService = referenceStub(
            templateScope,
            templateOrganizationId
        ),
    ): TemplateInstantiationFixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = organizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val exchangeLookup = mock<ExchangeLookupService>()
        whenever(exchangeLookup.lockExchangeForUpdate(exchangeId)).thenReturn(exchange)
        val saved = mutableListOf<InformationRequest>()
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequest>(0).also { saved += it }
        }
        whenever(requestRepository.findById(any())).thenAnswer { invocation ->
            saved.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }
        val materializer = mock<InformationRequestTemplateMaterializer>()
        whenever(materializer.materialize(any(), any())).thenReturn(
            InformationRequestMaterializationResult(
                requirementCount = 3
            )
        )
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                actorAccess.principal,
                Action.INFORMATION_REQUEST_CREATE,
                ResourceRef.exchange(exchangeId),
                actorAccess.authorization,
            ),
        ).thenReturn(if (allowed) Decision.Allow() else Decision.Deny("DENIED", "denied"))
        whenever(
            authorizationService.authorize(
                eq(actorAccess.principal),
                eq(Action.INFORMATION_REQUEST_VIEW),
                any(),
                eq(actorAccess.authorization),
            ),
        ).thenReturn(Decision.Allow())
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        val transitionHistory = mock<InformationRequestTransitionHistoryService>()
        whenever(transitionHistory.record(any())).thenAnswer { invocation ->
            val recorded = invocation.getArgument<InformationRequestTransitionHistoryCommand>(0)
            InformationRequestTransition().apply {
                informationRequestId = recorded.request.id
                sequenceNumber = 1
                toState = recorded.toState
                mutation = recorded.mutation
            }
        }
        return TemplateInstantiationFixture(
            service = InformationRequestTemplateInstantiationService(
                exchangeLookup = exchangeLookup,
                requestRepository = requestRepository,
                templateReferenceService = referenceService,
                materializer = materializer,
                entitlementGuard = entitlementGuard,
                authorizationService = authorizationService,
                commandReceiptService = CommandReceiptService(
                    InMemoryTemplateCommandReceiptStore(),
                    CommandReceiptScope()
                ),
                transitionHistory = transitionHistory,
            ),
            exchange = exchange,
            requestRepository = requestRepository,
            materializer = materializer,
            entitlementGuard = entitlementGuard,
            transitionHistory = transitionHistory,
            saved = saved,
        )
    }

    private fun referenceStub(
        templateScope: InformationRequestTemplateScopeKind,
        templateOrganizationId: UUID?,
    ): InformationRequestTemplateReferenceService {
        val referenceService = mock<InformationRequestTemplateReferenceService>()
        whenever(referenceService.requireInstantiableVersion(eq(templateVersionId), any())).thenReturn(
            InformationRequestTemplateVersionReference(
                templateVersionId = templateVersionId,
                templateDefinitionId = UUID.randomUUID(),
                versionNumber = 2,
                ownerScopeKind = templateScope,
                ownerOrganizationId = templateOrganizationId,
                ownerUserId = null,
            ),
        )
        return referenceService
    }
}

private class InMemoryTemplateCommandReceiptStore : CommandReceiptStore
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
