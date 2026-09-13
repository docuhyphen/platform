package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InformationRequestStructuredResponseValidationServiceTest
{
    @Test
    fun `custom validators can refuse every structured response validation kind`()
    {
        val kinds = setOf(
            InformationRequestStructuredResponseValidationKind.CROSS_FIELD,
            InformationRequestStructuredResponseValidationKind.CROSS_ROW,
            InformationRequestStructuredResponseValidationKind.UNIT,
            InformationRequestStructuredResponseValidationKind.CURRENCY,
            InformationRequestStructuredResponseValidationKind.DATE_RANGE,
            InformationRequestStructuredResponseValidationKind.PERIOD_COVERAGE,
            InformationRequestStructuredResponseValidationKind.DUPLICATE,
        )
        val service = InformationRequestStructuredResponseValidationService(
            listOf(
                object : InformationRequestStructuredResponseValidator
                {
                    override val kinds = kinds

                    override fun validate(context: InformationRequestStructuredResponseValidationContext) =
                        kinds.map { kind ->
                            InformationRequestStructuredResponseValidationIssue(
                                kind = kind,
                                message = "Rejected ${kind.name}",
                                requirementId = UUID.randomUUID(),
                            )
                        }
                },
            ),
        )

        val failure = assertThrows<InformationRequestLifecycleException> {
            service.validate(
                InformationRequestStructuredResponseValidationContext(
                    request = InformationRequest().apply { id = UUID.randomUUID() },
                    requirementsById = emptyMap(),
                    patches = emptyList(),
                    activeResponses = emptyList(),
                ),
            )
        }

        assertEquals("INFORMATION_REQUEST_STRUCTURED_RESPONSE_VALIDATION_FAILED", failure.reasonCode)
        kinds.forEach { kind ->
            assertTrue(failure.message!!.contains(kind.name))
        }
    }
}
