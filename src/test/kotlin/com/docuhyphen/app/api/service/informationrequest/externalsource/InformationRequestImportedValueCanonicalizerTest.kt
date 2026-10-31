package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestValueComparison
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.fields.FieldValueValidator
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class InformationRequestImportedValueCanonicalizerTest
{
    private val requestId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()
    private val valueSetId = UUID.randomUUID()
    private val bindings: InformationRequestTemplateRequirementBindingRepository = mock()
    private val requirementRevisions: InformationRequestRequirementRevisionRepository = mock()
    private val fieldsReader: FieldsReadFacade = mock()
    private val revisions: FieldValueRevisionQueryService = mock()
    private val registry = FieldTypeRegistry()
    private val currentRevisions = mutableMapOf<UUID, UUID>()
    private val canonicalizer = InformationRequestImportedValueCanonicalizer(
        bindings, requirementRevisions, fieldsReader, FieldValueValidator(registry), registry, revisions,
    )

    @Test
    fun `a value for a Requirement that collects a Field takes that Field's canonical form`()
    {
        val amount = fieldRequirement(FieldValueType.DECIMAL, constraints = """{"scale":2}""")
        assertEquals("\"42.50\"", canonicalizer.canonicalize(amount, FieldValueType.DECIMAL, JsonPrimitive(42.5)))

        val moment = fieldRequirement(FieldValueType.DATE_TIME)
        assertEquals(
            "\"2026-09-01T10:00:00Z\"",
            canonicalizer.canonicalize(moment, FieldValueType.DATE_TIME, JsonPrimitive("2026-09-01T10:00:00Z")),
        )

        val choices = fieldRequirement(FieldValueType.MULTI_SELECT, options = OPTIONS)
        assertEquals(
            "[\"first\",\"second\"]",
            canonicalizer.canonicalize(
                choices,
                FieldValueType.MULTI_SELECT,
                JsonArray(listOf(JsonPrimitive("second"), JsonPrimitive("first")))
            ),
        )
    }

    @Test
    fun `a value that does not fit the collected Field is refused`()
    {
        val amount = fieldRequirement(FieldValueType.DECIMAL, constraints = """{"scale":2}""")
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(amount, FieldValueType.SHORT_TEXT, JsonPrimitive("42.50"))
        }
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(amount, FieldValueType.DECIMAL, JsonPrimitive("not a number"))
        }
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(amount, FieldValueType.DECIMAL, JsonPrimitive(42.505))
        }
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(amount, FieldValueType.DECIMAL, JsonNull)
        }
        val choices = fieldRequirement(FieldValueType.SINGLE_SELECT, options = OPTIONS)
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(choices, FieldValueType.SINGLE_SELECT, JsonPrimitive("unlisted"))
        }
    }

    @Test
    fun `a value for a Requirement that collects no Field is a validated scalar`()
    {
        val record = requirement(collected = null)
        assertEquals(
            "\"2026-09-01\"",
            canonicalizer.canonicalize(record, FieldValueType.DATE, JsonPrimitive("2026-09-01"))
        )
        assertEquals("true", canonicalizer.canonicalize(record, FieldValueType.BOOLEAN, JsonPrimitive(true)))
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(record, FieldValueType.DATE, JsonPrimitive("first of September"))
        }
        listOf(FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT).forEach { type ->
            assertThrows<InformationRequestCommandRequestException> {
                canonicalizer.canonicalize(record, type, JsonPrimitive("first"))
            }
        }
        assertThrows<InformationRequestCommandRequestException> {
            canonicalizer.canonicalize(record, FieldValueType.SHORT_TEXT, JsonPrimitive(" "))
        }
    }

    @Test
    fun `a Requirement whose collected Field cannot be resolved is not silently treated as a plain record`()
    {
        val requirement = requirement(collected = UUID.randomUUID())
        whenever(fieldsReader.assignmentFor(FieldsResourceRef("INFORMATION_REQUEST", requestId))).thenReturn(null)
        assertThrows<IllegalStateException> {
            canonicalizer.canonicalize(requirement, FieldValueType.SHORT_TEXT, JsonPrimitive("value"))
        }
    }

    @Test
    fun `comparison is by meaning, not by spelling`()
    {
        val amount = fieldRequirement(FieldValueType.DECIMAL, constraints = """{"scale":2}""")
        answer(CanonicalFieldValue(FieldValueType.DECIMAL, isEmpty = false, numberValue = BigDecimal("42.500")))
        assertEquals(
            InformationRequestValueComparison.Matches,
            canonicalizer.compare(amount, imported(amount, FieldValueType.DECIMAL, "\"42.50\""), response(amount))
        )
        answer(CanonicalFieldValue(FieldValueType.DECIMAL, isEmpty = false, numberValue = BigDecimal("42.49")))
        assertEquals(
            InformationRequestValueComparison.Differs("\"42.49\""),
            canonicalizer.compare(amount, imported(amount, FieldValueType.DECIMAL, "\"42.50\""), response(amount)),
        )

        val moment = fieldRequirement(FieldValueType.DATE_TIME)
        answer(
            CanonicalFieldValue(
                FieldValueType.DATE_TIME,
                isEmpty = false,
                datetimeValue = Instant.parse("2026-09-01T10:00:00Z"),
                datetimeOffsetMinutes = 120
            )
        )
        assertEquals(
            InformationRequestValueComparison.Matches,
            canonicalizer.compare(
                moment,
                imported(moment, FieldValueType.DATE_TIME, "\"2026-09-01T10:00:00Z\""),
                response(moment)
            ),
        )

        val choices = fieldRequirement(FieldValueType.MULTI_SELECT, options = OPTIONS)
        answer(
            CanonicalFieldValue(
                FieldValueType.MULTI_SELECT,
                isEmpty = false,
                selectionCodes = listOf("first", "second")
            )
        )
        assertEquals(
            InformationRequestValueComparison.Matches,
            canonicalizer.compare(
                choices,
                imported(choices, FieldValueType.MULTI_SELECT, "[\"second\",\"first\"]"),
                response(choices)
            ),
        )
    }

    @Test
    fun `comparison reports a missing answer and a Requirement without a Field`()
    {
        val text = fieldRequirement(FieldValueType.SHORT_TEXT)
        val value = imported(text, FieldValueType.SHORT_TEXT, "\"recorded\"")
        assertEquals(InformationRequestValueComparison.NoAnswer, canonicalizer.compare(text, value, null))
        assertEquals(
            InformationRequestValueComparison.NoAnswer,
            canonicalizer.compare(text, value, response(text).apply { fieldValueSetId = null })
        )
        whenever(revisions.latestRevision(any(), eq(valueSetId), any())).thenReturn(null)
        assertEquals(InformationRequestValueComparison.NoAnswer, canonicalizer.compare(text, value, response(text)))
        answer(CanonicalFieldValue.empty(FieldValueType.SHORT_TEXT))
        assertEquals(InformationRequestValueComparison.NoAnswer, canonicalizer.compare(text, value, response(text)))

        val record = requirement(collected = null)
        assertEquals(
            InformationRequestValueComparison.NotComparable,
            canonicalizer.compare(record, imported(record, FieldValueType.DATE, "\"2026-09-01\""), response(record)),
        )
    }

    @Test
    fun `a value recorded while its Requirement collected another Field is kept out of the comparison`() {
        val count = fieldRequirement(FieldValueType.INTEGER)
        answer(CanonicalFieldValue(FieldValueType.INTEGER, isEmpty = false, numberValue = BigDecimal("7")))
        val earlier = imported(
            count,
            FieldValueType.SHORT_TEXT,
            "\"Original source text\"",
            revisionCollecting(UUID.randomUUID())
        )

        assertEquals(
            InformationRequestValueComparison.FieldChanged,
            canonicalizer.compare(count, earlier, response(count))
        )
        assertFalse(canonicalizer.comparesWithAnswer(count, earlier))
        verify(revisions, never()).latestRevision(any(), any(), any())
    }

    @Test
    fun `a value recorded for another Field of the same type is never read as an answer to the current Field`() {
        val summary = fieldRequirement(FieldValueType.SHORT_TEXT)
        answer(CanonicalFieldValue(FieldValueType.SHORT_TEXT, isEmpty = false, textValue = "recorded"))
        val earlier =
            imported(summary, FieldValueType.SHORT_TEXT, "\"recorded\"", revisionCollecting(UUID.randomUUID()))

        assertEquals(
            InformationRequestValueComparison.FieldChanged,
            canonicalizer.compare(summary, earlier, response(summary))
        )
        assertFalse(canonicalizer.comparesWithAnswer(summary, earlier))
    }

    @Test
    fun `a value recorded while its Requirement collected no Field is not compared once it collects one`() {
        val summary = fieldRequirement(FieldValueType.SHORT_TEXT)
        answer(CanonicalFieldValue(FieldValueType.SHORT_TEXT, isEmpty = false, textValue = "recorded"))
        val earlier = imported(summary, FieldValueType.SHORT_TEXT, "\"recorded\"", revisionCollecting(null))

        assertEquals(
            InformationRequestValueComparison.FieldChanged,
            canonicalizer.compare(summary, earlier, response(summary))
        )
    }

    @Test
    fun `a value of another type than the collected Field is reported as recorded for another Field`() {
        val count = fieldRequirement(FieldValueType.INTEGER)
        answer(CanonicalFieldValue(FieldValueType.INTEGER, isEmpty = false, numberValue = BigDecimal("7")))
        val mismatched = imported(count, FieldValueType.SHORT_TEXT, "\"seven\"")

        assertEquals(
            InformationRequestValueComparison.FieldChanged,
            canonicalizer.compare(count, mismatched, response(count))
        )
        assertFalse(canonicalizer.comparesWithAnswer(count, mismatched))
    }

    @Test
    fun `a value that no longer fits the collected Field is not comparable instead of failing the comparison`() {
        val choice = fieldRequirement(FieldValueType.SINGLE_SELECT, options = OPTIONS)
        answer(CanonicalFieldValue(FieldValueType.SINGLE_SELECT, isEmpty = false, selectionCodes = listOf("first")))
        val unlisted = imported(choice, FieldValueType.SINGLE_SELECT, "\"withdrawn-option\"")

        assertEquals(
            InformationRequestValueComparison.NotComparable,
            canonicalizer.compare(choice, unlisted, response(choice))
        )
        assertFalse(canonicalizer.comparesWithAnswer(choice, unlisted))
        assertTrue(
            canonicalizer.comparesWithAnswer(
                choice,
                imported(choice, FieldValueType.SINGLE_SELECT, "\"first\"")
            )
        )
    }

    private fun fieldRequirement(
        type: FieldValueType,
        constraints: String = "{}",
        options: String = "[]"
    ): InformationRequestRequirement
    {
        val fieldDefinitionId = UUID.randomUUID()
        val contractId = UUID.randomUUID()
        val requirement = requirement(collected = fieldDefinitionId)
        whenever(fieldsReader.assignmentFor(FieldsResourceRef("INFORMATION_REQUEST", requestId))).thenReturn(
            SchemaAssignment().apply {
                resourceType = "INFORMATION_REQUEST"
                resourceId = requestId
                this.schemaVersionId = this@InformationRequestImportedValueCanonicalizerTest.schemaVersionId
            },
        )
        whenever(fieldsReader.schemaBindings(schemaVersionId)).thenReturn(
            listOf(
                SchemaFieldBinding().apply {
                    schemaVersionId = this@InformationRequestImportedValueCanonicalizerTest.schemaVersionId
                    this.fieldDefinitionId = fieldDefinitionId
                    fieldContractId = contractId
                },
            ),
        )
        whenever(fieldsReader.contract(contractId)).thenReturn(
            FieldContract().apply {
                id = contractId
                this.fieldDefinitionId = fieldDefinitionId
                valueType = type
                label = "Synthetic Field"
                constraintsJson = constraints
                optionsJson = options
            },
        )
        return requirement
    }

    private fun requirement(collected: UUID?): InformationRequestRequirement
    {
        val bindingId = binding(collected)
        return InformationRequestRequirement().apply {
            informationRequestId = requestId
            sourceTemplateVersionId = UUID.randomUUID()
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = bindingId
            occurrencePath = "root"
        }.also { currentRevisions[it.id] = revisionOf(it.id, bindingId) }
    }

    private fun revisionCollecting(collected: UUID?): UUID = revisionOf(UUID.randomUUID(), binding(collected))

    private fun binding(collected: UUID?): UUID {
        val bindingId = UUID.randomUUID()
        whenever(bindings.findById(bindingId)).thenReturn(
            InformationRequestTemplateRequirementBinding().apply {
                id = bindingId
                collectedFieldDefinitionId = collected
            },
        )
        return bindingId
    }

    private fun revisionOf(requirementId: UUID, bindingId: UUID): UUID {
        val revision = InformationRequestRequirementRevision().apply {
            informationRequestRequirementId = requirementId
            informationRequestId = requestId
            sourceTemplateBindingId = bindingId
        }
        whenever(requirementRevisions.findById(revision.id)).thenReturn(revision)
        return revision.id
    }

    private fun imported(
        requirement: InformationRequestRequirement,
        type: FieldValueType,
        canonical: String,
        recordedUnder: UUID = currentRevisions.getValue(requirement.id),
    ) =
        InformationRequestImportedValue().apply {
            informationRequestId = requestId
            informationRequestRequirementId = requirement.id
            requirementRevisionId = recordedUnder
            resultKey = "recorded-value"
            valueType = type
            canonicalValue = canonical
        }

    private fun response(requirement: InformationRequestRequirement) = InformationRequestResponse().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirement.id
        requirementRevisionId = UUID.randomUUID()
        occurrencePath = "root"
        fieldValueSetId = valueSetId
        responseRevision = 3
    }

    private fun answer(value: CanonicalFieldValue)
    {
        val revision = FieldValueRevision().apply { id = UUID.randomUUID() }
        whenever(
            revisions.latestRevision(
                eq(FieldsResourceRef("INFORMATION_REQUEST", requestId)),
                eq(valueSetId),
                any()
            )
        ).thenReturn(revision)
        whenever(revisions.canonicalValueOf(revision.id)).thenReturn(value)
    }

    private companion object
    {
        const val OPTIONS = """[{"code":"first","label":"First"},{"code":"second","label":"Second"}]"""
    }
}
