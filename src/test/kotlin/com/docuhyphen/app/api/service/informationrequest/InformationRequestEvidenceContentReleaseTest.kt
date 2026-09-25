package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestEvidenceContentReleaseTest
{
    private val fixture = InformationRequestEvidenceServiceFixture()
    private val uploader = PrincipalRef.participant(UUID.randomUUID())
    private val reviewer = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val uploaderAccess = RequestAccessContext(uploader, AuthorizationContext(sessionRef = "verified-session"))
    private val version = InformationRequestEvidenceVersion().apply {
        evidenceArtifactId = UUID.randomUUID()
        informationRequestId = fixture.request.id
        versionNumber = 1
        createdByPrincipalKind = PrincipalKind.PARTICIPANT
        createdByPrincipalId = uploader.id
    }

    @Test
    fun `detected malware quarantines a version for every reader, its uploader included, in every deployment`()
    {
        scan("CLEAN", productionEligible = true, minutes = 0)
        scan("MALWARE_DETECTED", productionEligible = true, minutes = 5)

        listOf(false, true).forEach { productionOnly ->
            fixture.malwareScanRequired = productionOnly
            listOf(reviewer, uploaderAccess).forEach { access ->
                val refusal = assertThrows<InformationRequestLifecycleException> { fixture.contentRelease.requireReleasable(version, access) }
                assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED, refusal.reasonCode)
            }
        }
    }

    @Test
    fun `where malware scanning is required, a clean production scan releases a version to every authorized reader`()
    {
        fixture.malwareScanRequired = true
        scan("CLEAN", productionEligible = true, minutes = 0)
        scan("ERROR", productionEligible = false, minutes = 5)

        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, reviewer) }
        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, uploaderAccess) }
    }

    @Test
    fun `where malware scanning is required, unscanned or not production-clean content is released to its uploader only`()
    {
        fixture.malwareScanRequired = true

        assertNotReleasedToReviewer()
        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, uploaderAccess) }

        scan("UNAVAILABLE", productionEligible = false, minutes = 0)
        assertNotReleasedToReviewer()

        scan("CLEAN", productionEligible = false, minutes = 5)
        assertNotReleasedToReviewer()
        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, uploaderAccess) }
    }

    @Test
    fun `where malware scanning is not required, anything but a detection is released to every authorized reader`()
    {
        fixture.malwareScanRequired = false

        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, reviewer) }
        scan("TIMEOUT", productionEligible = false, minutes = 0)
        assertDoesNotThrow { fixture.contentRelease.requireReleasable(version, reviewer) }
    }

    private fun assertNotReleasedToReviewer()
    {
        val refusal = assertThrows<InformationRequestLifecycleException> { fixture.contentRelease.requireReleasable(version, reviewer) }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_CONTENT_NOT_RELEASED, refusal.reasonCode)
    }

    private fun scan(outcomeName: String, productionEligible: Boolean, minutes: Long)
    {
        fixture.assessmentRepository.save(
            InformationRequestEvidenceAssessment().apply {
                informationRequestId = version.informationRequestId
                evidenceVersionId = version.id
                assessmentKind = InformationRequestEvidenceAssessmentKind.MALWARE_SCAN
                outcome = outcomeName
                contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
                contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                contentLength = 3
                if (outcomeName == "CLEAN" || outcomeName == "MALWARE_DETECTED")
                {
                    engineName = "process-scanner"
                    engineVersion = "1.0"
                    signatureVersion = "2026.09.25"
                }
                this.productionEligible = productionEligible
                assessedAt = Timestamp.from(Instant.parse("2026-09-25T08:00:00Z").plusSeconds(minutes * 60))
            },
        )
    }
}
