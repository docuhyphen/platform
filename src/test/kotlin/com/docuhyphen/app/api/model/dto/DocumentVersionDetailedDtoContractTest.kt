package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.document.DocumentVersionView
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant

/**
 * The version payload a client receives. Where the platform stores a version's bytes is server
 * state, so no storage path, provider, or object key is ever part of it, and its creator is shown
 * the way the platform names the principal that created the version.
 */
class DocumentVersionDetailedDtoContractTest
{
    @Test
    fun `a version payload carries no storage location`()
    {
        val fields = (0 until DocumentVersionDetailedDto.serializer().descriptor.elementsCount)
            .map(DocumentVersionDetailedDto.serializer().descriptor::getElementName)

        assertEquals(
            listOf("id", "documentId", "createdAt", "version", "createdByEmail", "createdBy"),
            fields,
        )
    }

    @Test
    fun `a version payload names its creator as the platform shows that principal`()
    {
        val version = version()

        val payload = DetailedEntityToDtoTransformer.toDto(
            DocumentVersionView(version, PrincipalDisplay(name = "Record Author", email = "author@process.test")),
        )

        assertEquals(version.id, payload.id)
        assertEquals(version.document.id.toString(), payload.documentId)
        assertEquals("2", payload.version)
        assertEquals("Record Author", payload.createdBy)
        assertEquals("author@process.test", payload.createdByEmail)
    }

    @Test
    fun `a version payload for a creator the platform cannot name carries no creator`()
    {
        val payload = DetailedEntityToDtoTransformer.toDto(DocumentVersionView(version(), PrincipalDisplay.UNKNOWN))

        assertNull(payload.createdBy)
        assertNull(payload.createdByEmail)
    }

    private fun version(): DocumentVersion =
        DocumentVersion().apply {
            document = Document().apply { title = "Process record" }
            version = "2"
            createdDate = Timestamp.from(Instant.now())
        }
}
