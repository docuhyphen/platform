package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture.Question
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture.StoredAnswer
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A stored answer is overwritten in place, so on its own it can only say what is true now. Each
 * recorded change is therefore kept beside it as an immutable revision, numbered within its Value
 * Set and question, so any later record can name the exact answer it relied on and clearing an answer
 * is recorded rather than erased.
 *
 * The client posts every editable field on each save, so a repeat that stores nothing new must not
 * become a revision; otherwise the history fills with entries that record nothing having happened.
 */
class FieldValueRevisionHistoryTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `storing an answer records its first revision`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "First answer")))

        val revision = fixture.rootRevisions(fixture.noteContractId).single()
        assertEquals(1, revision.revisionNumber)
        assertEquals(fixture.rootValueSetId, revision.fieldValueSetId)
        assertEquals(fixture.assignmentId, revision.schemaAssignmentId)
        assertEquals(fixture.noteContractId, revision.fieldContractId)
        assertEquals("First answer", revision.textValue)
        assertEquals(FieldValueType.SHORT_TEXT, revision.valueType)
        assertEquals(FieldValueProvenance.USER, revision.provenance)
        assertEquals(PrincipalKind.USER, revision.recordedByPrincipalKind)
        assertEquals(author.id, revision.recordedByPrincipalId)
        assertFalse(revision.isCleared)
        assertEquals(
            requireNotNull(fixture.rootAnswer(fixture.noteContractId)).id, revision.fieldValueId,
            "A revision names the answer it records",
        )
    }

    @Test
    fun `changing an answer appends the next revision and leaves the earlier one intact`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                Question.NOTE to StoredAnswer("Earlier answer", author),
            ),
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Later answer")))
        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Latest answer")))

        val revisions = fixture.rootRevisions(fixture.noteContractId)
        assertEquals(listOf(1, 2), revisions.map { it.revisionNumber })
        assertEquals(listOf("Later answer", "Latest answer"), revisions.map { it.textValue })
    }

    @Test
    fun `revisions are numbered within the value set and question`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer of the resource")))
        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Second answer of the resource")))

        assertEquals(
            listOf(1, 2), fixture.rootRevisions(fixture.noteContractId).map { it.revisionNumber },
            "Numbering counts within the set and question rather than across the assignment",
        )
        assertTrue(
            fixture.savedRevisions.none { it.fieldValueSetId == fixture.occurrenceValueSetId },
            "A write to the resource's own answers must not touch a repetition's history",
        )
    }

    @Test
    fun `rewriting an answer with what it already says records nothing`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Unchanged answer")))
        val storedAfterFirstWrite = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        val updatedAtAfterFirstWrite = storedAfterFirstWrite.updatedAt

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Unchanged answer")))

        assertEquals(
            1, fixture.rootRevisions(fixture.noteContractId).size,
            "A repeat that stores nothing new is not a change",
        )
        assertEquals(
            updatedAtAfterFirstWrite,
            requireNotNull(fixture.rootAnswer(fixture.noteContractId)).updatedAt,
            "The answer's own timestamp must not move when nothing changed",
        )
    }

    @Test
    fun `the same answer left by a different principal is a change`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = PrincipalRef.participant(UUID.randomUUID()),
            rootAnswers = mapOf(
                Question.NOTE to StoredAnswer("Standing answer", author),
            ),
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Standing answer")))

        val revision = fixture.rootRevisions(fixture.noteContractId).single()
        assertEquals(
            PrincipalKind.PARTICIPANT, revision.recordedByPrincipalKind,
            "Who stands behind the answer changed, so the change is recorded",
        )
    }

    @Test
    fun `clearing an answer records a revision that holds no value`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                Question.NOTE to StoredAnswer("Answer to clear", author),
            ),
        )

        fixture.save(listOf(FieldValueEntry(fixture.noteContractId, JsonNull)))

        val revision = fixture.rootRevisions(fixture.noteContractId).single()
        assertTrue(revision.isCleared, "Clearing an answer is recorded rather than erased")
        assertNull(revision.textValue)
    }

    @Test
    fun `the chosen options of a select answer belong to the revision that recorded them`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.options("first-option", "second-option")))
        fixture.save(listOf(fixture.options("third-option")))

        val revisions = fixture.rootRevisions(fixture.optionsContractId)
        assertEquals(2, revisions.size)
        assertEquals(listOf("first-option", "second-option"), fixture.revisionOptionCodes(revisions[0]))
        assertEquals(
            listOf("third-option"), fixture.revisionOptionCodes(revisions[1]),
            "A later choice must not rewrite what the earlier revision recorded",
        )
    }

    @Test
    fun `repeating a selection records nothing further`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.options("first-option", "second-option")))
        fixture.save(listOf(fixture.options("first-option", "second-option")))

        assertEquals(1, fixture.rootRevisions(fixture.optionsContractId).size)
    }

    @Test
    fun `a stored change advances the value set that holds the answer`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer that moves the set")))

        val advanced = fixture.updatedSets.single()
        assertEquals(fixture.rootValueSetId, advanced.id)
        assertEquals(
            requireNotNull(fixture.rootAnswer(fixture.noteContractId)).updatedAt, advanced.updatedAt,
            "The set's timestamp follows its youngest answer",
        )
    }

    @Test
    fun `removing a schema keeps the recorded revisions`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                Question.NOTE to StoredAnswer("Answer to remove", author),
            ),
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Answer before removal")))
        val recorded = fixture.savedRevisions.toList()

        fixture.unassign()

        assertTrue(fixture.deletedValues.isNotEmpty(), "The live answers are removed with the assignment")
        assertEquals(
            recorded, fixture.savedRevisions,
            "What a resource once answered stays part of the record after its Schema is removed",
        )
    }
}
