package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant

class InformationRequestEvidenceAssessmentSelectionTest
{
    private val start = Instant.parse("2026-09-25T08:00:00Z")

    @Test
    fun `the latest settled scan governs a version over a later incomplete attempt`()
    {
        val clean = scan("CLEAN", minutes = 0)
        val error = scan("ERROR", minutes = 10)

        assertEquals(clean, InformationRequestEvidenceAssessmentSelection.governingScan(listOf(error, clean)))
    }

    @Test
    fun `a detection governs a version even when a later scan reports it clean`()
    {
        val detected = scan("MALWARE_DETECTED", minutes = 0)
        val clean = scan("CLEAN", minutes = 10)

        assertEquals(detected, InformationRequestEvidenceAssessmentSelection.governingScan(listOf(clean, detected)))
    }

    @Test
    fun `with no settled scan the latest attempt governs, and inspections are never scans`()
    {
        val unavailable = scan("UNAVAILABLE", minutes = 0)
        val timeout = scan("TIMEOUT", minutes = 5)
        val inspection = inspection("INSPECTED", minutes = 10)

        assertEquals(timeout, InformationRequestEvidenceAssessmentSelection.governingScan(listOf(unavailable, inspection, timeout)))
        assertNull(InformationRequestEvidenceAssessmentSelection.governingScan(listOf(inspection)))
    }

    @Test
    fun `the latest inspection describes the version`()
    {
        val first = inspection("INSPECTED", minutes = 0)
        val second = inspection("CORRUPT", minutes = 5)

        assertEquals(second, InformationRequestEvidenceAssessmentSelection.latestInspection(listOf(second, scan("CLEAN", 9), first)))
        assertNull(InformationRequestEvidenceAssessmentSelection.latestInspection(listOf(scan("CLEAN", 1))))
    }

    private fun scan(outcome: String, minutes: Long) = assessment(InformationRequestEvidenceAssessmentKind.MALWARE_SCAN, outcome, minutes)

    private fun inspection(outcome: String, minutes: Long) =
        assessment(InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, outcome, minutes)

    private fun assessment(kind: InformationRequestEvidenceAssessmentKind, outcomeName: String, minutes: Long) =
        InformationRequestEvidenceAssessment().apply {
            assessmentKind = kind
            outcome = outcomeName
            contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
            contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            contentLength = 3
            assessedAt = Timestamp.from(start.plusSeconds(minutes * 60))
            createdAt = assessedAt
        }
}
