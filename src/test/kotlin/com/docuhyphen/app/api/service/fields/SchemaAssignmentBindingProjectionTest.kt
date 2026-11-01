package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SchemaAssignmentBindingProjectionTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `the projection describes every question it carries an answer for, in the same order`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val projection = requireNotNull(fixture.read())

        assertEquals(
            listOf(fixture.noteContractId, fixture.secondNoteContractId, fixture.optionsContractId),
            projection.bindings.map { it.fieldContractId },
        )
        assertEquals(projection.fields.map { it.fieldContractId }, projection.bindings.map { it.fieldContractId })
    }

    @Test
    fun `each description carries the wording, type, and placement an editor is built from`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val options = requireNotNull(fixture.read())
            .bindings.single { it.fieldContractId == fixture.optionsContractId }

        assertEquals("Recorded options", options.label)
        assertEquals(FieldValueType.MULTI_SELECT, options.valueType)
        assertEquals(2, options.displayOrder)
        assertFalse(options.isRequired)
        assertFalse(options.isReadOnly)
        assertEquals(SchemaAssignmentFieldsFixture.OPTION_CODES, options.options.map { it.code })
    }

    @Test
    fun `each description names its binding and the stable definition behind it`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val projection = requireNotNull(fixture.read())
        val note = projection.bindings.single { it.fieldContractId == fixture.noteContractId }

        assertEquals(fixture.noteDefinitionId, note.fieldDefinitionId)
        assertEquals("process", note.namespace)
        assertEquals("recorded-note", note.fieldKey)
        assertEquals(
            projection.fields.mapNotNull { it.schemaFieldBindingId },
            projection.bindings.map { it.id },
        )
    }

    @Test
    fun `the descriptions a save answers with match the ones a read answers with`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author)

        val saved = fixture.save(listOf(fixture.entry(fixture.noteContractId, "Recorded answer")))
        val read = requireNotNull(fixture.read())

        assertEquals(read.bindings.map { it.fieldContractId }, saved.bindings.map { it.fieldContractId })
        assertEquals(read.bindings.map { it.label }, saved.bindings.map { it.label })
        assertEquals(read.bindings.map { it.valueType }, saved.bindings.map { it.valueType })
    }

    @Test
    fun `a caller shown none of a schema's questions is described none of them`()
    {
        val fixture = SchemaAssignmentFieldsFixture(principal = author, externalCaller = true)

        val projection = requireNotNull(fixture.read())

        assertEquals(fixture.schemaDefinitionId, projection.schemaDefinitionId)
        assertTrue(projection.bindings.isEmpty(), "no question is described")
        assertTrue(projection.fields.isEmpty(), "no question is answered")
    }
}
