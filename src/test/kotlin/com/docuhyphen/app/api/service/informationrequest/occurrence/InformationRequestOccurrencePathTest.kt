package com.docuhyphen.app.api.service.informationrequest.occurrence

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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

    @Test
    fun `a group occurrence path nests under its parent occurrence`() {
        assertEquals("items[0]", InformationRequestOccurrencePath.groupOccurrencePath("items", 0, null))
        assertEquals("items[0]/parts[2]", InformationRequestOccurrencePath.groupOccurrencePath("parts", 2, "items[0]"))
    }

    @Test
    fun `the next identity index follows the highest sibling identity even after removals`() {
        assertEquals(0, InformationRequestOccurrencePath.nextIdentityIndex("items", null, emptyList()))
        assertEquals(
            3,
            InformationRequestOccurrencePath.nextIdentityIndex("items", null, listOf("items[0]", "items[2]"))
        )
        assertEquals(
            6,
            InformationRequestOccurrencePath.nextIdentityIndex(
                "parts",
                "items[1]",
                listOf("items[0]/parts[9]", "items[1]/parts[5]", "items[1]/parts[x]", "items[1]/other[7]"),
            ),
        )
    }
}
