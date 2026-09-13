package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A set of answers counts the changes it has been through, and that count is what a client holds
 * onto to say which state of the set it edited. The count only moves forward, it moves by exactly
 * one for each save that stores something, and a save that stores nothing leaves it where it was,
 * so two clients holding the same count are looking at the same answers.
 *
 * The validator a client sends back names both the set and its count, so a validator taken from one
 * set can never be spent against another.
 */
class FieldValueSetRevisionTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `a save that stores a change advances the set by one`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "First answer")))

        assertEquals(5, fixture.updatedSets.single().revision)
    }

    @Test
    fun `several changes saved together advance the set once`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 4)

        fixture.save(listOf(
                fixture.entry(fixture.noteContractId, "First answer"),
                fixture.entry(fixture.secondNoteContractId, "Second answer"),
                fixture.options("first-option"),
            ),
        )

        // One save is one change to the set, however many of its questions that save answered.
        assertEquals(5, fixture.updatedSets.single().revision)
    }

    @Test
    fun `a save that stores nothing leaves the set where it was`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                SchemaAssignmentFieldsFixture.Question.NOTE to
                    SchemaAssignmentFieldsFixture.StoredAnswer("Unchanged answer", author),
            ),
            rootSetRevision = 4,
        )

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Unchanged answer")))

        assertTrue(fixture.updatedSets.isEmpty(), "Nothing was stored, so the set did not change")
        assertEquals(4, fixture.rootSet.revision)
    }

    @Test
    fun `consecutive saves advance the set one step at a time`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 1)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "First answer")))
        fixture.save(listOf(fixture.entry(fixture.noteContractId, "Second answer")))

        assertEquals(listOf(2L, 3L), fixture.updatedSetRevisions)
    }

    @Test
    fun `a read carries the validator of the set as it currently stands`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 7)

        val assignment = requireNotNull(fixture.read())

        assertEquals(FieldValueSetETag.of(fixture.rootSet), assignment.etag)
        assertEquals("\"${fixture.rootValueSetId}:7\"", assignment.etag)
    }

    @Test
    fun `a successful save carries the validator of the advanced set`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 7)
        val before = requireNotNull(fixture.read()).etag

        val saved = fixture.save(listOf(fixture.entry(fixture.noteContractId, "First answer")))

        assertEquals("\"${fixture.rootValueSetId}:8\"", saved.etag)
        assertNotEquals(before, saved.etag, "The answers changed, so the validator must not match the old one")
    }

    @Test
    fun `a validator names its own set`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 7)
        val other = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 7)

        assertNotEquals(
            FieldValueSetETag.of(fixture.rootSet), FieldValueSetETag.of(other.rootSet),
            "Two sets at the same count are still two different sets",
        )
    }

    @Test
    fun `a freshly assigned schema starts its set at the first revision`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, assigned = false, rootSetExists = false)

        val assignment = fixture.assign()

        val created = fixture.savedSets.single()
        assertEquals(1, created.revision)
        assertEquals(FieldValueSetETag.of(created), assignment.etag)
    }

    @Test
    fun `materializing a configured default advances the fresh set once`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            assigned = false,
            rootSetExists = false,
            configuredDefault = "\"Configured answer\"",
        )

        val assignment = fixture.assign()

        // The default is a stored answer like any other, so the set it landed in has changed once.
        assertEquals(2, fixture.updatedSets.single().revision)
        assertEquals("\"${fixture.savedSets.single().id}:2\"", assignment.etag)
    }
}
