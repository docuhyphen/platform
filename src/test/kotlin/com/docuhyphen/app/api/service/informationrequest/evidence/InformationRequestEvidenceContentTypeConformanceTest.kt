package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.time.LocalDate
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class InformationRequestEvidenceContentTypeConformanceTest {
    @TempDir
    lateinit var directory: Path

    private val inspector = InformationRequestEvidenceContentInspector()
    private val asOf = LocalDate.of(2026, 10, 21)

    @ParameterizedTest
    @CsvSource(
        "records.csv, text/csv",
        "records.json, application/json",
        "records.tsv, text/tab-separated-values",
        "records.xml, text/xml",
        "records.xml, application/xml",
        "notes.txt, text/plain",
        "records.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "records.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "records.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "generated.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "legacy.xls, application/vnd.ms-excel",
        "legacy.ppt, application/vnd.ms-powerpoint",
        "scan.pdf, application/pdf",
        "photo.png, image/png",
        "records.zip, application/zip",
        "records.zip, application/x-zip-compressed",
    )
    fun `an ordinary file conforms with the type its browser declares when no type is restricted`(
        name: String,
        declared: String
    ) {
        val evaluation = evaluate(name, declared)

        assertEquals(
            InformationRequestEvidenceConformance.CONFORMING,
            evaluation.conformance,
            evaluation.findings.toString()
        )
    }

    @Test
    fun `a spreadsheet export declared with a spreadsheet application type conforms when its name says what it is`() {
        val evaluation = evaluate("records.csv", "application/vnd.ms-excel")

        assertEquals(
            InformationRequestEvidenceConformance.CONFORMING,
            evaluation.conformance,
            evaluation.findings.toString()
        )
    }

    @ParameterizedTest
    @CsvSource(
        "binary.csv, text/csv",
        "notes.txt, application/pdf",
        "photo.png, image/jpeg",
        "binary.csv, application/json",
        "records.zip, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "scan.pdf, image/png",
        "records.docx, application/vnd.ms-excel",
    )
    fun `a file whose content is not what it claims to be is a content type mismatch`(name: String, declared: String) {
        val evaluation = evaluate(name, declared)

        assertEquals(InformationRequestEvidenceConformance.DEFICIENT, evaluation.conformance)
        assertTrue(
            evaluation.findings.any { it.code == InformationRequestEvidenceFindingCode.CONTENT_TYPE_MISMATCH },
            evaluation.findings.toString()
        )
    }

    @ParameterizedTest
    @CsvSource(
        "records.csv, text/csv, text/csv",
        "records.csv, application/vnd.ms-excel, text/csv",
        "records.json, application/json, application/json",
        "records.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "generated.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "records.csv, text/csv, text/plain",
        "records.xml, text/xml, application/xml",
        "scan.pdf, application/pdf, application/pdf",
    )
    fun `a file of an accepted type is accepted`(name: String, declared: String, accepted: String) {
        val evaluation = evaluate(name, declared, accepted = setOf(accepted))

        assertEquals(
            InformationRequestEvidenceConformance.CONFORMING,
            evaluation.conformance,
            evaluation.findings.toString()
        )
    }

    @ParameterizedTest
    @CsvSource(
        "records.csv, text/csv, application/pdf",
        "notes.txt, text/plain, text/csv",
        "records.json, application/json, text/csv",
        "records.zip, application/zip, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "legacy.xls, application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "photo.png, image/png, application/pdf",
    )
    fun `a file of a type the policy does not accept is refused`(name: String, declared: String, accepted: String) {
        val evaluation = evaluate(name, declared, accepted = setOf(accepted))

        assertTrue(
            evaluation.findings.any { it.code == InformationRequestEvidenceFindingCode.CONTENT_TYPE_NOT_ACCEPTED },
            evaluation.findings.toString()
        )
    }

    private fun evaluate(name: String, declared: String, accepted: Set<String> = emptySet()) =
        content(name).let { file ->
            InformationRequestEvidencePolicyEvaluator.evaluateVersion(
                policy(accepted),
                InformationRequestEvidenceVersionFacts(
                    versionId = UUID.randomUUID(),
                    artifactId = UUID.randomUUID(),
                    versionNumber = 1,
                    standing = InformationRequestEvidenceStanding.CURRENT,
                    contentVerification = DocumentVersionContentVerification.VERIFIED,
                    contentLength = file.length(),
                    declaredMediaType = declared,
                    declaredFileName = name,
                    attributes = InformationRequestEvidenceAttributes.NONE,
                    inspection = inspector.inspect(file),
                    malware = null,
                ),
                asOf,
                false,
            )
        }

    private fun content(name: String): File {
        val file = directory.resolve("${UUID.randomUUID()}-$name").toFile()
        when (name) {
            "records.csv" -> file.writeText("record,count\nfirst,1\nsecond,2\n")
            "records.json" -> file.writeText("{\"records\":[{\"key\":\"first\",\"count\":1}]}")
            "records.tsv" -> file.writeText("record\tcount\nfirst\t1\n")
            "records.xml" -> file.writeText("<?xml version=\"1.0\"?><records><record key=\"first\"/></records>")
            "notes.txt" -> file.writeText("A note about the records.\n")
            "binary.csv" -> file.writeBytes(ByteArray(512) { (it * 37 % 256).toByte() }.also {
                it[0] = 0; it[1] = 0x01
            })

            "records.docx" -> file.writeBytes(zip("[Content_Types].xml", "_rels/.rels", "word/document.xml"))
            "records.xlsx" -> file.writeBytes(zip("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml"))
            "records.pptx" -> file.writeBytes(zip("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml"))
            "generated.xlsx" -> file.writeBytes(zip("xl/workbook.xml", "_rels/.rels", "[Content_Types].xml"))
            "records.zip" -> file.writeBytes(zip("first.txt", "second.txt"))
            "legacy.xls" -> file.writeBytes(
                InformationRequestEvidenceContentInspectorTest.CompoundFiles.of("Workbook" to ByteArray(4_200) { 9 }),
            )

            "legacy.ppt" -> file.writeBytes(
                InformationRequestEvidenceContentInspectorTest.CompoundFiles.of(
                    "PowerPoint Document" to ByteArray(4_200) { 3 },
                    "Current User" to ByteArray(64) { 0 },
                ),
            )

            "scan.pdf" -> PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(file)
            }

            "photo.png" -> ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", file)
            else -> error("No content for $name")
        }
        return file
    }

    private fun zip(vararg names: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { archive ->
            names.forEach { name ->
                archive.putNextEntry(ZipEntry(name))
                archive.write("<part/>".toByteArray())
                archive.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun policy(accepted: Set<String>) = InformationRequestEvidencePolicy(
        minimumFileCount = 1,
        maximumFileCount = null,
        maximumFileSizeBytes = null,
        maximumTotalSizeBytes = null,
        minimumPageCount = null,
        maximumPageCount = null,
        attributeRequirements = emptyMap(),
        acceptedValues = if (accepted.isEmpty()) emptyMap() else mapOf(InformationRequestEvidenceAttribute.CONTENT_TYPE to accepted),
        maximumIssueAgeDays = null,
        minimumRemainingValidityDays = null,
        minimumCoverageDays = null,
        coverageContinuityRequired = false,
        waiverPolicy = InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
        conformancePolicy = InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    )
}
