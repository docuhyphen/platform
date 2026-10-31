package com.docuhyphen.app.api.service.informationrequest.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class InformationRequestTemplateTextLimitsTest {
    @Test
    fun `a Template's name, keys and description are refused past the length the record keeps`() {
        val refusals = listOf(
            {
                InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
                    "a".repeat(129),
                    "key",
                    "Name",
                    null
                )
            },
            {
                InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
                    "process",
                    "k".repeat(129),
                    "Name",
                    null
                )
            },
            {
                InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
                    "process",
                    "key",
                    "n".repeat(256),
                    null
                )
            },
            {
                InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
                    "process",
                    "key",
                    "Name",
                    "d".repeat(1025)
                )
            },
        ).map { check -> assertThrows<InformationRequestTemplateValidationException> { check() }.message }

        assertEquals(
            listOf(
                "The namespace is longer than 128 characters",
                "The Template key is longer than 128 characters",
                "The name is longer than 255 characters",
                "The description is longer than 1024 characters",
            ),
            refusals,
        )
        assertDoesNotThrow {
            InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
                "p".repeat(128),
                "k".repeat(128),
                "n".repeat(255),
                "d".repeat(1024),
            )
        }
    }
}
