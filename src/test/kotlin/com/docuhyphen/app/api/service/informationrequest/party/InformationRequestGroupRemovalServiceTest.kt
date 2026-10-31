package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestGroupRemovalServiceTest {
    @Test
    fun `a group removal locks every request it changes before revoking any of the group's parties`() {
        val groupId = UUID.randomUUID()
        val later = party(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val earlier = party(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val parties = mock<InformationRequestPartyRepository>()
        whenever(parties.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)).thenReturn(
            listOf(
                later,
                earlier
            )
        )
        val revocations = mock<InformationRequestPartyRevocationService>()
        val gate = mock<InformationRequestMutationGate>()

        InformationRequestGroupRemovalService(parties, revocations, gate).groupRemoved(groupId)

        inOrder(gate, revocations) {
            verify(gate).lockExisting(listOf(earlier.informationRequestId, later.informationRequestId))
            verify(revocations).revokeForGroupRemoval(earlier.informationRequestId, earlier.id)
            verify(revocations).revokeForGroupRemoval(later.informationRequestId, later.id)
        }
    }

    private fun party(requestId: UUID) = InformationRequestParty().apply {
        id = UUID.randomUUID()
        informationRequestId = requestId
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = PrincipalKind.PRINCIPAL_GROUP
        active = true
    }
}
