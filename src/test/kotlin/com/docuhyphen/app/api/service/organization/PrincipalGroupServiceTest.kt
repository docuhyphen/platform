package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestGroupRemovalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class PrincipalGroupServiceTest {
    private val groupRepository = mock<PrincipalGroupRepository>()
    private val memberRepository = mock<PrincipalGroupMemberRepository>()
    private val shareService = mock<ShareService>()
    private val requestGroupRemoval = mock<InformationRequestGroupRemovalService>()
    private val service =
        PrincipalGroupService(groupRepository, memberRepository, mock(), shareService, requestGroupRemoval)

    @Test
    fun `removing a group revokes the request parties it holds before the Shares it still holds`() {
        val group = PrincipalGroup().apply {
            name = "Reviewers"
            scope = PrincipalGroupScope.PERSONAL
        }
        whenever(groupRepository.findById(group.id)).thenReturn(group)
        val removals =
            listOf<(UUID) -> Unit>(service::deactivateGroup, service::deleteGroup, service::deletePersonalGroup)

        removals.forEach { remove ->
            clearInvocations(requestGroupRemoval, shareService)
            remove(group.id)

            inOrder(requestGroupRemoval, shareService) {
                verify(requestGroupRemoval).groupRemoved(group.id)
                verify(shareService).revokeGroupAccess(group.id)
            }
        }
    }

    @Test
    fun `the active members of a group are read from its active membership rows`() {
        val groupId = UUID.randomUUID()
        val members = listOf(
            PrincipalGroupMember().apply {
                principalGroupId = groupId
                principalKind = PrincipalKind.USER
                principalId = UUID.randomUUID()
            },
            PrincipalGroupMember().apply {
                principalGroupId = groupId
                principalKind = PrincipalKind.PARTICIPANT
                principalId = UUID.randomUUID()
            },
        )
        whenever(memberRepository.findActiveMembers(groupId)).thenReturn(members)

        assertEquals(members, service.activeMembers(groupId))
        assertEquals(emptyList<PrincipalGroupMember>(), service.activeMembers(UUID.randomUUID()))
    }
}
