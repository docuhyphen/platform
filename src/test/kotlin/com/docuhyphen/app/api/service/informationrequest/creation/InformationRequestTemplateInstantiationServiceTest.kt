package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestMaterializationResult
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

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

    private fun command(key: String = "create-from-version") = CreateInformationRequestFromTemplateVersionCommand(
        templateVersionId = templateVersionId,
        exchangeId = exchangeId,
        gatesExchangeClosure = false,
        access = access,
        idempotencyKey = key,
    )

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
        assertEquals(actorId, first.request.createdByAppUserId)
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

    @Test
    fun `a Version another owner holds is refused as not found and creates nothing`()
    {
        val fixture = fixture(InformationRequestTemplateScopeKind.ORGANIZATION, UUID.randomUUID())

        val refusal = assertThrows<InformationRequestTemplateVersionUnavailableException> {
            fixture.service.createFromTemplateVersion(command())
        }

        assertEquals(InformationRequestTemplateVersionUnavailableException.NOT_FOUND, refusal.code)
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

    private data class Fixture(
        val service: InformationRequestTemplateInstantiationService,
        val exchange: Exchange,
        val requestRepository: InformationRequestRepository,
        val materializer: InformationRequestTemplateMaterializer,
        val entitlementGuard: InformationRequestEntitlementGuard,
        val transitionHistory: InformationRequestTransitionHistoryService,
        val saved: MutableList<InformationRequest>,
    )

    private fun fixture(
        templateScope: InformationRequestTemplateScopeKind,
        templateOrganizationId: UUID?,
        allowed: Boolean = true,
    ): Fixture
    {
        val exchange = Exchange().apply {
            id = exchangeId
            ownerOrganizationId = organizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(exchange)
        val saved = mutableListOf<InformationRequest>()
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequest>(0).also { saved += it }
        }
        whenever(requestRepository.findById(any())).thenAnswer { invocation ->
            saved.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }
        val referenceService = mock<InformationRequestTemplateReferenceService>()
        whenever(referenceService.requireInstantiableVersion(templateVersionId)).thenReturn(
            InformationRequestTemplateVersionReference(
                templateVersionId = templateVersionId,
                templateDefinitionId = UUID.randomUUID(),
                versionNumber = 2,
                ownerScopeKind = templateScope,
                ownerOrganizationId = templateOrganizationId,
                ownerUserId = null,
            ),
        )
        val materializer = mock<InformationRequestTemplateMaterializer>()
        whenever(materializer.materialize(any(), any())).thenReturn(InformationRequestMaterializationResult(requirementCount = 3))
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_CREATE,
                ResourceRef.exchange(exchangeId),
                access.authorization,
            ),
        ).thenReturn(if (allowed) Decision.Allow() else Decision.Deny("DENIED", "denied"))
        whenever(
            authorizationService.authorize(
                org.mockito.kotlin.eq(access.principal),
                org.mockito.kotlin.eq(Action.INFORMATION_REQUEST_VIEW),
                any(),
                org.mockito.kotlin.eq(access.authorization),
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
        return Fixture(
            service = InformationRequestTemplateInstantiationService(
                exchangeRepository = exchangeRepository,
                requestRepository = requestRepository,
                templateReferenceService = referenceService,
                materializer = materializer,
                entitlementGuard = entitlementGuard,
                authorizationService = authorizationService,
                commandReceiptService = CommandReceiptService(InMemoryTemplateCommandReceiptStore()),
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
}

private class InMemoryTemplateCommandReceiptStore : CommandReceiptStore
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
