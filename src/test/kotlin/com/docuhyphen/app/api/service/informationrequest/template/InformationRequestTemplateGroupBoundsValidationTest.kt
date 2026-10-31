package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.*

class InformationRequestTemplateGroupBoundsValidationTest {
    private val validator = InformationRequestTemplateConfigurationValidator()

    @ParameterizedTest
    @CsvSource(
        "-1, , negative",
        "0, 0, at least one",
        "3, 2, fewer",
    )
    fun `a repeatable group with impossible occurrence bounds is refused by naming the group`(
        minimum: Int,
        maximum: Int?,
        explanation: String,
    ) {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(
                            groupKey = "Reported-Item",
                            minOccurrences = minimum,
                            maxOccurrences = maximum,
                        ),
                    ),
                ),
            )
        }

        assertEquals("reported-item", refusal.groupKey)
        assertEquals(null, refusal.sectionKey)
        assertEquals(null, refusal.requirementKey)
        assertTrue(refusal.message.contains("reported-item"), "The refusal names the group: ${refusal.message}")
        assertTrue(refusal.message.contains(explanation), "The refusal explains the bound: ${refusal.message}")
    }

    @Test
    fun `a nested group is checked for its own bounds`() {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(groupKey = "reported-item", maxOccurrences = 4),
                        InformationRequestTemplateGroupRequest(
                            groupKey = "reported-detail",
                            parentGroupKey = "reported-item",
                            minOccurrences = 2,
                            maxOccurrences = 1,
                        ),
                    ),
                ),
            )
        }

        assertEquals("reported-detail", refusal.groupKey)
    }

    @ParameterizedTest
    @CsvSource(
        "0, ",
        "0, 1",
        "2, 2",
        "1, ",
    )
    fun `possible occurrence bounds are kept as authored`(minimum: Int, maximum: Int?) {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        sectionKey = "reported-data",
                        title = "Reported data",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                requirementKey = "reported-detail",
                                requirementType = InformationRequestRequirementType.FIELD,
                                prompt = "State the reported detail",
                                collectedFieldDefinitionId = UUID.randomUUID(),
                                occurrenceAnchorKey = "reported-item",
                            ),
                        ),
                    ),
                ),
                groups = listOf(
                    InformationRequestTemplateGroupRequest(
                        groupKey = "reported-item",
                        minOccurrences = minimum,
                        maxOccurrences = maximum,
                    ),
                ),
            ),
        )

        val group = normalized.groups.single()
        assertEquals(minimum, group.minOccurrences)
        assertEquals(maximum, group.maxOccurrences)
    }
}
