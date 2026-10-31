package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestPartyDtoMapperTest {
    private fun participantParty(linkedUserShareId: UUID?) = InformationRequestParty().apply {
        informationRequestId = UUID.randomUUID()
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = PrincipalKind.PARTICIPANT
        principalId = UUID.randomUUID()
        shareId = UUID.randomUUID()
        active = true
        this.linkedUserShareId = linkedUserShareId
    }

    @Test
    fun `a participant who linked an account is shown as account linked to a caller who may see identities`() {
        val linked = participantParty(UUID.randomUUID())

        assertTrue(InformationRequestPartyDtoMapper.toDto(linked, revealIdentity = true).accountLinked)
        assertFalse(InformationRequestPartyDtoMapper.toDto(linked, revealIdentity = false).accountLinked)
        assertFalse(InformationRequestPartyDtoMapper.toDto(participantParty(null), revealIdentity = true).accountLinked)
    }
}
