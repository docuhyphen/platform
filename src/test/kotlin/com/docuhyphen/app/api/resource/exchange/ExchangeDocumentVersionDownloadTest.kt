package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionReadHandle
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentVersionService
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files

class ExchangeDocumentVersionDownloadTest {
    private val versionService: ExchangeDocumentVersionService = mock()
    private val resource = ExchangeDocumentVersionResource(versionService)

    @Test
    fun `a downloaded version copy is written to the caller and then released`() {
        val directory = Files.createTempDirectory("document-version-download-copy")
        val copy = directory.resolve("Process record_v1.pdf").toFile().apply { writeText("stored content") }
        whenever(versionService.getVersionContent("exchange-1", "document-1", "version-1")).thenReturn(
            DocumentVersionContent(DocumentVersionReadHandle.temporaryCopy(copy, directory), "Process record_v1.pdf"),
        )

        val response = resource.downloadVersion("exchange-1", "document-1", "version-1")

        assertEquals(200, response.status)
        assertEquals("attachment; filename=\"Process record_v1.pdf\"", response.getHeaderString("Content-Disposition"))
        assertEquals("14", response.getHeaderString("Content-Length"))
        assertArrayEquals("stored content".toByteArray(), body(response))
        assertFalse(directory.toFile().exists())
    }

    private fun body(response: Response): ByteArray =
        when (val entity = response.entity) {
            is StreamingOutput -> ByteArrayOutputStream().also { entity.write(it) }.toByteArray()
            is InputStream -> entity.use { it.readBytes() }
            else -> error("The download response carries no readable body")
        }
}
