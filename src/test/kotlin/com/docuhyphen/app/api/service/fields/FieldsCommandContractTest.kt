package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Every Fields command says who is asking and which set of answers it addresses, and the engine
 * takes both from the command rather than working either out for itself.
 *
 * That is what lets one engine serve two arrivals. A registered caller and a recipient-bound caller
 * reach the same service through different doors, and the door each came through is the only thing
 * that knows how to identify them. A command that carried nothing but a resource id would force the
 * engine to guess, and it would guess with whatever the current request happens to hold.
 *
 * Addressing is the same idea applied to the answers themselves: a resource answers as itself in its
 * root set, and each repetition of a repeatable group answers in its own, so a command that names a
 * repetition must land there and nowhere else.
 */
class FieldsCommandContractTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    private fun contextFor(principal: PrincipalRef, sessionRef: String? = null) =
        FieldsAccessContext(principal, AuthorizationContext(sessionRef = sessionRef))

    @Test
    fun `a save records the caller its own command names`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)
        val participant = PrincipalRef.participant(UUID.randomUUID())

        fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer supplied by a participant")),
            access = contextFor(participant, sessionRef = "participant-session"),
        )

        val stored = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals(PrincipalKind.PARTICIPANT, stored.updatedByPrincipalKind)
        assertEquals(participant.id, stored.updatedByPrincipalId)
        assertEquals("participant-session", stored.updatedBySessionRef)
    }

    @Test
    fun `two callers reaching one engine are recorded as themselves`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)
        val first = PrincipalRef.user(UUID.randomUUID())
        val second = PrincipalRef.participant(UUID.randomUUID())

        fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer from the first caller")),
            access = contextFor(first),
        )
        fixture.save(
            listOf(fixture.entry(fixture.secondNoteContractId, "Answer from the second caller")),
            access = contextFor(second),
        )

        assertEquals(first.id, fixture.rootAnswer(fixture.noteContractId)?.updatedByPrincipalId)
        assertEquals(second.id, fixture.rootAnswer(fixture.secondNoteContractId)?.updatedByPrincipalId)
    }

    @Test
    fun `a read is authorized as the caller its own command names`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)
        val reader = PrincipalRef.user(UUID.randomUUID())
        val access = contextFor(reader, sessionRef = "reader-session")

        fixture.read(access = access)

        assertEquals(
            listOf(reader to access.authorization), fixture.viewAuthorizations,
            "The caller the command named is the caller the resource was asked about",
        )
    }

    @Test
    fun `a save addressed to a repetition stores into that repetition`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer for one repetition")),
            valueSet = FieldValueSetRef.Occurrence(SchemaAssignmentFieldsFixture.OCCURRENCE_PATH),
        )

        assertEquals(
            "Answer for one repetition",
            fixture.storedByValueSet[fixture.occurrenceValueSetId]?.get(fixture.noteContractId)?.textValue,
        )
        assertTrue(
            fixture.storedByValueSet[fixture.rootValueSetId].isNullOrEmpty(),
            "An answer given for a repetition is not an answer the resource gave as itself",
        )
    }

    @Test
    fun `a save addressed to a repetition advances only that repetition`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author, rootSetRevision = 4, occurrenceSetRevision = 2,
        )

        val saved = fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer for one repetition")),
            valueSet = FieldValueSetRef.Occurrence(SchemaAssignmentFieldsFixture.OCCURRENCE_PATH),
        )

        assertEquals(4, fixture.rootSet.revision, "The resource's own answers did not change")
        assertEquals(3, fixture.occurrenceSet.revision)
        assertEquals(FieldValueSetETag.of(fixture.occurrenceSet), saved.etag)
    }

    @Test
    fun `a read addressed to a repetition projects that repetition`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                SchemaAssignmentFieldsFixture.Question.NOTE to
                    SchemaAssignmentFieldsFixture.StoredAnswer("Answer of the resource", author),
            ),
        )
        fixture.save(
            listOf(fixture.entry(fixture.noteContractId, "Answer of one repetition")),
            valueSet = FieldValueSetRef.Occurrence(SchemaAssignmentFieldsFixture.OCCURRENCE_PATH),
        )

        val projection = requireNotNull(
            fixture.read(FieldValueSetRef.Occurrence(SchemaAssignmentFieldsFixture.OCCURRENCE_PATH)),
        )

        val note = projection.fields.single { it.fieldContractId == fixture.noteContractId }
        assertEquals("\"Answer of one repetition\"", note.value.toString())
        assertEquals(FieldValueSetETag.of(fixture.occurrenceSet), projection.etag)
    }

    @Test
    fun `a command naming a repetition the assignment does not hold is refused`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val failure = assertThrows<FieldValidationException> {
            fixture.save(
                listOf(fixture.entry(fixture.noteContractId, "Answer with nowhere to go")),
                valueSet = FieldValueSetRef.Occurrence("items[7]"),
            )
        }

        assertTrue(failure.message.contains("items[7]"), "The refusal names the repetition that is missing")
        assertTrue(
            fixture.storedByValueSet.values.all { it.isEmpty() },
            "A misaddressed answer must not fall back to another set",
        )
    }
}
