package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.audit.AuditDraftValidationResult
import com.docuhyphen.app.api.service.audit.AuditEventDraftValidator
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture.Question
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture.StoredAnswer
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Choosing a Schema for a resource, removing it, and changing the answers held against it are all
 * recorded. The recorded event says which resource and assignment were affected, what kind of caller
 * acted, and how much changed; it never carries an answer, because a value copied into an audit
 * payload would leave the visibility rules that govern it behind.
 */
class SchemaAssignmentAuditTrailTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())
    private val sessionRef = UUID.randomUUID().toString()

    @Test
    fun `changing answers is recorded without disclosing them`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, sessionRef = sessionRef)

        fixture.save(listOf(
                fixture.entry(fixture.noteContractId, "Answer that must not be logged"),
                fixture.entry(fixture.secondNoteContractId, "Second answer that must not be logged"),
            ),
        )

        val draft = fixture.auditDrafts.single()
        assertEquals(AuditEventType.FIELD_VALUE_UPDATE.key, draft.eventTypeKey)
        assertEquals(AuditOwnerScope.Organization(fixture.organizationId), draft.owner)
        assertEquals(AuditActorKind.HUMAN, draft.actorKind)
        assertEquals(author.id, draft.actorId)
        assertEquals(sessionRef, draft.sessionId)
        assertEquals(fixture.assignmentId.toString(), draft.targetId)
        assertEquals(fixture.schemaDisplayName, draft.targetLabel)
        assertEquals("2", draft.payload["changedCount"])
        assertEquals("0", draft.payload["clearedCount"])
        assertEquals(fixture.resourceId.toString(), draft.payload["resourceId"])

        assertTrue(
            fixture.auditDrafts.none { recorded ->
                recorded.payload.values.any { it.contains("must not be logged") }
            },
            "An answer must never reach an audit payload",
        )
        assertInstanceOf(
            AuditDraftValidationResult.Valid::class.java,
            AuditEventDraftValidator.validate(draft),
            "The draft must satisfy the catalog and prohibited-payload rules",
        )
    }

    @Test
    fun `a cleared answer is counted separately from one that was given a value`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(Question.NOTE to StoredAnswer("Answer to clear", author)),
        )

        fixture.save(listOf(
                FieldValueEntry(fixture.noteContractId, JsonNull),
                fixture.entry(fixture.secondNoteContractId, "Answer that was given"),
            ),
        )

        val draft = fixture.auditDrafts.single()
        assertEquals("2", draft.payload["changedCount"])
        assertEquals("1", draft.payload["clearedCount"])
    }

    @Test
    fun `a save that changes nothing is not recorded`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(Question.NOTE to StoredAnswer("Standing answer", author)),
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Standing answer")))

        assertTrue(
            fixture.auditDrafts.isEmpty(),
            "A save that stores nothing new is not a change worth recording",
        )
    }

    @Test
    fun `choosing a schema for a resource is recorded`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            assigned = false,
            rootSetExists = false,
        )

        fixture.assign()

        val draft = fixture.auditDrafts.single { it.eventTypeKey == AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN.key }
        assertEquals(AuditOwnerScope.Organization(fixture.organizationId), draft.owner)
        assertEquals(fixture.assignmentId.toString(), draft.targetId)
        assertEquals(fixture.schemaDisplayName, draft.targetLabel)
        assertEquals(fixture.schemaVersionId.toString(), draft.payload["schemaVersionId"])
    }

    @Test
    fun `a record about something one person holds is filed under that personal owner`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            assigned = false,
            rootSetExists = false,
            ownerScope = ScopeReference.Personal(author.id),
            schemaScope = ScopeReference.Platform,
        )

        fixture.assign()

        val draft = fixture.auditDrafts.single { it.eventTypeKey == AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN.key }
        assertEquals(AuditOwnerScope.Personal(author.id), draft.owner)
    }

    @Test
    fun `removing a schema is recorded with how many answers went with it`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(Question.NOTE to StoredAnswer("Answer to remove", author)),
        )

        fixture.unassign()

        val draft = fixture.auditDrafts.single()
        assertEquals(AuditEventType.SCHEMA_ASSIGNMENT_UNASSIGN.key, draft.eventTypeKey)
        assertEquals("1", draft.payload["removedValueCount"])
    }

    @Test
    fun `an answer left by a participant is recorded as a participant acting`()
    {
        val participantId = UUID.randomUUID()
        val fixture = SchemaAssignmentFieldsFixture(principal = PrincipalRef.participant(participantId))

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer left by a participant")))

        val draft = fixture.auditDrafts.single()
        assertEquals(AuditActorKind.PARTICIPANT, draft.actorKind)
        assertEquals(participantId, draft.actorId)
        assertEquals("PARTICIPANT", draft.payload["actorPrincipalKind"])
    }
}
