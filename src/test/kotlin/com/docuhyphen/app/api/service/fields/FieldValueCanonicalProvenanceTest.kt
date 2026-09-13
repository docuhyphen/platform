package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Authorship of an answer and of a Schema Assignment is the canonical principal. Every principal the
 * platform can authenticate reaches the same Fields engine, so a participant, a link-verified
 * recipient, a registered application, and a service principal must all be recordable as the author
 * of an answer without being misfiled as a registered user.
 *
 * The retained registered-user column is a foreign key into the registered-user table, so it may only
 * ever name a real registered user. A participant or application answer that filled it in would
 * either name a row that is not a user or fail the key outright.
 *
 * The participant and public-link cases use explicit synthetic principals at the service boundary.
 * No endpoint issues either principal for a Fields call yet, so these are pre-exposure contract
 * tests of the engine rather than proof of a reachable route.
 */
class FieldValueCanonicalProvenanceTest
{
    private val sessionRef = UUID.randomUUID().toString()

    @Test
    fun `an answer left by a registered user records the canonical principal and the legacy author`()
    {
        val userId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(
            principal = PrincipalRef.user(userId),
            sessionRef = sessionRef,
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer left by a registered user")))

        val stored = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals(PrincipalKind.USER, stored.updatedByPrincipalKind)
        assertEquals(userId, stored.updatedByPrincipalId)
        assertEquals(
            userId, stored.updatedByAppUserId,
            "A registered user keeps the retained column filled while it exists",
        )
        assertEquals(
            sessionRef, stored.updatedBySessionRef,
            "The session the change was made in is recorded as a non-secret reference",
        )
    }

    @Test
    fun `an answer left by a participant records the canonical principal only`()
    {
        val participantId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(principal = PrincipalRef.participant(participantId))

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer left by a participant")))

        val stored = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals(PrincipalKind.PARTICIPANT, stored.updatedByPrincipalKind)
        assertEquals(participantId, stored.updatedByPrincipalId)
        assertNull(
            stored.updatedByAppUserId,
            "A participant is not a registered user and must never occupy that key",
        )
    }

    @Test
    fun `an answer left by a registered application records the canonical principal only`()
    {
        val applicationId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(principal = PrincipalRef.application(applicationId))

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer left by an application")))

        val stored = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals(PrincipalKind.APPLICATION, stored.updatedByPrincipalKind)
        assertEquals(applicationId, stored.updatedByPrincipalId)
        assertNull(stored.updatedByAppUserId)
    }

    @Test
    fun `an answer left through a verified link records the public link principal only`()
    {
        val linkId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(principal = PrincipalRef.publicLink(linkId))

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer left through a link")))

        val stored = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals(PrincipalKind.PUBLIC_LINK, stored.updatedByPrincipalKind)
        assertEquals(linkId, stored.updatedByPrincipalId)
        assertNull(stored.updatedByAppUserId)
    }

    @Test
    fun `assigning a schema records who chose it as the canonical principal`()
    {
        val userId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(
            principal = PrincipalRef.user(userId),
            sessionRef = sessionRef,
            assigned = false,
            rootSetExists = false,
        )

        fixture.assign()

        val assignment = fixture.savedAssignments.single()
        assertEquals(PrincipalKind.USER, assignment.assignedByPrincipalKind)
        assertEquals(userId, assignment.assignedByPrincipalId)
        assertEquals(userId, assignment.assignedByAppUserId)
        assertEquals(sessionRef, assignment.assignedBySessionRef)
    }

    @Test
    fun `a schema chosen by a registered application leaves the legacy assigner unset`()
    {
        val applicationId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(
            principal = PrincipalRef.application(applicationId),
            assigned = false,
            rootSetExists = false,
        )

        fixture.assign()

        val assignment = fixture.savedAssignments.single()
        assertEquals(PrincipalKind.APPLICATION, assignment.assignedByPrincipalKind)
        assertEquals(applicationId, assignment.assignedByPrincipalId)
        assertNull(assignment.assignedByAppUserId)
    }

    @Test
    fun `a materialized schema default records the principal that assigned the schema`()
    {
        val participantId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(
            principal = PrincipalRef.participant(participantId),
            assigned = false,
            rootSetExists = false,
            configuredDefault = "\"Configured starting note\"",
        )

        fixture.assign()

        val rootSet = fixture.savedSets.single()
        val stored = requireNotNull(
            fixture.storedByValueSet[rootSet.id]?.get(fixture.secondNoteContractId),
        )
        assertEquals(PrincipalKind.PARTICIPANT, stored.updatedByPrincipalKind)
        assertEquals(participantId, stored.updatedByPrincipalId)
        assertNull(stored.updatedByAppUserId)
    }
}
