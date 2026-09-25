package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanVerdict
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScannerEngine
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSignatureState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.time.Instant

class InformationRequestEvidenceDeploymentPolicyTest
{
    @Test
    fun `upload is unavailable while the deployment has not enabled it`()
    {
        val policy = InformationRequestEvidenceDeploymentPolicy(false, false, eligibleScanner)

        assertFalse(policy.uploadAvailable())
        val refusal = assertThrows<InformationRequestLifecycleException> { policy.requireUploadAvailable() }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE, refusal.reasonCode)
    }

    @Test
    fun `where malware scanning is not required, an enabled deployment collects evidence with no scanner at all`()
    {
        val policy = InformationRequestEvidenceDeploymentPolicy(true, false, UnconfiguredInformationRequestEvidenceMalwareScanner)

        assertTrue(policy.uploadAvailable())
        assertDoesNotThrow { policy.requireUploadAvailable() }
        assertFalse(policy.malwareScanRequired())
        assertFalse(policy.malwareScanningConfigured())
    }

    @Test
    fun `where malware scanning is required, upload waits for a production-eligible engine`()
    {
        listOf(UnconfiguredInformationRequestEvidenceMalwareScanner, ineligibleScanner).forEach { scanner ->
            val policy = InformationRequestEvidenceDeploymentPolicy(true, true, scanner)

            assertFalse(policy.uploadAvailable())
            assertThrows<InformationRequestLifecycleException> { policy.requireUploadAvailable() }
        }

        val ready = InformationRequestEvidenceDeploymentPolicy(true, true, eligibleScanner)
        assertTrue(ready.uploadAvailable())
        assertDoesNotThrow { ready.requireUploadAvailable() }
        assertTrue(ready.malwareScanRequired())
    }

    @Test
    fun `a configured scanner is reported whether or not scanning is required`()
    {
        assertTrue(InformationRequestEvidenceDeploymentPolicy(true, false, ineligibleScanner).malwareScanningConfigured())
        assertTrue(InformationRequestEvidenceDeploymentPolicy(true, true, eligibleScanner).malwareScanningConfigured())
    }

    private class ScriptedScanner(private val productionEligible: Boolean) : InformationRequestEvidenceMalwareScanner
    {
        override fun engine() = InformationRequestEvidenceScannerEngine("process-scanner", "1.0", productionEligible)
        override fun signatures() = InformationRequestEvidenceSignatureState("2026.09.25", Instant.now())
        override fun scan(content: File): InformationRequestEvidenceScanVerdict = InformationRequestEvidenceScanVerdict.Clean
    }

    private companion object
    {
        val eligibleScanner: InformationRequestEvidenceMalwareScanner = ScriptedScanner(productionEligible = true)
        val ineligibleScanner: InformationRequestEvidenceMalwareScanner = ScriptedScanner(productionEligible = false)
    }
}
