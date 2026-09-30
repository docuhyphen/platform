package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestQueryServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.user(UUID.randomUUID()),
        AuthorizationContext(activeOrgId = UUID.randomUUID()),
    )
    private val exchange = Exchange().apply {
        id = exchangeId
        ownerOrganizationId = UUID.randomUUID()
    }
    private val exchangeRepository = mock<ExchangeRepository>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val authorizationService = mock<AuthorizationService>()
    private val service = InformationRequestQueryService(
        exchangeRepository = exchangeRepository,
        requestRepository = requestRepository,
        authorizationService = authorizationService,
    )

    @Test
    fun `a request party cannot read after parent rejection rescission or deletion`()
    {
        val request = InformationRequest().apply { exchangeId = this@InformationRequestQueryServiceTest.exchangeId }
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(authorizationService.authorize(access.principal, Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(request.id), access.authorization)).thenAnswer {
            val outcome = InformationRequestPolicyEvaluator().evaluate(
                com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest(access.principal,
                    Action.INFORMATION_REQUEST_VIEW, ResourceRef.informationRequest(request.id),
                    com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext(
                        com.docuhyphen.app.api.service.auth.authz.OwnerContext.Organization(exchange.ownerOrganizationId!!),
                        policyFacts = com.docuhyphen.app.api.model.informationrequest.InformationRequestParentPolicyFacts(
                            InformationRequestParentSnapshot(exchange.status, exchange.isDeleted))),
                    setOf(com.docuhyphen.app.api.service.auth.authz.Capability.INFORMATION_REQUEST_READ), access.authorization))
            when (outcome) {
                is com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome.Deny -> Decision.Deny(outcome.reasonCode, outcome.message)
                else -> Decision.Allow()
            }
        }
        for (status in listOf(com.docuhyphen.app.api.model.entity.ExchangeStatus.REJECTED,
            com.docuhyphen.app.api.model.entity.ExchangeStatus.RESCINDED))
        {
            exchange.status = status
            assertThrows(ForbiddenException::class.java) { service.findById(request.id, access) }
        }
        exchange.status = com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED
        exchange.isDeleted = true
        assertThrows(ForbiddenException::class.java) { service.findById(request.id, access) }
    }

    @Test
    fun `an owner keeps historical reads after parent rejection rescission or deletion`()
    {
        val request = InformationRequest().apply { exchangeId = this@InformationRequestQueryServiceTest.exchangeId }
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(authorizationService.authorize(access.principal, Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(request.id), access.authorization)).thenAnswer {
            parentPolicyDecision(request.id, com.docuhyphen.app.api.service.auth.authz.Capability.INFORMATION_REQUEST_ADMIN)
        }
        for (status in listOf(com.docuhyphen.app.api.model.entity.ExchangeStatus.REJECTED,
            com.docuhyphen.app.api.model.entity.ExchangeStatus.RESCINDED))
        {
            exchange.status = status
            assertEquals(request, service.findById(request.id, access))
        }
        exchange.status = com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED
        exchange.isDeleted = true
        assertEquals(request, service.findById(request.id, access))
    }

    private fun readableIssuedRequest(): InformationRequest
    {
        val request = InformationRequest().apply {
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
            state = InformationRequestState.ISSUED
        }
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(request.id),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        return request
    }

    private fun parentPolicyDecision(
        requestId: UUID,
        vararg capabilities: com.docuhyphen.app.api.service.auth.authz.Capability,
    ): Decision
    {
        val outcome = InformationRequestPolicyEvaluator().evaluate(
            com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest(access.principal,
                Action.INFORMATION_REQUEST_VIEW, ResourceRef.informationRequest(requestId),
                com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext(
                    com.docuhyphen.app.api.service.auth.authz.OwnerContext.Organization(exchange.ownerOrganizationId!!),
                    policyFacts = com.docuhyphen.app.api.model.informationrequest.InformationRequestParentPolicyFacts(
                        InformationRequestParentSnapshot(exchange.status, exchange.isDeleted))),
                capabilities.toSet(), access.authorization))
        return when (outcome) {
            is com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome.Deny ->
                Decision.Deny(outcome.reasonCode, outcome.message)
            else -> Decision.Allow()
        }
    }

    @Test
    fun `an authorized owner lists the requests for their exchange`()
    {
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.exchange(exchangeId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val requests = listOf(InformationRequest().apply { this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId })
        whenever(requestRepository.findForExchange(exchangeId)).thenReturn(requests)

        val result = service.listForExchange(exchangeId, access)

        assertEquals(requests, result)
    }

    @Test
    fun `a caller denied on the exchange and on every request is refused`()
    {
        val hidden = InformationRequest().apply { this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(requestRepository.findForExchange(exchangeId)).thenReturn(listOf(hidden))
        whenever(
            authorizationService.authorize(
                eq(access.principal),
                eq(Action.INFORMATION_REQUEST_VIEW),
                any<ResourceRef>(),
                eq(access.authorization),
            ),
        ).thenReturn(Decision.Deny("reason", "denied"))

        assertThrows(ForbiddenException::class.java) { service.listForExchange(exchangeId, access) }
    }

    @Test
    fun `listing keeps a request the caller may still read and drops one the parent state denies`()
    {
        val readable = InformationRequest().apply { this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId }
        val denied = InformationRequest().apply { this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(requestRepository.findForExchange(exchangeId)).thenReturn(listOf(readable, denied))
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.exchange(exchangeId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("reason", "the parent Exchange no longer grants access"))
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(readable.id),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(denied.id),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("reason", "denied"))

        val result = service.listForExchange(exchangeId, access)

        assertEquals(listOf(readable), result)
    }

    @Test
    fun `listing keeps every readable request whatever the owner's commercial or operational standing`()
    {
        val issued = InformationRequest().apply {
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
            state = InformationRequestState.ISSUED
        }
        val draft = InformationRequest().apply {
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
            state = InformationRequestState.DRAFT
        }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(requestRepository.findForExchange(exchangeId)).thenReturn(listOf(issued, draft))
        whenever(
            authorizationService.authorize(
                eq(access.principal),
                eq(Action.INFORMATION_REQUEST_VIEW),
                any<ResourceRef>(),
                eq(access.authorization),
            ),
        ).thenReturn(Decision.Allow())

        val result = service.listForExchange(exchangeId, access)

        assertEquals(listOf(issued, draft), result)
    }

    @Test
    fun `a missing exchange is refused before authorization`()
    {
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) { service.listForExchange(exchangeId, access) }
    }

    @Test
    fun `an authorized caller gets a single request by id`()
    {
        val requestId = UUID.randomUUID()
        val request = InformationRequest().apply {
            id = requestId
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
        }
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())

        val result = service.findById(requestId, access)

        assertEquals(request, result)
    }

    @Test
    fun `an issued request stays readable after its execution grant is revoked`()
    {
        val request = readableIssuedRequest()

        assertEquals(request, service.findById(request.id, access))
    }

    @Test
    fun `an issued request stays readable during an operational suspension`()
    {
        val request = readableIssuedRequest()

        assertEquals(request, service.findById(request.id, access))
    }

    @Test
    fun `a draft stays readable after its owner loses the feature`()
    {
        val request = readableIssuedRequest().apply { state = InformationRequestState.DRAFT }

        assertEquals(request, service.findById(request.id, access))
    }

    @Test
    fun `a recipient-bound session reads issued work`()
    {
        val requestId = UUID.randomUUID()
        val sessionAccess = RequestAccessContext(
            PrincipalRef.participant(UUID.randomUUID()),
            AuthorizationContext.ANONYMOUS,
        )
        val request = InformationRequest().apply {
            id = requestId
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
            state = InformationRequestState.ISSUED
        }
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(
            authorizationService.authorize(
                sessionAccess.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                sessionAccess.authorization,
            ),
        ).thenReturn(Decision.Allow())

        val result = service.findById(requestId, sessionAccess)

        assertEquals(request, result)
    }

    @Test
    fun `a caller denied by the central authorizer is refused when fetching by id`()
    {
        val requestId = UUID.randomUUID()
        val request = InformationRequest().apply {
            id = requestId
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
        }
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("reason", "denied"))

        assertThrows(ForbiddenException::class.java) { service.findById(requestId, access) }
    }

    @Test
    fun `fetching a missing request by id is refused before authorization`()
    {
        val requestId = UUID.randomUUID()
        whenever(requestRepository.findById(requestId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) { service.findById(requestId, access) }
    }

    @Test
    fun `a request whose parent exchange cannot be found is a state error, not a missing-request 404`()
    {
        val requestId = UUID.randomUUID()
        val request = InformationRequest().apply {
            id = requestId
            this.exchangeId = this@InformationRequestQueryServiceTest.exchangeId
        }
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(null)

        assertThrows(IllegalStateException::class.java) { service.findById(requestId, access) }
    }
}
