package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * What a caller is handed when it reads the answers a resource holds: one entry per question the
 * schema asks, in the order the schema asks them, each naming the question by its stable identity
 * and carrying the answer in the canonical form of its type, wrapped in an envelope naming the
 * schema, the version of it in force, and the exact state the answers stand in.
 *
 * These are the guarantees a consumer builds against, so they are pinned here independently of
 * which class assembles them, and they hold for the projection a read returns and for the one a
 * save returns alike.
 */
class SchemaAssignmentProjectionCharacterizationTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `the projection carries one entry per question in the order the schema asks them`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val projection = requireNotNull(fixture.read())

        assertEquals(
            listOf(fixture.noteContractId, fixture.secondNoteContractId, fixture.optionsContractId),
            projection.fields.map { it.fieldContractId },
        )
    }

    @Test
    fun `each entry names its question by stable identity, contract label, and type`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val options = requireNotNull(fixture.read()).fields.single { it.fieldContractId == fixture.optionsContractId }

        assertEquals("process", options.namespace)
        assertEquals("recorded-options", options.fieldKey)
        assertEquals("Recorded options", options.label)
        assertEquals(FieldValueType.MULTI_SELECT, options.valueType)
    }

    @Test
    fun `each entry names the binding it answers, so a caller can pair it with the schema view`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val entries = requireNotNull(fixture.read()).fields

        assertTrue(entries.all { it.schemaFieldBindingId != null }, "every entry names its binding")
        assertEquals(entries.size, entries.mapNotNull { it.schemaFieldBindingId }.toSet().size)
    }

    @Test
    fun `a question with no stored answer is projected as empty and carries no value`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val note = requireNotNull(fixture.read()).fields.single { it.fieldContractId == fixture.noteContractId }

        assertTrue(note.isEmpty)
        assertEquals(JsonNull, note.value)
    }

    @Test
    fun `a stored answer is projected as answered and carries its canonical value`()
    {
        val fixture = SchemaAssignmentFieldsFixture(
            principal = author,
            rootAnswers = mapOf(
                SchemaAssignmentFieldsFixture.Question.NOTE to
                    SchemaAssignmentFieldsFixture.StoredAnswer("Recorded answer", author),
            ),
        )

        val note = requireNotNull(fixture.read()).fields.single { it.fieldContractId == fixture.noteContractId }

        assertFalse(note.isEmpty)
        assertEquals(JsonPrimitive("Recorded answer"), note.value)
    }

    @Test
    fun `a multi-select answer is projected as its option codes in the order the schema declares`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)
        fixture.save(listOf(fixture.options("third-option", "first-option")))

        val options = requireNotNull(fixture.read()).fields.single { it.fieldContractId == fixture.optionsContractId }

        // The order a caller chose in is not the order it reads back in: the codes are stored in the
        // order the schema offers them, so the same selection always projects identically.
        assertFalse(options.isEmpty)
        assertEquals(JsonArray(listOf(JsonPrimitive("first-option"), JsonPrimitive("third-option"))), options.value)
    }

    @Test
    fun `the envelope names the resource, its schema, and the version in force`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val projection = requireNotNull(fixture.read())

        assertEquals(fixture.assignmentId, projection.id)
        assertEquals(fixture.resourceType, projection.resourceType)
        assertEquals(fixture.resourceId, projection.resourceId)
        assertEquals(fixture.schemaDefinitionId, projection.schemaDefinitionId)
        assertEquals(fixture.schemaVersionId, projection.schemaVersionId)
        assertEquals("process-data", projection.schemaKey)
        assertEquals(fixture.schemaDisplayName, projection.displayName)
        assertEquals(1, projection.versionNumber)
    }

    @Test
    fun `the envelope carries the validator for the exact state of the answers it holds`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetRevision = 7)

        assertEquals("\"${fixture.rootValueSetId}:7\"", requireNotNull(fixture.read()).etag)
    }

    @Test
    fun `an assignment holding no set of answers is projected without a validator`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, rootSetExists = false)

        val projection = requireNotNull(fixture.read())

        assertNull(projection.etag)
        assertTrue(projection.fields.all { it.isEmpty }, "every question reads as unanswered")
    }

    @Test
    fun `the projection a save answers with matches the one a read answers with`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val saved = fixture.save(listOf(fixture.entry(fixture.noteContractId, "Recorded answer")))
        val read = requireNotNull(fixture.read())

        assertEquals(read.fields.map { it.fieldContractId }, saved.fields.map { it.fieldContractId })
        assertEquals(read.fields.map { it.value }, saved.fields.map { it.value })
        assertEquals(read.fields.map { it.isEmpty }, saved.fields.map { it.isEmpty })
        assertEquals(read.schemaKey, saved.schemaKey)
        assertEquals(read.versionNumber, saved.versionNumber)
    }
}
