package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

class InformationRequestDelegatedGroupAuthorityTest {
    private val fixture = InformationRequestDelegatedAuthorityAuthorizationFixture()
    private val group = PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, UUID.randomUUID())
    private val member = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `an active member of a delegated group responds, uploads evidence, and confirms for the assigned party`() {
        fixture.addMember(group, member)

        val grant = fixture.grant(group, "grant-group-authority")

        actingActions.forEach { action -> assertTrue(fixture.authorize(member, action).isAllowed, "$action") }
        assertEquals(listOf(fixture.assignedParty.id to grant.authority.id), actingPartiesOf(member))
    }

    @Test
    fun `a member who leaves the delegated group loses the authority at once`() {
        fixture.addMember(group, member)
        fixture.grant(group, "grant-group-authority")

        fixture.removeMember(group, member)

        assertRefused(fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertEquals(emptyList<Pair<UUID, UUID?>>(), actingPartiesOf(member))
    }

    @Test
    fun `a principal outside the delegated group is not treated as one of its members`() {
        val outsider = PrincipalRef.user(UUID.randomUUID())
        fixture.addMember(group, member)
        fixture.addMember(PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, UUID.randomUUID()), outsider)
        fixture.grant(group, "grant-group-authority")

        assertRefused(fixture.authorize(outsider, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertEquals(emptyList<Pair<UUID, UUID?>>(), actingPartiesOf(outsider))
    }

    @Test
    fun `a group authority alone gives its members access to the Requirement it names`() {
        fixture.addMember(group, member)

        val beforeGrant = fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND)
        fixture.grant(group, "grant-group-authority")

        assertEquals(Decision.REASON_NO_GRANT, (beforeGrant as Decision.Deny).reasonCode)
        assertTrue(fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND).isAllowed)
        assertRefused(
            fixture.authorize(
                member,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                fixture.otherRequirement.id
            )
        )
    }

    @Test
    fun `a delegated participant and a participant member act through their linked accounts`() {
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val participantAccount = PrincipalRef.user(UUID.randomUUID())
        val participantMember = PrincipalRef.participant(UUID.randomUUID())
        val memberAccount = PrincipalRef.user(UUID.randomUUID())
        fixture.linkAccount(participant, participantAccount)
        fixture.linkAccount(participantMember, memberAccount)
        fixture.addMember(group, participantMember)

        val participantGrant = fixture.grant(participant, "grant-participant-authority")
        val groupGrant = fixture.grant(group, "grant-group-authority")

        assertTrue(fixture.authorize(participantAccount, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND).isAllowed)
        assertTrue(fixture.authorize(memberAccount, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND).isAllowed)
        assertEquals(
            listOf(fixture.assignedParty.id to participantGrant.authority.id),
            actingPartiesOf(participantAccount)
        )
        assertEquals(listOf(fixture.assignedParty.id to groupGrant.authority.id), actingPartiesOf(memberAccount))
    }

    @Test
    fun `an expired or revoked group authority no longer lets its members act`() {
        val expiredGroup = PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, UUID.randomUUID())
        val expiredMember = PrincipalRef.user(UUID.randomUUID())
        fixture.addMember(group, member)
        fixture.addMember(expiredGroup, expiredMember)
        val now = Instant.now()
        fixture.grant(
            expiredGroup,
            "grant-expired-group-authority",
            effectiveAt = Timestamp.from(now.minus(Duration.ofDays(2))),
            expiresAt = Timestamp.from(now.minus(Duration.ofDays(1))),
        )
        val grant = fixture.grant(group, "grant-group-authority")
        assertTrue(fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND).isAllowed)

        fixture.revoke(grant, "revoke-group-authority")

        assertRefused(fixture.authorize(expiredMember, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertRefused(fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertEquals(emptyList<Pair<UUID, UUID?>>(), actingPartiesOf(expiredMember))
        assertEquals(emptyList<Pair<UUID, UUID?>>(), actingPartiesOf(member))
    }

    @Test
    fun `a group authority reaches exactly the requirements it names`() {
        val unscopedGroup = PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, UUID.randomUUID())
        val unscopedMember = PrincipalRef.user(UUID.randomUUID())
        fixture.addMember(group, member)
        fixture.addMember(unscopedGroup, unscopedMember)

        fixture.grant(group, "grant-scoped-group-authority")
        fixture.grant(unscopedGroup, "grant-request-wide-group-authority", requirementId = null)

        assertTrue(fixture.authorize(member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND).isAllowed)
        assertRefused(
            fixture.authorize(
                member,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                fixture.otherRequirement.id
            )
        )
        assertEquals(emptyList<Pair<UUID, UUID?>>(), actingPartiesOf(member, fixture.otherRequirement.id))
        listOf(fixture.requirement.id, fixture.otherRequirement.id).forEach { requirementId ->
            assertTrue(
                fixture.authorize(
                    unscopedMember,
                    Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                    requirementId
                ).isAllowed
            )
        }
    }

    private fun actingPartiesOf(
        principal: PrincipalRef,
        requirementId: UUID = fixture.requirement.id
    ): List<Pair<UUID, UUID?>> =
        fixture.requirementProvider.actingPartiesFor(
            fixture.request,
            setOf(InformationRequestContributorRole.CONTRIBUTOR),
            principal,
            requirementId,
        ).map { it.party.id to it.delegatedAuthorityId }

    private fun assertRefused(decision: Decision) {
        assertTrue(decision is Decision.Deny, "$decision")
    }

    private companion object {
        val actingActions = listOf(
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
            Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
            Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
        )
    }
}
