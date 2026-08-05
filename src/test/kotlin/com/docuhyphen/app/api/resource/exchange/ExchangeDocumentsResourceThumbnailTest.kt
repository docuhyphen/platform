package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.dto.DocumentThumbnailResult
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExchangeDocumentsResourceThumbnailTest
{
    private val thumbnailContent = byteArrayOf(1, 2, 3)
    private val exchangeDocumentService = mock<ExchangeDocumentService>()
    private val resource = ExchangeDocumentsResource(exchangeDocumentService, mock<FileStorageService>())

    @Test
    fun `thumbnail response is private cacheable PNG with an etag`()
    {
        whenever(exchangeDocumentService.getDocumentThumbnail("exchange", "document"))
            .thenReturn(DocumentThumbnailResult(thumbnailContent, "content-hash"))

        val response = resource.getDocumentThumbnail("exchange", "document", null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals("image/png", response.getHeaderString("Content-Type"))
        assertEquals("\"content-hash\"", response.getHeaderString("ETag"))
        assertEquals("private, max-age=300, must-revalidate", response.getHeaderString("Cache-Control"))
        assertArrayEquals(thumbnailContent, response.entity as ByteArray)
    }

    @Test
    fun `matching etag returns not modified`()
    {
        whenever(exchangeDocumentService.getDocumentThumbnail("exchange", "document"))
            .thenReturn(DocumentThumbnailResult(thumbnailContent, "content-hash"))

        val response = resource.getDocumentThumbnail("exchange", "document", "\"content-hash\"")

        assertEquals(Response.Status.NOT_MODIFIED.statusCode, response.status)
        assertEquals("\"content-hash\"", response.getHeaderString("ETag"))
    }
}
