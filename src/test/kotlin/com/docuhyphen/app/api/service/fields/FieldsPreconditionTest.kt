package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.StoredAnswer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.inOrder
import java.util.*

class FieldsPreconditionTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `a save that names the state it read is accepted`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)
        val read = requireNotNull(fixture.read()).etag

        val saved = fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer conditioned on what was read")),
            precondition = FieldsPrecondition.ExpectedRevision(requireNotNull(read)),
        )

        assertEquals("\"${fixture.rootValueSetId}:5\"", saved.etag)
        assertNotEquals(read, saved.etag)
    }

    @Test
    fun `a save that names a state the answers have moved past is refused as stale`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)
        val read = requireNotNull(requireNotNull(fixture.read()).etag)
        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer from the first caller")))

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.save(
                listOf(fixture.entry(fixture.secondNoteContractId, "Answer from the caller who was late")),
                precondition = FieldsPrecondition.ExpectedRevision(read),
            )
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals("FIELDS_PRECONDITION_STALE", failure.reasonCode)
        assertEquals(
            "\"${fixture.rootValueSetId}:5\"", failure.currentETag,
            "The refusal hands back the state that is current so the caller can look again",
        )
        assertEquals(
            null, fixture.rootAnswer(fixture.secondNoteContractId),
            "A refused save stores nothing",
        )
    }

    @Test
    fun `a save required to name a state and naming none is refused as missing`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.save(
                listOf(fixture.entry(fixture.noteContractId, "Answer with nothing stated")),
                precondition = FieldsPrecondition.Absent,
            )
        }

        assertEquals(FieldsPreconditionException.Kind.REQUIRED, failure.kind)
        assertEquals("FIELDS_PRECONDITION_REQUIRED", failure.reasonCode)
        assertNotEquals(
            failure.reasonCode, FieldsPreconditionException.Kind.STALE.reasonCode,
            "A client can tell the two refusals apart without reading the message",
        )
        assertTrue(fixture.storedByValueSet.values.all { it.isEmpty() }, "A refused save stores nothing")
    }

    @Test
    fun `a save that names no state and need not is still accepted`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)

        val saved = fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer from an older client")))

        assertEquals("\"${fixture.rootValueSetId}:5\"", saved.etag)
    }

    @Test
    fun `a state read from one set cannot be spent against another`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)
        val rootState = requireNotNull(requireNotNull(fixture.read()).etag)

        assertThrows<FieldsPreconditionException> {
            fixture.save(
                listOf(fixture.entry(fixture.noteContractId, "Answer for a repetition")),
                precondition = FieldsPrecondition.ExpectedRevision(rootState),
                valueSet = FieldValueSetRef.Occurrence(SchemaAssignmentFieldsFixture.OCCURRENCE_PATH),
            )
        }
    }

    @Test
    fun `a save that stores nothing still has to name the state it read`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                SchemaAssignmentFieldsFixture.Question.NOTE to
                        StoredAnswer("Unchanged answer", author),
            ),
            rootSetRevision = 4,
        )

        assertThrows<FieldsPreconditionException> {
            fixture.save(
                listOf(fixture.entry(fixture.noteContractId, "Unchanged answer")),
                precondition = FieldsPrecondition.ExpectedRevision("\"${fixture.rootValueSetId}:1\""),
            )
        }

        assertEquals(4, fixture.rootSet.revision, "A refused save leaves the count where it was")
    }

    @Test
    fun `a save resolves the state it is judged against under a lock`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer that competes")))

        assertEquals(
            listOf(FieldValueSetRef.Root), fixture.lockedSets,
            "A save that decided against an unlocked read could be overtaken between deciding and storing",
        )
    }

    @Test
    fun `removing a Schema while naming the answers that were read is accepted`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)
        val read = requireNotNull(requireNotNull(fixture.read()).etag)

        fixture.unassign(precondition = FieldsPrecondition.ExpectedRevision(read))

        assertTrue(fixture.deletedSets.isNotEmpty(), "The assignment and its sets were removed")
    }

    @Test
    fun `removing a Schema while naming answers that have since changed is refused as stale`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)
        val read = requireNotNull(requireNotNull(fixture.read()).etag)
        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer recorded after the read")))

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.unassign(precondition = FieldsPrecondition.ExpectedRevision(read))
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals(FieldValueSetETag.of(fixture.rootSet), failure.currentETag)
        assertTrue(fixture.deletedSets.isEmpty(), "A refused removal removes nothing")
    }

    @Test
    fun `removing a Schema while naming the assignment identity rather than its answers is refused as stale`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.unassign(precondition = FieldsPrecondition.ExpectedRevision("\"${fixture.assignmentId}\""))
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertTrue(fixture.deletedSets.isEmpty(), "A refused removal removes nothing")
    }

    @Test
    fun `removing a Schema required to name the assignment and naming none is refused as missing`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.unassign(precondition = FieldsPrecondition.Absent)
        }

        assertEquals(FieldsPreconditionException.Kind.REQUIRED, failure.kind)
        assertTrue(fixture.deletedSets.isEmpty(), "A refused removal removes nothing")
    }

    @Test
    fun `choosing a Schema while naming a state no assignment stands in is refused as stale`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.assign(precondition = FieldsPrecondition.ExpectedRevision("\"${UUID.randomUUID()}\""))
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertTrue(fixture.savedAssignments.isEmpty(), "A refused choice assigns nothing")
    }

    @Test
    fun `choosing a Schema while stating that none is assigned yet is accepted`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        val assigned = fixture.assign(precondition = FieldsPrecondition.ExpectedAbsent)

        assertEquals(1, fixture.savedAssignments.size)
        assertEquals("\"${fixture.savedSets.single().id}:1\"", assigned.etag)
    }

    @Test
    fun `choosing a Schema while stating that none is assigned is refused as stale once one is`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 3)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.assign(precondition = FieldsPrecondition.ExpectedAbsent)
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals(
            "\"${fixture.rootValueSetId}:3\"", failure.currentETag,
            "The refusal names the assignment that now stands, so the caller can read it",
        )
        assertTrue(fixture.savedAssignments.isEmpty(), "A refused choice assigns nothing")
    }

    @Test
    fun `choosing a Schema for a resource that already has one is refused with a stable conflict code`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val failure = assertThrows<SchemaAssignmentConflictException> {
            fixture.assign(precondition = FieldsPrecondition.Unconditioned)
        }

        assertEquals("FIELDS_SCHEMA_ALREADY_ASSIGNED", failure.reasonCode)
        assertTrue(fixture.savedAssignments.isEmpty(), "A refused choice assigns nothing")
    }

    @Test
    fun `choosing a Schema takes the resource's lock before reading which Schema it has`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        fixture.assign(precondition = FieldsPrecondition.ExpectedAbsent)

        inOrder(fixture.assignmentRepository) {
            verify(fixture.assignmentRepository).lockResource(fixture.resourceType, fixture.resourceId)
            verify(fixture.assignmentRepository).findByResource(fixture.resourceType, fixture.resourceId)
        }
    }

    @Test
    fun `removing a Schema takes the resource's lock before reading which Schema it has`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.unassign(precondition = FieldsPrecondition.Unconditioned)

        inOrder(fixture.assignmentRepository) {
            verify(fixture.assignmentRepository).lockResource(fixture.resourceType, fixture.resourceId)
            verify(fixture.assignmentRepository).findByResource(fixture.resourceType, fixture.resourceId)
        }
    }

    @Test
    fun `assigning the version a resource is created with takes the same lock`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        fixture.assignForCreation()

        inOrder(fixture.assignmentRepository) {
            verify(fixture.assignmentRepository).lockResource(fixture.resourceType, fixture.resourceId)
            verify(fixture.assignmentRepository).findByResource(fixture.resourceType, fixture.resourceId)
        }
    }
}
