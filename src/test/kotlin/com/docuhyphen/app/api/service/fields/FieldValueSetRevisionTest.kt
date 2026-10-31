package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.StoredAnswer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

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

        assertEquals(5, fixture.updatedSets.single().revision)
    }

    @Test
    fun `a save that stores nothing leaves the set where it was`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                SchemaAssignmentFieldsFixture.Question.NOTE to
                        StoredAnswer("Unchanged answer", author),
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

        assertEquals(2, fixture.updatedSets.single().revision)
        assertEquals("\"${fixture.savedSets.single().id}:2\"", assignment.etag)
    }
}
