package com.docuhyphen.app.api.service.informationrequest

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A Requirement outside every repeatable group sits on the root occurrence, which has one spelling.
 */
class InformationRequestOccurrencePathTest
{
    @Test
    fun `the root occurrence is spelled root`()
    {
        assertTrue(InformationRequestOccurrencePath.isRoot("root"))
    }

    @Test
    fun `no other spelling is read as the root occurrence`()
    {
        assertFalse(InformationRequestOccurrencePath.isRoot("\$"))
        assertFalse(InformationRequestOccurrencePath.isRoot("items[0]"))
    }

    @Test
    fun `an occurrence is active when it is the root or a listed active occurrence`()
    {
        assertTrue(InformationRequestOccurrencePath.isActiveOccurrence("root", emptySet()))
        assertTrue(InformationRequestOccurrencePath.isActiveOccurrence("items[0]", setOf("items[0]")))
        assertFalse(InformationRequestOccurrencePath.isActiveOccurrence("items[1]", setOf("items[0]")))
        assertFalse(InformationRequestOccurrencePath.isActiveOccurrence("\$", emptySet()))
    }
}
