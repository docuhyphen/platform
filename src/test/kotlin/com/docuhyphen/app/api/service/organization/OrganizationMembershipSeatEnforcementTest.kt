package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationMembershipSeatEnforcementTest
{
    private val appUserId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val membershipRepository = mock<OrganizationMembershipRepository>()
    private val appUserRepository = mock<AppUserRepository>()
    private val seatGuard = mock<OrganizationSeatGuard>()
    private val service = OrganizationMembershipService(membershipRepository, appUserRepository, seatGuard, mock())

    @Test
    fun `activating an invited provisioned membership reserves a seat`()
    {
        val membership = membership(OrganizationMembershipStatus.INVITED)
        whenever(membershipRepository.findByUserAndOrg(appUserId, organizationId)).thenReturn(membership)
        whenever(appUserRepository.findById(appUserId)).thenReturn(provisionedUser())
        whenever(membershipRepository.update(membership)).thenReturn(membership)

        service.assignOrgRole(appUserId, organizationId, OrganizationRoleName.ORG_MEMBER)

        verify(seatGuard).enforceAvailableSeat(organizationId)
        verify(membershipRepository).update(membership)
    }

    @Test
    fun `adding a role to an already active member does not reserve another seat`()
    {
        val membership = membership(OrganizationMembershipStatus.ACTIVE)
        whenever(membershipRepository.findByUserAndOrg(appUserId, organizationId)).thenReturn(membership)
        whenever(appUserRepository.findById(appUserId)).thenReturn(provisionedUser())
        whenever(membershipRepository.update(membership)).thenReturn(membership)

        service.assignOrgRole(appUserId, organizationId, OrganizationRoleName.ORG_ADMIN)

        verify(seatGuard, never()).enforceAvailableSeat(organizationId)
    }

    @Test
    fun `inactive and temporary accounts do not consume a seat when membership is recorded`()
    {
        whenever(membershipRepository.findByUserAndOrg(appUserId, organizationId)).thenReturn(null)
        whenever(appUserRepository.findById(appUserId)).thenReturn(provisionedUser().apply {
            isActive = false
            isTemporary = true
        })
        whenever(membershipRepository.save(org.mockito.kotlin.any())).thenAnswer {
            it.arguments[0] as OrganizationMembership
        }

        service.assignOrgRole(appUserId, organizationId, OrganizationRoleName.ORG_MEMBER)

        verify(seatGuard, never()).enforceAvailableSeat(organizationId)
    }

    @Test
    fun `account activation locks organizations in stable order`()
    {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
        whenever(membershipRepository.findActiveByUser(appUserId)).thenReturn(
            listOf(
                membership(OrganizationMembershipStatus.ACTIVE).apply { organizationId = second },
                membership(OrganizationMembershipStatus.ACTIVE).apply { organizationId = first },
            ),
        )

        service.enforceSeatsForAccountActivation(appUserId)

        org.mockito.kotlin.inOrder(seatGuard) {
            verify(seatGuard).enforceAvailableSeat(first)
            verify(seatGuard).enforceAvailableSeat(second)
        }
    }

    private fun membership(status: OrganizationMembershipStatus) = OrganizationMembership().apply {
        appUserId = this@OrganizationMembershipSeatEnforcementTest.appUserId
        organizationId = this@OrganizationMembershipSeatEnforcementTest.organizationId
        this.status = status
    }

    private fun provisionedUser() = AppUser().apply {
        id = appUserId
        email = "member@example.com"
        isActive = true
        isTemporary = false
        deprovisionedAt = null
    }
}
