package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.service.storage.DocumentThumbnailStorageService
import com.docuhyphen.app.api.service.storage.FileStorageService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class DocumentThumbnailServiceTest
{
    @Test
    fun `getOrGenerate stores and reuses a first-page PNG`()
    {
        val sourcePdf = Files.createTempFile("thumbnail-source-", ".pdf").toFile()
        PDDocument().use { pdf ->
            pdf.addPage(PDPage())
            pdf.save(sourcePdf)
        }

        val fileStorage = mock<FileStorageService>()
        whenever(fileStorage.downloadDocument(any())).thenReturn(sourcePdf)
        val conversionService = mock<DocumentPdfConversionService>()
        whenever(conversionService.convert(sourcePdf)).thenReturn(sourcePdf)

        var cachedContent: ByteArray? = null
        val thumbnailStorage = mock<DocumentThumbnailStorageService>()
        whenever(thumbnailStorage.load(any())).thenAnswer { cachedContent }
        doAnswer { invocation ->
            cachedContent = invocation.getArgument<java.io.File>(0).readBytes()
            null
        }.whenever(thumbnailStorage).store(any(), any())

        val service = DocumentThumbnailService(
            fileStorageService = fileStorage,
            thumbnailStorageService = thumbnailStorage,
            pdfConversionService = conversionService,
        )
        val document = Document().apply {
            id = UUID.randomUUID()
            type = DocumentType.PDF
            uploadDate = Timestamp.from(Instant.now())
            hash = "content-hash"
        }

        try
        {
            val first = service.getOrGenerate(document)
            val second = service.getOrGenerate(document)

            assertEquals("content-hash", first.etag)
            assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), first.content.copyOfRange(0, 4))
            assertArrayEquals(first.content, second.content)
            verify(fileStorage, times(1)).downloadDocument(any())
            verify(fileStorage, times(1)).releaseDownloadedDocument(sourcePdf)
            verify(thumbnailStorage, times(1)).store(any(), any())
        }
        finally
        {
            sourcePdf.delete()
        }
    }
}
