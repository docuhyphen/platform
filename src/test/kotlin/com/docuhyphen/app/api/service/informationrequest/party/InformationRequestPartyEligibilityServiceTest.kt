package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPartyEligibilityServiceTest {
    private val policy = mock<OrganizationExchangePolicyService>()
    private val groups = mock<OrganizationGroupService>()
    private val appUsers = mock<AppUserService>()
    private val participants = mock<ExternalParticipantService>()
    private val recipientAddresses = mock<InformationRequestRecipientAddressResolver>()
    private val service = InformationRequestPartyEligibilityService(
        policy, groups, appUsers, participants, recipientAddresses,
    )
    private val organizationId = UUID.randomUUID()
    private val manager = PrincipalRef.user(UUID.randomUUID())
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
    }

    @Test
    fun `the address of a trusted recipient of the Exchange must be chosen from the trusted recipients`() {
        whenever(recipientAddresses.isTrustedPersonAddress(request.exchangeId, " member@partner.example.test "))
            .thenReturn(true)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.requireUntrustedAddress(request, " member@partner.example.test ")
        }

        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_SELECTION_REQUIRED, refusal.reasonCode)
    }

    @Test
    fun `an address the Exchange does not list as a trusted recipient is never looked up among accounts`() {
        val personal = InformationRequest().apply {
            exchangeId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.USER
            ownerUserId = UUID.randomUUID()
        }

        service.requireUntrustedAddress(request, "member@partner.example.test")
        service.requireUntrustedAddress(request, "nobody@example.test")
        service.requireUntrustedAddress(personal, "member@partner.example.test")

        verifyNoInteractions(appUsers)
    }

    @Test
    fun `an account is checked against the owning organization's sharing policy, and a temporary account as an individual`() {
        val member = AppUser().apply { id = UUID.randomUUID() }
        val temporary = AppUser().apply { id = UUID.randomUUID(); isTemporary = true }
        whenever(appUsers.getById(member.id)).thenReturn(member)
        whenever(appUsers.getById(temporary.id)).thenReturn(temporary)

        service.requireEligible(request, manager, PrincipalRef.user(member.id))
        service.requireEligible(request, manager, PrincipalRef.user(temporary.id))

        verify(policy).assertCanShareWithUser(organizationId, manager.id, member.id)
        verify(policy).assertCanShareWithUser(organizationId, manager.id, null)
    }

    @Test
    fun `a sharing policy refusal is a stable request refusal that keeps the policy's reason`() {
        val outsider = AppUser().apply { id = UUID.randomUUID() }
        whenever(appUsers.getById(outsider.id)).thenReturn(outsider)
        doThrow(IllegalArgumentException("Your organization only permits sharing with members of your organization or a trusted organization."))
            .whenever(policy).assertCanShareWithUser(organizationId, manager.id, outsider.id)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.requireEligible(request, manager, PrincipalRef.user(outsider.id))
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
        assertEquals(
            "Your organization only permits sharing with members of your organization or a trusted organization.",
            refusal.message
        )
    }

    @Test
    fun `an address that belongs to an account is checked as that account, and any other address as an outside individual`() {
        val member = AppUser().apply { id = UUID.randomUUID(); email = "member@partner.example.test" }
        whenever(appUsers.getAppUserByEmail("member@partner.example.test")).thenReturn(member)

        service.requireAddressEligible(request, manager, " member@partner.example.test ")
        service.requireAddressEligible(request, manager, "nobody@example.test")

        verify(policy).assertCanShareWithUser(organizationId, manager.id, member.id)
        verify(policy).assertCanShareWithUser(organizationId, manager.id, null)
    }

    @Test
    fun `an address of a member of an untrusted organization is refused by the sharing policy as that member`() {
        val outsider = AppUser().apply { id = UUID.randomUUID(); email = "outsider@other.example.test" }
        whenever(appUsers.getAppUserByEmail("outsider@other.example.test")).thenReturn(outsider)
        doThrow(IllegalArgumentException("Your organization only permits sharing with members of your organization or a trusted organization."))
            .whenever(policy).assertCanShareWithUser(organizationId, manager.id, outsider.id)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.requireAddressEligible(request, manager, "outsider@other.example.test")
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
    }

    @Test
    fun `a group is checked for its state, ownership, and publication by the sharing policy`() {
        val group = PrincipalGroup().apply { id = UUID.randomUUID() }
        whenever(groups.getById(group.id.toString())).thenReturn(group)
        doThrow(IllegalArgumentException("The selected group is not available for external sharing"))
            .whenever(policy).assertCanShareWithGroup(organizationId, manager.id, group)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            service.requireEligible(request, manager, PrincipalRef.group(group.id))
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
    }

    @Test
    fun `an account or group that does not exist is not found rather than refused by policy`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.requireEligible(request, manager, PrincipalRef.user(UUID.randomUUID()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.requireEligible(request, manager, PrincipalRef.group(UUID.randomUUID()))
        }
    }

    @Test
    fun `a participant must exist and belong to the request's owner before the sharing policy is asked`() {
        val owned = ExternalParticipant().apply {
            id = UUID.randomUUID()
            ownerOrganizationId = organizationId
            email = "nobody@example.test"
        }
        whenever(participants.findOwned(owned.id, ExternalParticipantOwner.Organization(organizationId)))
            .thenReturn(owned)

        service.requireEligible(request, manager, PrincipalRef.participant(owned.id))
        verify(policy).assertCanShareWithUser(organizationId, manager.id, null)

        val unknown = assertThrows(IllegalArgumentException::class.java) {
            service.requireEligible(request, manager, PrincipalRef.participant(UUID.randomUUID()))
        }
        assertEquals("Participant not found", unknown.message)
        verifyNoMoreInteractions(policy)
    }

    @Test
    fun `an existing participant whose address belongs to an account is checked as that account`() {
        val owned = ExternalParticipant().apply {
            id = UUID.randomUUID()
            ownerOrganizationId = organizationId
            email = "member@partner.example.test"
        }
        val member = AppUser().apply { id = UUID.randomUUID(); email = "member@partner.example.test" }
        whenever(participants.findOwned(owned.id, ExternalParticipantOwner.Organization(organizationId))).thenReturn(
            owned
        )
        whenever(appUsers.getAppUserByEmail("member@partner.example.test")).thenReturn(member)

        service.requireEligible(request, manager, PrincipalRef.participant(owned.id))

        verify(policy).assertCanShareWithUser(organizationId, manager.id, member.id)
    }

    @Test
    fun `a personally owned request is checked without an owning organization`() {
        val personal = InformationRequest().apply {
            ownerType = InformationRequestOwnerType.USER
            ownerUserId = manager.id
        }
        val group = PrincipalGroup().apply { id = UUID.randomUUID() }
        whenever(groups.getById(group.id.toString())).thenReturn(group)

        service.requireAddressEligible(personal, manager, "nobody@example.test")
        service.requireEligible(personal, manager, PrincipalRef.group(group.id))

        verify(policy).assertCanShareWithUser(null, manager.id, null)
        verify(policy).assertCanShareWithGroup(null, manager.id, group)
    }
}
