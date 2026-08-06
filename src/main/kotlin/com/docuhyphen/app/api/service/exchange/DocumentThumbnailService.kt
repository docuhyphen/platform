package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.dto.DocumentThumbnailResult
import com.docuhyphen.app.api.service.storage.DocumentThumbnailStorageService
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType as PdfImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.context.ThreadContext
import org.slf4j.LoggerFactory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@ApplicationScoped
class DocumentThumbnailService @Inject constructor(
    private val fileStorageService: FileStorageService,
    private val thumbnailStorageService: DocumentThumbnailStorageService,
    private val pdfConversionService: DocumentPdfConversionService,
)
{
    companion object
    {
        private const val THUMBNAIL_WIDTH = 360
        private val logger = LoggerFactory.getLogger(DocumentThumbnailService::class.java)
    }

    private val generationLocks = ConcurrentHashMap<String, Any>()
    private val generationSlots = Semaphore(2, true)

    // Dedicated executor for background thumbnail work. The JTA transaction
    // context is explicitly cleared so this off-request rendering never joins
    // the caller's transaction or touches its database connection. Sharing the
    // caller's transaction across threads causes commits to fail with
    // "Enlisted connection used without active transaction".
    private val backgroundExecutor: ManagedExecutor = ManagedExecutor.builder()
        .propagated(ThreadContext.ALL_REMAINING)
        .cleared(ThreadContext.TRANSACTION)
        .maxAsync(2)
        .build()

    @PreDestroy
    fun shutdown()
    {
        backgroundExecutor.shutdown()
        runCatching {
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS))
            {
                backgroundExecutor.shutdownNow()
            }
        }.onFailure {
            Thread.currentThread().interrupt()
            backgroundExecutor.shutdownNow()
        }
    }

    fun scheduleGeneration(document: Document)
    {
        val descriptor = descriptor(document) ?: return
        backgroundExecutor.runAsync {
            runCatching { generateIfMissing(descriptor) }
                .onFailure { error ->
                    logger.warn("Failed to generate document thumbnail for {}", descriptor.documentId, error)
                }
        }
    }

    fun getOrGenerate(document: Document): DocumentThumbnailResult
    {
        val descriptor = descriptor(document)
            ?: throw DocumentThumbnailUnavailableException("Document thumbnail is unavailable")
        val content = thumbnailStorageService.load(descriptor.thumbnailKey)
            ?: generateIfMissing(descriptor)
        return DocumentThumbnailResult(content, descriptor.hash)
    }

    fun scheduleDeletion(documentId: String)
    {
        backgroundExecutor.runAsync {
            runCatching { thumbnailStorageService.deleteDocumentThumbnails(documentId) }
                .onFailure { error -> logger.warn("Failed to delete thumbnails for document {}", documentId, error) }
        }
    }

    private fun generateIfMissing(descriptor: ThumbnailDescriptor): ByteArray
    {
        thumbnailStorageService.load(descriptor.thumbnailKey)?.let { return it }
        val lock = generationLocks.computeIfAbsent(descriptor.thumbnailKey) { Any() }
        try
        {
            synchronized(lock)
            {
                thumbnailStorageService.load(descriptor.thumbnailKey)?.let { return it }
                generationSlots.acquire()
                try
                {
                    return generate(descriptor)
                }
                finally
                {
                    generationSlots.release()
                }
            }
        }
        finally
        {
            generationLocks.remove(descriptor.thumbnailKey, lock)
        }
    }

    private fun generate(descriptor: ThumbnailDescriptor): ByteArray
    {
        val original = fileStorageService.downloadDocument(descriptor.originalStorageKey)
        val thumbnail = Files.createTempFile("docuhyphen-thumbnail-render-", ".png").toFile()
        var pdf: File? = null
        try
        {
            val convertedPdf = pdfConversionService.convert(original)
            pdf = convertedPdf
            Loader.loadPDF(convertedPdf).use { pdfDocument ->
                if (pdfDocument.numberOfPages == 0)
                {
                    throw DocumentThumbnailUnavailableException("Document has no pages")
                }
                val rendered = PDFRenderer(pdfDocument).renderImageWithDPI(0, 96f, PdfImageType.RGB)
                val height = (rendered.height.toDouble() * THUMBNAIL_WIDTH / rendered.width)
                    .toInt()
                    .coerceAtLeast(1)
                val scaled = BufferedImage(THUMBNAIL_WIDTH, height, BufferedImage.TYPE_INT_RGB)
                val graphics = scaled.createGraphics()
                try
                {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.drawImage(rendered, 0, 0, THUMBNAIL_WIDTH, height, null)
                }
                finally
                {
                    graphics.dispose()
                }
                ImageIO.write(scaled, "png", thumbnail)
            }
            thumbnailStorageService.store(thumbnail, descriptor.thumbnailKey)
            return thumbnail.readBytes()
        }
        catch (error: Exception)
        {
            thumbnail.delete()
            throw DocumentThumbnailUnavailableException("Document thumbnail generation failed", error)
        }
        finally
        {
            thumbnail.delete()
            if (pdf != null && pdf != original) pdf.parentFile?.deleteRecursively()
            fileStorageService.releaseDownloadedDocument(original)
        }
    }

    private fun descriptor(document: Document): ThumbnailDescriptor?
    {
        val type = document.type ?: return null
        val hash = document.hash.takeIf { it.isNotBlank() } ?: return null
        if (document.uploadDate == null) return null
        return ThumbnailDescriptor(
            documentId = document.id.toString(),
            hash = hash,
            originalStorageKey = "${document.id}${DocumentType.toFileExtension(type)}",
            thumbnailKey = "${document.id}/$hash/page-1.png",
        )
    }

    private class ThumbnailDescriptor(
        val documentId: String,
        val hash: String,
        val originalStorageKey: String,
        val thumbnailKey: String,
    )
}

class DocumentThumbnailUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
