package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.fields.BlueprintFieldDefaultEntry
import com.docuhyphen.app.api.model.fields.FieldPrincipalProvenance
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture.Question
import com.docuhyphen.app.api.service.fields.model.StoredAnswer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class SchemaAssignmentAuditTrailTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())
    private val sessionRef = UUID.randomUUID().toString()

    @Test
    fun `changing answers is recorded without disclosing them`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, sessionRef = sessionRef)

        fixture.save(
            listOf(
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
        assertEquals("USER", draft.payload["valueProvenance"])
        assertEquals("process:recorded-note,process:recorded-second-note", draft.payload["fieldKeys"])
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

        fixture.save(
            listOf(
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

    @Test
    fun `Blueprint defaults stored on a new resource are recorded as a value change without the values`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.applyBlueprintDefaults(
            listOf(
                BlueprintFieldDefaultEntry(fixture.noteDefinitionId, JsonPrimitive("Default that must not be logged")),
                BlueprintFieldDefaultEntry(
                    fixture.secondNoteDefinitionId,
                    JsonPrimitive("Second default that must not be logged")
                ),
            )
        )

        val draft = fixture.auditDrafts.single()
        assertEquals(AuditEventType.FIELD_VALUE_UPDATE.key, draft.eventTypeKey)
        assertEquals("BLUEPRINT_DEFAULT", draft.payload["valueProvenance"])
        assertEquals("2", draft.payload["changedCount"])
        assertEquals("0", draft.payload["clearedCount"])
        assertEquals("process:recorded-note,process:recorded-second-note", draft.payload["fieldKeys"])
        assertTrue(
            draft.payload.values.none { it.contains("must not be logged") },
            "A default value must never reach an audit payload",
        )
        assertInstanceOf(AuditDraftValidationResult.Valid::class.java, AuditEventDraftValidator.validate(draft))
    }

    @Test
    fun `Blueprint defaults that store nothing record nothing`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.applyBlueprintDefaults(listOf(BlueprintFieldDefaultEntry(fixture.noteDefinitionId, JsonNull)))

        assertTrue(fixture.auditDrafts.isEmpty(), "Defaults that stored nothing changed nothing")
    }

    @Test
    fun `a schema default stored when the schema is assigned is recorded with the assignment and as a value change`() {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            assigned = false,
            rootSetExists = false,
            configuredDefault = "\"Configured note that must not be logged\"",
        )

        fixture.assign()

        val assigned = fixture.auditDrafts.single { it.eventTypeKey == AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN.key }
        assertEquals("1", assigned.payload["defaultValueCount"])
        val defaults = fixture.auditDrafts.single { it.eventTypeKey == AuditEventType.FIELD_VALUE_UPDATE.key }
        assertEquals("SCHEMA_DEFAULT", defaults.payload["valueProvenance"])
        assertEquals("1", defaults.payload["changedCount"])
        assertEquals("process:recorded-second-note", defaults.payload["fieldKeys"])
        assertTrue(
            fixture.auditDrafts.none { draft -> draft.payload.values.any { it.contains("must not be logged") } },
            "A default value must never reach an audit payload",
        )
    }

    @Test
    fun `assigning a schema with no configured default records no value change`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        fixture.assign()

        val assigned = fixture.auditDrafts.single()
        assertEquals(AuditEventType.SCHEMA_ASSIGNMENT_ASSIGN.key, assigned.eventTypeKey)
        assertEquals("0", assigned.payload["defaultValueCount"])
    }

    @Test
    fun `a capture failure under a fail-closed audit policy stops the change it would have recorded`() {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenThrow(AuditCaptureFailedException("The audit outbox is unavailable", null))
        val trail = SchemaAssignmentAuditTrail(recorder)
        val assignment = SchemaAssignment().apply {
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            schemaVersionId = UUID.randomUUID()
        }
        val provenance = FieldPrincipalProvenance(author, sessionRef)

        assertAll(
            {
                assertThrows<AuditCaptureFailedException> {
                    trail.schemaAssigned(assignment, ScopeReference.Platform, provenance, "Process record", 0)
                }
            },
            {
                assertThrows<AuditCaptureFailedException> {
                    trail.schemaUnassigned(assignment, ScopeReference.Platform, provenance, "Process record", 0)
                }
            },
            {
                assertThrows<AuditCaptureFailedException> {
                    trail.valuesUpdated(
                        assignment, ScopeReference.Platform, provenance, "Process record",
                        FieldValueProvenance.USER, 1, 0, listOf("process:recorded-note"),
                    )
                }
            },
        )
    }

    @Test
    fun `a draft the catalog rejects is still a local problem that does not stop the change`() {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenThrow(AuditDraftInvalidException(listOf("unknown event")))
        val trail = SchemaAssignmentAuditTrail(recorder)
        val assignment = SchemaAssignment().apply {
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            schemaVersionId = UUID.randomUUID()
        }

        assertDoesNotThrow {
            trail.schemaAssigned(
                assignment,
                ScopeReference.Platform,
                FieldPrincipalProvenance(author),
                "Process record",
                0
            )
        }
    }
}
