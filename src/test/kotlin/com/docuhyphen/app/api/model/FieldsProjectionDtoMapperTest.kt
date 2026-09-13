package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.service.fields.ResolvedFieldBinding
import com.docuhyphen.app.api.service.fields.ResolvedFieldValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Shaping gathered Fields records into the responses a consumer reads. These mappers hold no
 * collaborators and reach nothing, so every case is stated as records in and a response out.
 *
 * The distinctions worth pinning are the ones a consumer branches on: which record each name is
 * taken from, when an answer counts as empty, and how a value of each type is written.
 */
class FieldsProjectionDtoMapperTest
{
    // ── One question and its answer ──────────────────────────────────────────

    @Test
    fun `an entry takes its identity from the stable definition and its wording from the contract`()
    {
        val question = question(label = "Recorded note", key = "recorded-note")

        val dto = FieldValueDtoMapper.toDto(ResolvedFieldValue(question, stored = null))

        assertEquals(question.contract.id, dto.fieldContractId)
        assertEquals(question.binding.id, dto.schemaFieldBindingId)
        assertEquals("process", dto.namespace)
        assertEquals("recorded-note", dto.fieldKey)
        assertEquals("Recorded note", dto.label)
        assertEquals(FieldValueType.SHORT_TEXT, dto.valueType)
    }

    @Test
    fun `a question with no stored answer reads as empty and carries nothing`()
    {
        val dto = FieldValueDtoMapper.toDto(ResolvedFieldValue(question(), stored = null))

        assertTrue(dto.isEmpty)
        assertEquals(JsonNull, dto.value)
    }

    @Test
    fun `a stored answer saying nothing reads as empty just like one that was never given`()
    {
        val question = question()

        val dto = FieldValueDtoMapper.toDto(ResolvedFieldValue(question, storedText(question, text = null)))

        assertTrue(dto.isEmpty)
    }

    @Test
    fun `a stored answer reads as answered and carries its canonical text`()
    {
        val question = question()

        val dto = FieldValueDtoMapper.toDto(ResolvedFieldValue(question, storedText(question, "Recorded answer")))

        assertFalse(dto.isEmpty)
        assertEquals(JsonPrimitive("Recorded answer"), dto.value)
    }

    @Test
    fun `a stored choice carries its option codes and is empty when none were chosen`()
    {
        val question = question(type = FieldValueType.MULTI_SELECT)
        val stored = storedText(question, text = null)

        val chosen = FieldValueDtoMapper.toDto(
            ResolvedFieldValue(question, stored, listOf("first-option", "third-option")),
        )
        val none = FieldValueDtoMapper.toDto(ResolvedFieldValue(question, stored, emptyList()))

        assertFalse(chosen.isEmpty)
        assertEquals(JsonArray(listOf(JsonPrimitive("first-option"), JsonPrimitive("third-option"))), chosen.value)
        assertTrue(none.isEmpty)
    }

    @Test
    fun `a stored number is written at the scale its own contract configures`()
    {
        val question = question(type = FieldValueType.DECIMAL, constraints = """{"scale":2}""")
        val stored = storedText(question, text = null).apply { numberValue = BigDecimal("12.5000") }

        val dto = FieldValueDtoMapper.toDto(ResolvedFieldValue(question, stored))

        // Written as digits rather than as a JSON number, so its width survives the client.
        assertEquals(JsonPrimitive("12.50"), dto.value)
    }

    // ── One question as an editor is built from it ───────────────────────────

    @Test
    fun `a question description carries where it sits and how it must be answered`()
    {
        val question = question(label = "Recorded note", key = "recorded-note")
        question.binding.displayOrder = 4
        question.binding.section = "Recorded section"
        question.binding.isRequired = true
        question.binding.isReadOnly = true
        question.binding.defaultValueJson = "\"Configured answer\""
        question.contract.description = "What this question asks"
        question.contract.helpText = "How to answer it"

        val dto = SchemaFieldBindingDtoMapper.toDto(question)

        assertEquals(question.binding.id, dto.id)
        assertEquals(question.contract.id, dto.fieldContractId)
        assertEquals(question.definition.id, dto.fieldDefinitionId)
        assertEquals("recorded-note", dto.fieldKey)
        assertEquals("Recorded note", dto.label)
        assertEquals(4, dto.displayOrder)
        assertEquals("Recorded section", dto.section)
        assertTrue(dto.isRequired)
        assertTrue(dto.isReadOnly)
        assertEquals("\"Configured answer\"", dto.defaultValueJson)
        assertEquals(FieldDataClassification.INTERNAL, dto.visibility)
        assertEquals("What this question asks", dto.description)
        assertEquals("How to answer it", dto.helpText)
    }

    @Test
    fun `a question description carries the options its contract offers, in the order it offers them`()
    {
        val question = question(
            type = FieldValueType.MULTI_SELECT,
            options = """[{"code":"first-option","label":"First","order":0,"active":true},
                          {"code":"second-option","label":"Second","order":1,"active":true}]""",
        )

        val dto = SchemaFieldBindingDtoMapper.toDto(question)

        assertEquals(listOf("first-option", "second-option"), dto.options.map { it.code })
    }

    // ── The assignment envelope ──────────────────────────────────────────────

    @Test
    fun `the envelope names the resource, the schema governing it, and the version in force`()
    {
        val definition = definition()
        val version = version(definition, number = 3)
        val assignment = assignment(version)

        val dto = SchemaAssignmentDtoMapper.toDto(assignment, definition, version, emptyList(), valueSet())

        assertEquals(assignment.id, dto.id)
        assertEquals("EXCHANGE", dto.resourceType)
        assertEquals(assignment.resourceId, dto.resourceId)
        assertEquals(version.id, dto.schemaVersionId)
        assertEquals(definition.id, dto.schemaDefinitionId)
        assertEquals("process-data", dto.schemaKey)
        assertEquals("Process data", dto.displayName)
        assertEquals(3, dto.versionNumber)
        assertEquals(SchemaAssignmentSource.MANUAL, dto.assignmentSource)
        assertEquals(assignment.assignedAt, dto.assignedAt)
    }

    @Test
    fun `the envelope carries the entries it was given, in the order it was given them`()
    {
        val definition = definition()
        val version = version(definition)
        val first = ResolvedFieldValue(question(key = "recorded-note"), stored = null)
        val second = ResolvedFieldValue(question(key = "recorded-second-note"), stored = null)

        val dto = SchemaAssignmentDtoMapper.toDto(
            assignment(version), definition, version, listOf(first, second), valueSet(),
        )

        assertEquals(listOf("recorded-note", "recorded-second-note"), dto.fields.map { it.fieldKey })
    }

    @Test
    fun `the envelope describes each question it carries an entry for, in the same order`()
    {
        val definition = definition()
        val version = version(definition)
        val first = ResolvedFieldValue(question(label = "Recorded note", key = "recorded-note"), stored = null)
        val second = ResolvedFieldValue(
            question(label = "Second recorded note", key = "recorded-second-note"), stored = null,
        )

        val dto = SchemaAssignmentDtoMapper.toDto(
            assignment(version), definition, version, listOf(first, second), valueSet(),
        )

        assertEquals(listOf("recorded-note", "recorded-second-note"), dto.bindings.map { it.fieldKey })
        assertEquals(listOf("Recorded note", "Second recorded note"), dto.bindings.map { it.label })
        assertEquals(dto.fields.map { it.fieldContractId }, dto.bindings.map { it.fieldContractId })
    }

    @Test
    fun `the envelope validator names the set of answers and the state it stands in`()
    {
        val definition = definition()
        val version = version(definition)
        val set = valueSet(revision = 9)

        val dto = SchemaAssignmentDtoMapper.toDto(assignment(version), definition, version, emptyList(), set)

        assertEquals("\"${set.id}:9\"", dto.etag)
    }

    @Test
    fun `an assignment holding no set of answers carries no validator`()
    {
        val definition = definition()
        val version = version(definition)

        val dto = SchemaAssignmentDtoMapper.toDto(
            assignment(version), definition, version, emptyList(), valueSet = null,
        )

        assertNull(dto.etag)
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private fun question(
        label: String = "Recorded note",
        key: String = "recorded-note",
        type: FieldValueType = FieldValueType.SHORT_TEXT,
        constraints: String = "{}",
        options: String = "[]",
    ): ResolvedFieldBinding
    {
        val fieldDefinition = FieldDefinition().apply {
            id = UUID.randomUUID()
            namespace = "process"
            fieldKey = key
            status = FieldLifecycleStatus.PUBLISHED
        }
        val contract = FieldContract().apply {
            id = UUID.randomUUID()
            fieldDefinitionId = fieldDefinition.id
            valueType = type
            this.label = label
            constraintsJson = constraints
            optionsJson = options
        }
        val binding = SchemaFieldBinding().apply {
            id = UUID.randomUUID()
            schemaVersionId = UUID.randomUUID()
            fieldContractId = contract.id
            visibility = FieldDataClassification.INTERNAL
        }
        return ResolvedFieldBinding(binding, contract, fieldDefinition)
    }

    private fun storedText(question: ResolvedFieldBinding, text: String?) = FieldValue().apply {
        id = UUID.randomUUID()
        fieldValueSetId = UUID.randomUUID()
        schemaAssignmentId = UUID.randomUUID()
        fieldContractId = question.contract.id
        resourceType = "EXCHANGE"
        resourceId = UUID.randomUUID()
        valueType = question.contract.valueType
        textValue = text
    }

    private fun assignment(version: SchemaVersion) = SchemaAssignment().apply {
        id = UUID.randomUUID()
        resourceType = "EXCHANGE"
        resourceId = UUID.randomUUID()
        schemaVersionId = version.id
        assignmentSource = SchemaAssignmentSource.MANUAL
        assignedAt = Timestamp.from(Instant.parse("2026-01-02T03:04:05Z"))
    }

    private fun definition() = SchemaDefinition().apply {
        id = UUID.randomUUID()
        scopeKind = FieldScopeKind.ORGANIZATION
        namespace = "process"
        schemaKey = "process-data"
        displayName = "Process data"
        targetResourceType = "EXCHANGE"
        status = FieldLifecycleStatus.PUBLISHED
    }

    private fun version(definition: SchemaDefinition, number: Int = 1) = SchemaVersion().apply {
        id = UUID.randomUUID()
        schemaDefinitionId = definition.id
        versionNumber = number
        status = FieldLifecycleStatus.PUBLISHED
    }

    private fun valueSet(revision: Long = 1) = FieldValueSet().apply {
        id = UUID.randomUUID()
        schemaAssignmentId = UUID.randomUUID()
        setKind = FieldValueSetKind.ROOT
        this.revision = revision
    }
}
