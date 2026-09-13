package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.fields.FieldValueClearCommand
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FieldValueClearCommandTest
{
    @Test
    fun `explicit clearing removes a required current answer while preserving its earlier revision`()
    {
        val fixture = SchemaAssignmentFieldsFixture(requiredFields = true)
        val saved = fixture.save(listOf(fixture.entry(fixture.noteContractId, "Original process data")))
        val command = FieldValueClearCommand(fixture.resource, fixture.access, setOf(fixture.noteContractId),
            FieldValueSetRef.Root, FieldsPrecondition.ExpectedRevision(saved.etag!!))
        val cleared = fixture.service.clearValues(command)
        assertEquals(JsonNull, cleared.fields.single { it.fieldContractId == fixture.noteContractId }.value)
        assertEquals(JsonNull, fixture.read()!!.fields.single { it.fieldContractId == fixture.noteContractId }.value)
        val history = fixture.rootRevisions(fixture.noteContractId)
        assertEquals(2, history.size)
        assertEquals("Original process data", history.first().textValue)
        assertTrue(history.last().isCleared)
        assertEquals(fixture.principal.id, history.last().recordedByPrincipalId)
        assertTrue(fixture.deletedValues.isEmpty())
        assertThrows(FieldsPreconditionException::class.java) { fixture.service.clearValues(command) }
    }
}
