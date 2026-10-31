package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateBindingConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestRequirementConfigurationHashTest {
    private val binding = InformationRequestTemplateRequirementBinding().apply {
        id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        prompt = "State the process reference"
    }
    private val requirement = InformationRequestTemplateRequirement().apply {
        id = UUID.fromString("00000000-0000-0000-0000-000000000002")
        requirementKey = "process_reference"
        requirementType = InformationRequestRequirementType.FIELD
    }

    @Test
    fun `a stated requirement configuration keeps the hash its revisions were stored under`() {
        assertEquals(
            "a8e71e16c1b5eeac9aee8121a90720f6c13ad9ac11fa93804be442f00bff74dd",
            InformationRequestRequirementConfigurationHash.of(
                binding,
                requirement,
                configuration(
                    InformationRequestResponseDisposition.PROVIDED,
                    InformationRequestResponseDisposition.NOT_APPLICABLE
                ),
            ),
        )
    }

    @Test
    fun `a change to what the requirement states changes its hash`() {
        val stated = InformationRequestRequirementConfigurationHash.of(
            binding,
            requirement,
            configuration(InformationRequestResponseDisposition.PROVIDED),
        )

        val withAnotherDisposition = InformationRequestRequirementConfigurationHash.of(
            binding,
            requirement,
            configuration(InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.WAIVED),
        )
        binding.prompt = "State the revised process reference"
        val withAnotherPrompt = InformationRequestRequirementConfigurationHash.of(
            binding,
            requirement,
            configuration(InformationRequestResponseDisposition.PROVIDED),
        )

        assertNotEquals(stated, withAnotherDisposition)
        assertNotEquals(stated, withAnotherPrompt)
    }

    private fun configuration(vararg dispositions: InformationRequestResponseDisposition) =
        InformationRequestTemplateBindingConfiguration(
            dispositionsByBinding = mapOf(
                binding.id to dispositions.map { disposition ->
                    InformationRequestTemplateBindingDisposition().apply {
                        templateBindingId = binding.id
                        this.disposition = disposition
                    }
                },
            ),
            policiesByBinding = emptyMap(),
            acceptedValuesByPolicy = emptyMap(),
            substitutesByBinding = emptyMap(),
            evidenceLinksByBinding = emptyMap(),
        )
}
