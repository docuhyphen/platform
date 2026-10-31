package com.docuhyphen.app.api.model.informationrequest.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.*

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
}
