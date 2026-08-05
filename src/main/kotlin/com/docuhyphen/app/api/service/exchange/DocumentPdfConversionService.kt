package com.docuhyphen.app.api.service.exchange

import jakarta.enterprise.context.ApplicationScoped
import java.io.File
import java.nio.file.Files

@ApplicationScoped
class DocumentPdfConversionService
{
    fun convert(originalFile: File): File
    {
        if (originalFile.extension.equals("pdf", ignoreCase = true)) return originalFile

        val workingDirectory = Files.createTempDirectory("docuhyphen-pdf-").toFile()
        val workingSource = File(workingDirectory, originalFile.name)
        originalFile.copyTo(workingSource, overwrite = true)
        val pdfFile = File(workingDirectory, workingSource.nameWithoutExtension + ".pdf")
        val command = listOf(
            "soffice",
            "--headless",
            "--convert-to",
            "pdf",
            workingSource.absolutePath,
            "--outdir",
            workingDirectory.absolutePath,
        )

        val process = try
        {
            ProcessBuilder(command).redirectErrorStream(true).start()
        }
        catch (_: java.io.IOException)
        {
            workingDirectory.deleteRecursively()
            throw DocumentPreviewConversionException(
                "LibreOffice (soffice) is not available on this server. Original file can still be downloaded."
            )
        }

        process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0 || !pdfFile.exists())
        {
            workingDirectory.deleteRecursively()
            throw DocumentPreviewConversionException("PDF conversion failed for file: ${originalFile.name}")
        }
        return pdfFile
    }
}
