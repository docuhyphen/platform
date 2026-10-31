package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestTemplateFieldMembershipValidatorTest {
    private val existing = UUID.randomUUID()
    private val fields = mock<FieldsReadFacade>().also {
        whenever(it.existingFieldDefinitionIds(any())).thenReturn(setOf(existing))
    }
    private val validator = InformationRequestTemplateFieldMembershipValidator(fields)

    @Test
    fun `a collected Field that does not exist is refused naming its requirement before any Schema is named`() {
        validator.requireCollectedFieldsBound(null, listOf(section("recorded-note", existing)))

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.requireCollectedFieldsBound(null, listOf(section("recorded-entry", UUID.randomUUID())))
        }

        assertEquals("collected-data", refusal.sectionKey)
        assertEquals("recorded-entry", refusal.requirementKey)
    }

    private fun section(requirementKey: String, fieldDefinitionId: UUID) =
        InformationRequestTemplateSectionRequest(
            sectionKey = "collected-data",
            title = "Collected data",
            requirements = listOf(
                InformationRequestTemplateRequirementRequest(
                    requirementKey = requirementKey,
                    requirementType = InformationRequestRequirementType.FIELD,
                    prompt = "State the recorded note",
                    collectedFieldDefinitionId = fieldDefinitionId,
                ),
            ),
        )
}
