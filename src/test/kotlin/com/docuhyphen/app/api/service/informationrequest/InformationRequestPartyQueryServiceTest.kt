package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestPartyQueryServiceTest
{
    private val requestId = UUID.randomUUID()
    private val viewerUserId = UUID.randomUUID()
    private val peerUserId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.user(viewerUserId),
        AuthorizationContext(activeOrgId = UUID.randomUUID()),
    )
    private val request = InformationRequest().apply { id = requestId }
    private val viewerParty = InformationRequestParty().apply {
        informationRequestId = requestId
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = PrincipalKind.USER
        principalId = viewerUserId
        exchangeRecipientId = UUID.randomUUID()
    }
    private val peerParty = InformationRequestParty().apply {
        informationRequestId = requestId
        roleKey = InformationRequestShareRoleKey.REVIEWER
        principalKind = PrincipalKind.USER
        principalId = peerUserId
        exchangeRecipientId = UUID.randomUUID()
    }
    private val requestRepository = mock<InformationRequestRepository>()
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val authorizationService = mock<AuthorizationService>()
    private val service = InformationRequestPartyQueryService(
        requestRepository = requestRepository,
        partyRepository = partyRepository,
        authorizationService = authorizationService,
    )

    @Test
    fun `a non-managing peer sees another party's own record but not the other party's identity`()
    {
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("reason", "not a manager"))
        whenever(partyRepository.findActiveForRequest(requestId)).thenReturn(listOf(viewerParty, peerParty))

        val result = service.listForRequest(requestId, access)

        val ownRow = result.single { it.id == viewerParty.id }
        assertEquals(viewerUserId, ownRow.principalId)
        assertEquals(viewerParty.exchangeRecipientId, ownRow.exchangeRecipientId)

        val peerRow = result.single { it.id == peerParty.id }
        assertNull(peerRow.principalId, "a peer party's principal id must not leak to another non-managing party")
        assertNull(peerRow.principalKind)
        assertNull(peerRow.exchangeRecipientId)
        assertEquals(InformationRequestShareRoleKey.REVIEWER, peerRow.roleKey)
    }

    @Test
    fun `a manager sees full identity for every party`()
    {
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(partyRepository.findActiveForRequest(requestId)).thenReturn(listOf(viewerParty, peerParty))

        val result = service.listForRequest(requestId, access)

        val peerRow = result.single { it.id == peerParty.id }
        assertEquals(peerUserId, peerRow.principalId)
        assertEquals(peerParty.exchangeRecipientId, peerRow.exchangeRecipientId)
    }

    @Test
    fun `a caller denied the view action never reaches the party repository`()
    {
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("reason", "denied"))

        assertThrows(ForbiddenException::class.java) { service.listForRequest(requestId, access) }

        verify(partyRepository, never()).findActiveForRequest(any())
    }

    @Test
    fun `a missing request is refused before authorization`()
    {
        whenever(requestRepository.findById(requestId)).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) { service.listForRequest(requestId, access) }
    }
}
