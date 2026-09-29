package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.SubjectIdentityExternalIdentifierRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

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
    private val principalDisplayService = mock<PrincipalDisplayService>()
    private val identifierRepository = mock<SubjectIdentityExternalIdentifierRepository>()
    private val service = InformationRequestPartyQueryService(
        requestRepository = requestRepository,
        partyRepository = partyRepository,
        authorizationService = authorizationService,
        principalDisplayService = principalDisplayService,
        identifierRepository = identifierRepository,
    )

    @Test
    fun `the management listing names a subject by its reference`()
    {
        val subjectId = UUID.randomUUID()
        val subjectParty = InformationRequestParty().apply {
            informationRequestId = requestId
            roleKey = InformationRequestShareRoleKey.SUBJECT
            subjectIdentityRefId = subjectId
        }
        whenever(requestRepository.findById(requestId)).thenReturn(request)
        whenever(authorizationService.authorize(any(), any(), any<ResourceRef>(), any())).thenReturn(Decision.Allow())
        whenever(partyRepository.findActiveForRequest(requestId)).thenReturn(listOf(subjectParty))
        whenever(identifierRepository.findForSubjects(listOf(subjectId))).thenReturn(
            listOf(SubjectIdentityExternalIdentifier().apply {
                subjectIdentityRefId = subjectId
                authority = "Records office"
                identifierType = "Account"
                identifierValue = "A-100"
            }),
        )

        val listing = service.listForManagement(requestId, access)

        assertEquals("Account A-100", listing.parties.single().label)
    }

    @Test
    fun `the management listing names the parties the caller may identify and answers the parties ETag`()
    {
        request.partyRevision = 4
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
        whenever(principalDisplayService.display(PrincipalRef.user(viewerUserId)))
            .thenReturn(PrincipalDisplay(name = "Viewer Person", email = "viewer@example.test"))
        whenever(principalDisplayService.display(PrincipalRef.user(peerUserId)))
            .thenReturn(PrincipalDisplay(name = "Peer Person", email = "peer@example.test"))

        val listing = service.listForManagement(requestId, access)

        assertEquals(InformationRequestETag.partiesOf(request), listing.partiesETag)
        assertEquals("Viewer Person", listing.parties.single { it.id == viewerParty.id }.label)
        assertNull(listing.parties.single { it.id == peerParty.id }.label)
        verify(principalDisplayService, never()).display(PrincipalRef.user(peerUserId))
    }

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
