package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * A change says which state of the data it is changing, and the engine refuses it when the data has
 * moved on since.
 *
 * Two people editing the same answers is the ordinary case, not the exotic one: one screen left open
 * while another is used, one save arriving after a phone lost signal. Without a stated expectation
 * the later save simply overwrites the earlier one and nobody is told. With it, the later caller is
 * turned away holding the state that is now current, which is what they need to look again.
 *
 * The two refusals are kept apart on purpose. A caller that named nothing has a client that must
 * start naming it; a caller whose state moved on has to read again. Telling them apart by machine
 * code means a client can act on the difference rather than parse a sentence.
 */
class FieldsPreconditionTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    // ── Saving answers ────────────────────────────────────────────────────────

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
                    SchemaAssignmentFieldsFixture.StoredAnswer("Unchanged answer", author),
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

    // ── Choosing and removing a Schema ────────────────────────────────────────

    @Test
    fun `removing a Schema while naming the assignment that was read is accepted`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.unassign(
            precondition = FieldsPrecondition.ExpectedRevision("\"${fixture.assignmentId}\""),
        )

        assertTrue(fixture.deletedSets.isNotEmpty(), "The assignment and its sets were removed")
    }

    @Test
    fun `removing a Schema while naming an assignment that was replaced is refused as stale`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val failure = assertThrows<FieldsPreconditionException> {
            fixture.unassign(precondition = FieldsPrecondition.ExpectedRevision("\"${UUID.randomUUID()}\""))
        }

        assertEquals(FieldsPreconditionException.Kind.STALE, failure.kind)
        assertEquals("\"${fixture.assignmentId}\"", failure.currentETag)
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
}
