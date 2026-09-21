package com.docuhyphen.app.api.model.informationrequest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class InformationRequestEvidenceSourceTest
{
    @Test
    fun `file evidence retains the exact Document Version identity`()
    {
        val firstVersion = UUID.randomUUID()
        val secondVersion = UUID.randomUUID()
        val source = InformationRequestDocumentVersionEvidenceSource(firstVersion)

        assertEquals(firstVersion, source.documentVersionId)
        assertEquals(source, InformationRequestDocumentVersionEvidenceSource(firstVersion))
        assertNotEquals(source, InformationRequestDocumentVersionEvidenceSource(secondVersion))
    }

    @Test
    fun `external evidence preserves type and opaque value without resolving content`()
    {
        val source = InformationRequestExternalEvidenceSource("record_reference", "external:record/1")

        assertEquals("record_reference", source.referenceType)
        assertEquals("external:record/1", source.referenceValue)
        assertNotEquals(source, InformationRequestExternalEvidenceSource("result_reference", source.referenceValue))
        assertNotEquals(source, InformationRequestExternalEvidenceSource(source.referenceType, "external:record/2"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `external evidence rejects a blank reference type`(referenceType: String)
    {
        assertThrows<IllegalArgumentException> {
            InformationRequestExternalEvidenceSource(referenceType, "external:record/1")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `external evidence rejects a blank reference value`(referenceValue: String)
    {
        assertThrows<IllegalArgumentException> {
            InformationRequestExternalEvidenceSource("record_reference", referenceValue)
        }
    }

    @Test
    fun `copying an external source cannot bypass reference validation`()
    {
        val source = InformationRequestExternalEvidenceSource("result_reference", "external:result/1")

        assertThrows<IllegalArgumentException> { source.copy(referenceType = "") }
        assertThrows<IllegalArgumentException> { source.copy(referenceValue = "") }
        assertEquals("result_reference", source.referenceType)
        assertEquals("external:result/1", source.referenceValue)
    }
}
