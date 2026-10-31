package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTrustedRecipientCandidateQueryServiceTest {
    private val requestRepository = mock<InformationRequestRepository>()
    private val authorizationService = mock<AuthorizationService>()
    private val exchangeRecipientService = mock<ExchangeRecipientService>()
    private val shareService = mock<ShareService>()
    private val principalDisplayService = mock<PrincipalDisplayService>()
    private val organizationService = mock<OrganizationService>()
    private val service = InformationRequestTrustedRecipientCandidateQueryService(
        requestRepository,
        authorizationService,
        exchangeRecipientService,
        shareService,
        principalDisplayService,
        organizationService,
    )
    private val ownerOrganizationId = UUID.randomUUID()
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = this@InformationRequestTrustedRecipientCandidateQueryServiceTest.ownerOrganizationId
    }
    private val manager = RequestAccessContext(
        PrincipalRef.user(UUID.randomUUID()),
        AuthorizationContext(activeOrgId = ownerOrganizationId),
    )
    private val recipients = mutableListOf<ExchangeRecipient>()
    private val partner = Organization().apply {
        id = UUID.randomUUID()
        name = "Partner organization"
    }

    init {
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(exchangeRecipientService.findByExchangeId(request.exchangeId)).thenReturn(recipients)
        whenever(organizationService.getOrganizationById(partner.id)).thenReturn(partner)
        managesParties(true)
    }

    @Test
    fun `a manager is offered the parent Exchange's accepted trusted recipients with their organization and trust standing`() {
        val person = recipient(
            ExchangeRecipientSelectionType.TRUSTED_PERSON, ExchangeRecipientAcceptanceStatus.ACCEPTED,
            PrincipalRef.user(UUID.randomUUID()), ShareStatus.ACTIVE, "Verified Member"
        )
        val group = recipient(
            ExchangeRecipientSelectionType.TRUSTED_GROUP, ExchangeRecipientAcceptanceStatus.ACCEPTED,
            PrincipalRef.group(UUID.randomUUID()), ShareStatus.ACTIVE, "Published Group"
        )
        recipient(
            ExchangeRecipientSelectionType.TRUSTED_PERSON, ExchangeRecipientAcceptanceStatus.PENDING,
            PrincipalRef.user(UUID.randomUUID()), ShareStatus.PENDING_APPROVAL, "Pending Member"
        )
        recipient(
            ExchangeRecipientSelectionType.REGISTERED_USER, ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            PrincipalRef.user(UUID.randomUUID()), ShareStatus.ACTIVE, "Ordinary Recipient"
        )
        recipient(
            ExchangeRecipientSelectionType.TRUSTED_PERSON, ExchangeRecipientAcceptanceStatus.ACCEPTED,
            PrincipalRef.user(UUID.randomUUID()), ShareStatus.REVOKED, "Removed Member"
        )
        whenever(exchangeRecipientService.trustSuspended(group.id)).thenReturn(true)

        val candidates = service.candidates(request.id, manager)

        assertEquals(listOf(person.id, group.id), candidates.map { it.exchangeRecipientId })
        val offeredPerson = candidates.first()
        assertEquals(ExchangeRecipientSelectionType.TRUSTED_PERSON, offeredPerson.selectionType)
        assertEquals(PrincipalKind.USER, offeredPerson.principal.kind)
        assertEquals("Verified Member", offeredPerson.label)
        assertEquals(partner.id, offeredPerson.targetOrganizationId)
        assertEquals("Partner organization", offeredPerson.targetOrganizationName)
        assertEquals(false, offeredPerson.trustSuspended)
        val offeredGroup = candidates.last()
        assertEquals(PrincipalKind.PRINCIPAL_GROUP, offeredGroup.principal.kind)
        assertEquals("Published Group", offeredGroup.label)
        assertEquals(true, offeredGroup.trustSuspended)
    }

    @Test
    fun `a personally owned request offers no trusted recipients`() {
        request.ownerType = InformationRequestOwnerType.USER
        request.ownerOrganizationId = null
        request.ownerUserId = UUID.randomUUID()

        assertTrue(service.candidates(request.id, manager).isEmpty())
        verify(exchangeRecipientService, never()).findByExchangeId(any())
    }

    @Test
    fun `a caller who may not manage the request's parties is refused`() {
        managesParties(false)

        assertThrows(ForbiddenException::class.java) { service.candidates(request.id, manager) }
        verify(exchangeRecipientService, never()).findByExchangeId(any())
    }

    private fun managesParties(allowed: Boolean) {
        whenever(
            authorizationService.authorize(
                manager.principal,
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                ResourceRef.informationRequest(request.id),
                manager.authorization,
            ),
        ).thenReturn(if (allowed) Decision.Allow() else Decision.Deny(Decision.REASON_NO_GRANT, "not a manager"))
    }

    private fun recipient(
        selectionType: ExchangeRecipientSelectionType,
        acceptance: ExchangeRecipientAcceptanceStatus,
        principal: PrincipalRef,
        shareStatus: ShareStatus,
        label: String,
    ): ExchangeRecipient {
        val share = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.EXCHANGE
            resourceId = request.exchangeId
            principalKind = principal.kind
            principalId = principal.id
            roleName = "VIEWER"
            status = shareStatus
        }
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(principalDisplayService.display(principal)).thenReturn(PrincipalDisplay(name = label, email = null))
        return ExchangeRecipient().apply {
            exchangeId = request.exchangeId
            directShareId = share.id
            this.selectionType = selectionType
            acceptanceStatus = acceptance
            targetOrganizationId = partner.id.takeIf { selectionType != ExchangeRecipientSelectionType.REGISTERED_USER }
        }.also(recipients::add)
    }
}
