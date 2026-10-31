package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.*

class InformationRequestStructuredResponseValidationServiceTest
{
    @Test
    fun `custom validators can refuse every structured response validation kind, one problem per issue`()
    {
        val kinds = InformationRequestStructuredResponseValidationKind.entries
        val requirementIds = kinds.associateWith { UUID.randomUUID() }
        val service = InformationRequestStructuredResponseValidationService(
            listOf(validator(InformationRequestStructuredResponseValidationStage.entries.toSet()) {
                kinds.map { kind ->
                    InformationRequestStructuredResponseValidationIssue(
                        kind = kind,
                        message = "Rejected ${kind.name}",
                        requirementId = requirementIds.getValue(kind),
                        occurrencePath = "entries[${kind.ordinal}]",
                    )
                }
            }),
        )

        val failure = assertThrows<InformationRequestItemRefusalException> {
            service.validate(context(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE))
        }

        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, failure.reasonCode)
        assertEquals(kinds, failure.problems.map { it.validationKind })
        kinds.forEach { kind ->
            val problem = failure.problems.single { it.validationKind == kind }
            assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, problem.code)
            assertEquals(requirementIds.getValue(kind), problem.requirementId)
            assertEquals("entries[${kind.ordinal}]", problem.occurrencePath)
            assertEquals("Rejected ${kind.name}", problem.message)
            assertFalse(failure.message.contains(kind.name))
        }
    }

    @Test
    fun `validators run only at the stages they declare and report without throwing through evaluate`() {
        val saveOnly = validator(setOf(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)) {
            listOf(issue("Saved entries repeat"))
        }
        val submissionOnly = validator(setOf(InformationRequestStructuredResponseValidationStage.SUBMISSION)) {
            listOf(issue("Covered periods leave a gap"))
        }
        val service = InformationRequestStructuredResponseValidationService(listOf(saveOnly, submissionOnly))

        assertTrue(service.appliesTo(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE))
        assertFalse(service.appliesTo(InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE))
        assertEquals(
            listOf("Saved entries repeat"),
            service.evaluate(context(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE))
                .map { it.message },
        )
        assertEquals(
            listOf("Covered periods leave a gap"),
            service.evaluate(context(InformationRequestStructuredResponseValidationStage.SUBMISSION))
                .map { it.message },
        )
        assertDoesNotThrow { service.validate(context(InformationRequestStructuredResponseValidationStage.OCCURRENCE_CHANGE)) }
    }

    @Test
    fun `an issue naming a Requirement the caller may not see is counted but never described`() {
        val visible = UUID.randomUUID()
        val withheld = UUID.randomUUID()
        val service = InformationRequestStructuredResponseValidationService(
            listOf(validator(setOf(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)) {
                listOf(
                    issue("Visible entry repeats", visible),
                    issue("Withheld entry repeats", withheld),
                    issue("Entries overlap in time"),
                )
            }),
        )

        val failure = assertThrows<InformationRequestItemRefusalException> {
            service.validate(context(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)) { it == visible }
        }

        assertEquals(listOf(visible, null), failure.problems.map { it.requirementId })
        assertEquals(1, failure.undisclosedProblemCount)
        assertFalse(failure.problems.any { it.message.contains("Withheld") })
    }

    @Test
    fun `a patch naming one Requirement or one Field twice is refused with a duplicate problem for each repeat`() {
        val requirementId = UUID.randomUUID()
        val fieldContractId = UUID.randomUUID()
        val service = InformationRequestStructuredResponseValidationService(emptyList())

        val failure = assertThrows<InformationRequestItemRefusalException> {
            service.requireDistinctPatchTargets(
                listOf(
                    InformationRequestResponsePatch(
                        requirementId = requirementId,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(
                                FieldValueEntry(fieldContractId, JsonPrimitive("First")),
                                FieldValueEntry(fieldContractId, JsonPrimitive("Second")),
                            ),
                            precondition = FieldsPrecondition.Unconditioned,
                        ),
                    ),
                    InformationRequestResponsePatch(requirementId = requirementId),
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, failure.reasonCode)
        assertEquals(2, failure.problems.size)
        assertTrue(failure.problems.all { it.validationKind == InformationRequestStructuredResponseValidationKind.DUPLICATE })
        assertTrue(failure.problems.all { it.requirementId == requirementId })
        assertEquals(listOf(null, fieldContractId), failure.problems.map { it.fieldContractId })
    }

    private fun validator(
        declaredStages: Set<InformationRequestStructuredResponseValidationStage>,
        issues: () -> List<InformationRequestStructuredResponseValidationIssue>,
    ) = object : InformationRequestStructuredResponseValidator {
        override val stages = declaredStages

        override fun validate(context: InformationRequestStructuredResponseValidationContext) = issues()
    }

    private fun issue(message: String, requirementId: UUID? = null) =
        InformationRequestStructuredResponseValidationIssue(
            kind = InformationRequestStructuredResponseValidationKind.CROSS_ROW,
            message = message,
            requirementId = requirementId,
        )

    private fun context(stage: InformationRequestStructuredResponseValidationStage) =
        InformationRequestStructuredResponseValidationContext(
            stage = stage,
            request = InformationRequest().apply { id = UUID.randomUUID() },
            requirementsById = emptyMap(),
        )
}
