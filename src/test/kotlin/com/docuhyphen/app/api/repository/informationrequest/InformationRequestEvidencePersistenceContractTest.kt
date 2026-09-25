package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributesMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStoredUsage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExternalEvidenceSource
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSupportingEvidenceLinkService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

private const val ABC_SHA_256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

private class RequestEvidencePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<RequestEvidencePostgreSQLContainer>(imageName)

class RequestEvidencePostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = RequestEvidencePostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_request_evidence_persistence_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

/**
 * A stored evidence item has to come back as the exact typed source it was written as, under the
 * exact Requirement occurrence it was collected for.
 *
 * A lookup that reads an artifact key without its Requirement occurrence answers for a different
 * occurrence of the same request, and a version list that ignores its artifact mixes two collected
 * items into one history. Recorded evidence history is never rewritten, so the mapped repository
 * must refuse an update or a delete rather than leave that refusal to the database alone.
 */
@QuarkusTest
@QuarkusTestResource(RequestEvidencePostgreSQLResource::class)
class InformationRequestEvidencePersistenceContractTest
{
    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var versionRepository: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var assessmentRepository: InformationRequestEvidenceAssessmentRepository

    @Inject
    lateinit var supportingLinkRepository: InformationRequestSupportingEvidenceLinkRepository

    @Inject
    lateinit var supportingLinkService: InformationRequestSupportingEvidenceLinkService

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `an artifact and its file-backed and external versions survive the round trip`()
    {
        val fixture = fixture()

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record"))
        }
        val fileVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                fileVersion(artifact.id, fixture.requestId, 1, fixture.documentVersionId),
            )
        }
        val externalVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                externalVersion(artifact.id, fixture.requestId, 2, "process-register", "REG-4821"),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val storedArtifact = artifactRepository.findById(artifact.id)
            assertEquals(fixture.requestId, storedArtifact?.informationRequestId)
            assertEquals(fixture.requirementId, storedArtifact?.informationRequestRequirementId)
            assertEquals("collected-record", storedArtifact?.artifactKey)
            assertEquals(1L, storedArtifact?.artifactRevision)

            val storedFileVersion = versionRepository.findById(fileVersion.id)
            assertEquals(artifact.id, storedFileVersion?.evidenceArtifactId)
            assertEquals(fixture.requestId, storedFileVersion?.informationRequestId)
            assertEquals(1, storedFileVersion?.versionNumber)
            assertEquals(InformationRequestEvidenceSourceKind.DOCUMENT_VERSION, storedFileVersion?.sourceKind)
            assertEquals(
                InformationRequestDocumentVersionEvidenceSource(fixture.documentVersionId),
                InformationRequestEvidenceVersionSourceMapper.read(storedFileVersion!!),
            )

            val storedExternalVersion = versionRepository.findById(externalVersion.id)
            assertEquals(
                InformationRequestEvidenceSourceKind.EXTERNAL_REFERENCE,
                storedExternalVersion?.sourceKind,
            )
            assertNull(storedExternalVersion?.documentVersionId)
            assertEquals(
                InformationRequestExternalEvidenceSource("process-register", "REG-4821"),
                InformationRequestEvidenceVersionSourceMapper.read(storedExternalVersion!!),
            )
        }
    }

    @Test
    fun `an artifact lookup answers only for its own Requirement occurrence and request`()
    {
        val fixture = fixture()

        val owned = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record")).id
        }
        val sibling = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(
                artifact(fixture.requestId, fixture.siblingRequirementId, "collected-record"),
            ).id
        }
        val otherRequest = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(
                artifact(fixture.otherRequestId, fixture.otherRequestRequirementId, "collected-record"),
            ).id
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(owned),
                artifactRepository.findForRequirement(fixture.requirementId).map { it.id },
            )
            assertEquals(
                listOf(sibling),
                artifactRepository.findForRequirement(fixture.siblingRequirementId).map { it.id },
            )
            assertEquals(
                owned,
                artifactRepository.findByKey(fixture.requirementId, "collected-record")?.id,
            )
            assertEquals(
                setOf(owned, sibling),
                artifactRepository.findForRequest(fixture.requestId).map { it.id }.toSet(),
            )
            assertEquals(
                listOf(otherRequest),
                artifactRepository.findForRequest(fixture.otherRequestId).map { it.id },
            )
            assertNull(artifactRepository.findByKey(fixture.requirementId, "unrecorded-key"))
        }
    }

    @Test
    fun `versions read in recorded order under their own artifact`()
    {
        val fixture = fixture()

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record")).id
        }
        val otherArtifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "supporting-record")).id
        }

        val first = QuarkusTransaction.requiringNew().call {
            versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId)).id
        }
        val second = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                externalVersion(artifact, fixture.requestId, 2, "process-register", "REG-4821"),
            ).id
        }
        val otherArtifactVersion = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                fileVersion(otherArtifact, fixture.requestId, 1, fixture.documentVersionId),
            ).id
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                listOf(first, second),
                versionRepository.findForArtifact(artifact).map { it.id },
            )
            assertEquals(second, versionRepository.findLatest(artifact)?.id)
            assertEquals(
                listOf(otherArtifactVersion),
                versionRepository.findForArtifact(otherArtifact).map { it.id },
            )
            assertEquals(otherArtifactVersion, versionRepository.findLatest(otherArtifact)?.id)
            assertNull(versionRepository.findLatest(UUID.randomUUID()))
        }
    }

    @Test
    fun `recorded evidence history is append-only through the mapped repository`()
    {
        val fixture = fixture()

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record")).id
        }
        val recorded = QuarkusTransaction.requiringNew().call {
            versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId))
        }

        val rewrite = assertThrows<UnsupportedOperationException> {
            versionRepository.update(recorded.apply { versionNumber = 5 })
        }
        assertTrue(rewrite.message.orEmpty().isNotBlank(), "The refusal has to say why it refused")
        assertThrows<UnsupportedOperationException> { versionRepository.delete(recorded) }
        assertThrows<UnsupportedOperationException> { versionRepository.deleteById(recorded.id) }

        QuarkusTransaction.requiringNew().run {
            assertEquals(1, versionRepository.findById(recorded.id)?.versionNumber)
            assertEquals(listOf(recorded.id), versionRepository.findForArtifact(artifact).map { it.id })
        }
    }

    @Test
    fun `a recorded version number is never repeated or skipped for one artifact`()
    {
        val fixture = fixture()

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record")).id
        }
        QuarkusTransaction.requiringNew().run {
            versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId))
        }

        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().run {
                versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId))
            }
        }
        assertThrows<Exception> {
            QuarkusTransaction.requiringNew().run {
                versionRepository.save(fileVersion(artifact, fixture.requestId, 3, fixture.documentVersionId))
            }
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(listOf(1), versionRepository.findForArtifact(artifact).map { it.versionNumber })
        }
    }

    @Test
    fun `an artifact's creator and state change and a version's uploader and attributes survive the round trip`()
    {
        val fixture = fixture()
        val creatorId = UUID.randomUUID()
        val attributes = InformationRequestEvidenceAttributes(
            issuer = "Process Registry",
            language = "en",
            issuedOn = LocalDate.of(2026, 1, 10),
            expiresOn = LocalDate.of(2027, 1, 10),
            coverage = InformationRequestEvidenceCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
        )

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(
                artifact(fixture.requestId, fixture.requirementId, "collected-record").apply {
                    createdByPrincipalKind = PrincipalKind.PARTICIPANT
                    createdByPrincipalId = creatorId
                },
            )
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(
                fileVersion(artifact.id, fixture.requestId, 1, fixture.documentVersionId).apply {
                    createdByPrincipalKind = PrincipalKind.PARTICIPANT
                    createdByPrincipalId = creatorId
                    createdBySessionRef = "verified-session"
                    declaredFileName = "record.pdf"
                    declaredMediaType = "application/pdf"
                    InformationRequestEvidenceAttributesMapper.write(this, attributes)
                },
            )
        }
        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(artifactRepository.findById(artifact.id))
            stored.collectionState = InformationRequestEvidenceCollectionState.WITHDRAWN
            stored.stateChangedAt = Timestamp.from(Instant.now())
            stored.stateChangedByPrincipalKind = PrincipalKind.PARTICIPANT
            stored.stateChangedByPrincipalId = creatorId
            stored.stateReason = "Recorded in error"
            stored.artifactRevision = 2
            artifactRepository.update(stored)
        }

        QuarkusTransaction.requiringNew().run {
            val storedArtifact = requireNotNull(artifactRepository.findById(artifact.id))
            assertEquals(PrincipalKind.PARTICIPANT, storedArtifact.createdByPrincipalKind)
            assertEquals(creatorId, storedArtifact.createdByPrincipalId)
            assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, storedArtifact.collectionState)
            assertEquals(creatorId, storedArtifact.stateChangedByPrincipalId)
            assertEquals("Recorded in error", storedArtifact.stateReason)
            assertEquals(2L, storedArtifact.artifactRevision)

            val storedVersion = requireNotNull(versionRepository.findById(version.id))
            assertEquals(PrincipalKind.PARTICIPANT, storedVersion.createdByPrincipalKind)
            assertEquals("verified-session", storedVersion.createdBySessionRef)
            assertEquals("record.pdf", storedVersion.declaredFileName)
            assertEquals("application/pdf", storedVersion.declaredMediaType)
            assertEquals(attributes, InformationRequestEvidenceAttributesMapper.read(storedVersion))
        }
    }

    @Test
    fun `evidence assessments survive the round trip in assessment order and are append-only through the mapped repository`()
    {
        val fixture = fixture()
        val assessedAt = Instant.parse("2026-09-25T08:00:00Z")

        val artifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "collected-record")).id
        }
        val version = QuarkusTransaction.requiringNew().call {
            versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId))
        }
        val scan = QuarkusTransaction.requiringNew().call {
            assessmentRepository.save(
                assessment(fixture.requestId, version.id, InformationRequestEvidenceAssessmentKind.MALWARE_SCAN, "CLEAN", assessedAt.plusSeconds(60))
                    .apply {
                        engineName = "process-scanner"
                        engineVersion = "1.0"
                        signatureVersion = "2026.09.25"
                        signaturesPublishedAt = Timestamp.from(Instant.parse("2026-09-24T00:00:00Z"))
                        productionEligible = true
                    },
            )
        }
        val inspection = QuarkusTransaction.requiringNew().call {
            assessmentRepository.save(
                assessment(fixture.requestId, version.id, InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, "INSPECTED", assessedAt)
                    .apply {
                        detectedMediaType = "application/pdf"
                        pageCount = 2
                    },
            )
        }
        val unavailable = QuarkusTransaction.requiringNew().call {
            assessmentRepository.save(
                assessment(fixture.requestId, version.id, InformationRequestEvidenceAssessmentKind.MALWARE_SCAN, "UNAVAILABLE", assessedAt.plusSeconds(120))
                    .apply { detail = "scanner not configured" },
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = assessmentRepository.findForVersions(listOf(version.id))
            assertEquals(listOf(inspection.id, scan.id, unavailable.id), stored.map { it.id })
            val storedInspection = stored.first()
            assertEquals(InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION, storedInspection.assessmentKind)
            assertEquals("application/pdf", storedInspection.detectedMediaType)
            assertEquals(2, storedInspection.pageCount)
            assertEquals(3L, storedInspection.contentLength)
            val storedScan = stored[1]
            assertEquals("process-scanner", storedScan.engineName)
            assertEquals("2026.09.25", storedScan.signatureVersion)
            assertEquals(Instant.parse("2026-09-24T00:00:00Z"), storedScan.signaturesPublishedAt?.toInstant())
            assertTrue(storedScan.productionEligible)
            assertEquals("scanner not configured", stored[2].detail)

            val settled = assessmentRepository.findSettledMalwareScans(ABC_SHA_256).map { it.id }
            assertTrue(scan.id in settled)
            assertTrue(inspection.id !in settled && unavailable.id !in settled)
            assertTrue(assessmentRepository.findForVersions(emptyList()).isEmpty())
        }

        assertThrows<UnsupportedOperationException> { assessmentRepository.update(scan) }
        assertThrows<UnsupportedOperationException> { assessmentRepository.delete(scan) }
        assertThrows<UnsupportedOperationException> { assessmentRepository.deleteById(scan.id) }
    }

    @Test
    fun `a file-backed version is due for a malware scan until a final or fresh result exists`()
    {
        val fixture = fixture()
        val now = Instant.parse("2026-09-25T12:00:00Z")
        val engine: InformationRequestEvidenceAssessment.() -> Unit = {
            engineName = "process-scanner"
            engineVersion = "1.0"
            signatureVersion = "2026.09.25"
            productionEligible = true
        }

        fun scanned(key: String, outcome: String?, at: Instant, describe: InformationRequestEvidenceAssessment.() -> Unit = {}): UUID
        {
            val artifact = QuarkusTransaction.requiringNew().call {
                artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, key)).id
            }
            val version = QuarkusTransaction.requiringNew().call {
                versionRepository.save(fileVersion(artifact, fixture.requestId, 1, fixture.documentVersionId))
            }
            if (outcome != null)
            {
                QuarkusTransaction.requiringNew().run {
                    assessmentRepository.save(
                        assessment(fixture.requestId, version.id, InformationRequestEvidenceAssessmentKind.MALWARE_SCAN, outcome, at)
                            .apply(describe),
                    )
                }
            }
            return version.id
        }

        val unscanned = scanned("unscanned", null, now)
        val recentFailure = scanned("recent-failure", "ERROR", now.minusSeconds(5 * 60))
        val oldFailure = scanned("old-failure", "TIMEOUT", now.minusSeconds(60 * 60))
        val recentClean = scanned("recent-clean", "CLEAN", now.minusSeconds(24 * 60 * 60), engine)
        val oldClean = scanned("old-clean", "CLEAN", now.minusSeconds(40L * 24 * 60 * 60), engine)
        val detected = scanned("detected", "MALWARE_DETECTED", now.minusSeconds(40L * 24 * 60 * 60), engine)
        val skipped = scanned("skipped", "SKIPPED", now.minusSeconds(40L * 24 * 60 * 60))
        val externalArtifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "external")).id
        }
        val external = QuarkusTransaction.requiringNew().call {
            versionRepository.save(externalVersion(externalArtifact, fixture.requestId, 1, "process-register", "REG-4821")).id
        }

        val due = QuarkusTransaction.requiringNew().call {
            assessmentRepository.findVersionsDueForMalwareScan(
                retryBefore = now.minusSeconds(15 * 60),
                rescanBefore = now.minusSeconds(30L * 24 * 60 * 60),
                limit = 10_000,
            )
        }

        assertTrue(listOf(unscanned, oldFailure, oldClean).all { it in due }, "Expected every due version in $due")
        assertTrue(listOf(recentFailure, recentClean, detected, skipped, external).none { it in due }, "Unexpected version in $due")
        assertEquals(
            1,
            QuarkusTransaction.requiringNew().call {
                assessmentRepository.findVersionsDueForMalwareScan(now.minusSeconds(15 * 60), now.minusSeconds(30L * 24 * 60 * 60), 1)
            }.size,
        )
    }

    @Test
    fun `stored usage counts every retained file-backed version and its bytes, for a request and for one uploader`()
    {
        val fixture = fixture()
        val uploader = PrincipalRef.participant(UUID.randomUUID())
        val otherParty = PrincipalRef.user(UUID.randomUUID())

        fun stored(artifactId: UUID, number: Int, by: PrincipalRef)
        {
            QuarkusTransaction.requiringNew().run {
                versionRepository.save(
                    fileVersion(artifactId, fixture.requestId, number, fixture.documentVersionId).apply {
                        createdByPrincipalKind = by.kind
                        createdByPrincipalId = by.id
                    },
                )
            }
        }

        val replaced = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "replaced")).id
        }
        stored(replaced, 1, uploader)
        stored(replaced, 2, uploader)
        val otherArtifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "other-party")).id
        }
        stored(otherArtifact, 1, otherParty)
        val externalArtifact = QuarkusTransaction.requiringNew().call {
            artifactRepository.save(artifact(fixture.requestId, fixture.requirementId, "external")).id
        }
        QuarkusTransaction.requiringNew().run {
            versionRepository.save(
                externalVersion(externalArtifact, fixture.requestId, 1, "process-register", "REG-4821").apply {
                    createdByPrincipalKind = uploader.kind
                    createdByPrincipalId = uploader.id
                },
            )
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(InformationRequestEvidenceStoredUsage(3, 9), versionRepository.storedUsageForRequest(fixture.requestId))
            assertEquals(
                InformationRequestEvidenceStoredUsage(2, 6),
                versionRepository.storedUsageForUploader(fixture.requestId, uploader),
            )
            assertEquals(
                InformationRequestEvidenceStoredUsage(1, 3),
                versionRepository.storedUsageForUploader(fixture.requestId, otherParty),
            )
            assertEquals(InformationRequestEvidenceStoredUsage(0, 0), versionRepository.storedUsageForRequest(fixture.otherRequestId))
        }
    }

    @Test
    fun `a materialized supporting evidence link survives the round trip and is append-only through the mapped repository`()
    {
        val fixture = fixture()

        val link = QuarkusTransaction.requiringNew().call {
            supportingLinkRepository.save(
                InformationRequestSupportingEvidenceLink().apply {
                    informationRequestId = fixture.requestId
                    supportedRequirementId = fixture.requirementId
                    supportingRequirementId = fixture.documentRequirementId
                    templateEvidenceLinkId = fixture.templateEvidenceLinkId
                },
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = supportingLinkRepository.findForRequest(fixture.requestId).single()
            assertEquals(link.id, stored.id)
            assertEquals(fixture.requirementId, stored.supportedRequirementId)
            assertEquals(fixture.documentRequirementId, stored.supportingRequirementId)
            assertEquals(fixture.templateEvidenceLinkId, stored.templateEvidenceLinkId)
            assertTrue(supportingLinkRepository.findForRequest(fixture.otherRequestId).isEmpty())
        }
        assertThrows<UnsupportedOperationException> { supportingLinkRepository.update(link) }
        assertThrows<UnsupportedOperationException> { supportingLinkRepository.delete(link) }
        assertThrows<UnsupportedOperationException> { supportingLinkRepository.deleteById(link.id) }
    }

    @Test
    fun `materializing a request writes each supporting evidence link its Template links resolve to, once`()
    {
        val fixture = fixture()
        val request = QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)) }

        QuarkusTransaction.requiringNew().run { supportingLinkService.materialize(request) }
        QuarkusTransaction.requiringNew().run { supportingLinkService.materialize(request) }

        QuarkusTransaction.requiringNew().run {
            val links = supportingLinkRepository.findForRequest(fixture.requestId)
            assertEquals(
                setOf(fixture.requirementId to fixture.documentRequirementId, fixture.siblingRequirementId to fixture.documentRequirementId),
                links.map { it.supportedRequirementId to it.supportingRequirementId }.toSet(),
            )
            assertEquals(2, links.size)
            assertTrue(links.all { it.templateEvidenceLinkId == fixture.templateEvidenceLinkId })
        }
    }

    private fun assessment(
        requestId: UUID,
        versionId: UUID,
        kind: InformationRequestEvidenceAssessmentKind,
        outcomeName: String,
        at: Instant,
    ) = InformationRequestEvidenceAssessment().apply {
        informationRequestId = requestId
        evidenceVersionId = versionId
        assessmentKind = kind
        outcome = outcomeName
        contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
        contentHash = ABC_SHA_256
        contentLength = 3
        assessedAt = Timestamp.from(at)
    }

    private fun artifact(requestId: UUID, requirementId: UUID, key: String) =
        InformationRequestEvidenceArtifact().apply {
            informationRequestId = requestId
            informationRequestRequirementId = requirementId
            artifactKey = key
            createdByPrincipalKind = PrincipalKind.USER
            createdByPrincipalId = UUID.randomUUID()
        }

    private fun fileVersion(
        artifactId: UUID,
        requestId: UUID,
        number: Int,
        documentVersion: UUID,
    ) = InformationRequestEvidenceVersion().apply {
        evidenceArtifactId = artifactId
        informationRequestId = requestId
        versionNumber = number
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = UUID.randomUUID()
        declaredFileName = "process-record.pdf"
        InformationRequestEvidenceVersionSourceMapper.write(
            this,
            InformationRequestDocumentVersionEvidenceSource(documentVersion),
        )
    }

    private fun externalVersion(
        artifactId: UUID,
        requestId: UUID,
        number: Int,
        referenceType: String,
        referenceValue: String,
    ) = InformationRequestEvidenceVersion().apply {
        evidenceArtifactId = artifactId
        informationRequestId = requestId
        versionNumber = number
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = UUID.randomUUID()
        InformationRequestEvidenceVersionSourceMapper.write(
            this,
            InformationRequestExternalEvidenceSource(referenceType, referenceValue),
        )
    }

    private fun fixture(): Fixture
    {
        val fixture = Fixture()
        dataSource.connection.use { connection -> fixture.insertInto(connection) }
        return fixture
    }

    private inner class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val templateVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val templateRequirementId: UUID = UUID.randomUUID()
        val templateBindingId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()
        val otherRequestId: UUID = UUID.randomUUID()
        val requirementId: UUID = UUID.randomUUID()
        val siblingRequirementId: UUID = UUID.randomUUID()
        val otherRequestRequirementId: UUID = UUID.randomUUID()
        val documentId: UUID = UUID.randomUUID()
        val documentVersionId: UUID = UUID.randomUUID()
        val documentTemplateRequirementId: UUID = UUID.randomUUID()
        val documentBindingId: UUID = UUID.randomUUID()
        val templateEvidenceLinkId: UUID = UUID.randomUUID()
        val documentRequirementId: UUID = UUID.randomUUID()

        fun insertInto(connection: Connection)
        {
            val now = Timestamp.from(Instant.now())
            execute(
                connection,
                """
                INSERT INTO organization
                    (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process Owner', ?, TRUE, TRUE, ?)
                """.trimIndent(),
                organizationId,
                "REG-${organizationId.toString().take(8)}",
                now,
            )
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                userId,
                now,
                "evidence-owner-${userId.toString().take(8)}@process.test",
            )
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                     created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                exchangeId,
                organizationId,
                userId,
                now,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_definition
                    (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                     status, created_at, updated_at)
                VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern', 'PUBLISHED', ?, ?)
                """.trimIndent(),
                definitionId,
                organizationId,
                "collection-${definitionId.toString().take(8)}",
                now,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_version
                    (id, template_definition_id, version_number, status, created_at)
                VALUES (?, ?, 1, 'DRAFT', ?)
                """.trimIndent(),
                templateVersionId,
                definitionId,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_section
                    (id, template_version_id, section_key, display_order, title)
                VALUES (?, ?, 'records', 1, 'Records')
                """.trimIndent(),
                sectionId,
                templateVersionId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_requirement
                    (id, template_definition_id, requirement_key, requirement_type, created_at)
                VALUES (?, ?, 'recorded-assertion', 'RESPONSE_ATTESTATION', ?)
                """.trimIndent(),
                templateRequirementId,
                definitionId,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_requirement_binding
                    (id, template_version_id, template_definition_id, template_requirement_id,
                     template_section_id, display_order, prompt, response_mode, requiredness,
                     contributor_role, review_policy)
                VALUES (?, ?, ?, ?, ?, 1, 'State the response', 'PROVIDE', 'REQUIRED',
                        'CONTRIBUTOR', 'NOT_REQUIRED')
                """.trimIndent(),
                templateBindingId,
                templateVersionId,
                definitionId,
                templateRequirementId,
                sectionId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_requirement
                    (id, template_definition_id, requirement_key, requirement_type, created_at)
                VALUES (?, ?, 'supporting-record', 'DOCUMENT', ?)
                """.trimIndent(),
                documentTemplateRequirementId,
                definitionId,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_requirement_binding
                    (id, template_version_id, template_definition_id, template_requirement_id,
                     template_section_id, display_order, prompt, response_mode, requiredness,
                     contributor_role, review_policy)
                VALUES (?, ?, ?, ?, ?, 2, 'Provide the supporting record', 'PROVIDE', 'REQUIRED',
                        'CONTRIBUTOR', 'NOT_REQUIRED')
                """.trimIndent(),
                documentBindingId,
                templateVersionId,
                definitionId,
                documentTemplateRequirementId,
                sectionId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_evidence_policy
                    (id, template_binding_id, template_version_id, minimum_file_count, maximum_file_count,
                     issuer_requirement, jurisdiction_requirement, language_requirement, issue_date_requirement,
                     expiry_date_requirement, coverage_period_requirement, certification_requirement,
                     signature_requirement, coverage_continuity_required, waiver_policy, conformance_policy)
                VALUES (?, ?, ?, 1, 3, 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED',
                        'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', 'NOT_CAPTURED', FALSE,
                        'NOT_PERMITTED', 'CONFORMANCE_REQUIRED')
                """.trimIndent(),
                UUID.randomUUID(),
                documentBindingId,
                templateVersionId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_template_binding_evidence_link
                    (id, template_binding_id, supporting_template_binding_id, template_version_id)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                templateEvidenceLinkId,
                templateBindingId,
                documentBindingId,
                templateVersionId,
            )
            publish(connection, now)
            insertRequest(connection, requestId, now)
            insertRequest(connection, otherRequestId, now)
            insertRuntimeRequirement(connection, requirementId, requestId, "root", now)
            insertRuntimeRequirement(connection, siblingRequirementId, requestId, "items[0]", now)
            insertRuntimeRequirement(connection, otherRequestRequirementId, otherRequestId, "root", now)
            execute(
                connection,
                """
                INSERT INTO information_request_requirement
                    (id, information_request_id, source_template_version_id,
                     source_template_requirement_id, source_template_binding_id, occurrence_path,
                     created_at)
                VALUES (?, ?, ?, ?, ?, 'root', ?)
                """.trimIndent(),
                documentRequirementId,
                requestId,
                templateVersionId,
                documentTemplateRequirementId,
                documentBindingId,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO document
                    (id, encryption_mode, is_deleted, created_date, update_date, title, type)
                VALUES (?, 0, FALSE, ?, ?, 'Process record', 'PDF')
                """.trimIndent(),
                documentId,
                now,
                now,
            )
            execute(
                connection,
                """
                INSERT INTO document_version
                    (id, document_id, created_by_principal_kind, created_by_principal_id, created_date, file_name,
                     version, storage_provider, storage_locator_kind, storage_locator,
                     content_length, content_hash_algorithm, content_hash, content_verification)
                VALUES (?, ?, 'USER', ?, ?, 'process-record.pdf', '1', 'OBJECT_STORE', 'OBJECT_KEY', ?,
                        3, 'SHA_256', '$ABC_SHA_256', 'VERIFIED')
                """.trimIndent(),
                documentVersionId,
                documentId,
                userId,
                now,
                "document-versions/process-record-${documentVersionId.toString().take(8)}.pdf",
            )
        }

        private fun publish(connection: Connection, now: Timestamp)
        {
            execute(
                connection,
                """
                INSERT INTO information_request_template_version_capability
                    (id, template_version_id, capability_key, required_contract_version)
                SELECT gen_random_uuid(), version.id, required.capability_key, 1
                FROM information_request_template_version version
                         CROSS JOIN request_template_required_capabilities(version.id) required
                WHERE version.id = ?
                """.trimIndent(),
                templateVersionId,
            )
            execute(
                connection,
                """
                UPDATE information_request_template_version
                SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
                WHERE id = ?
                """.trimIndent(),
                now,
                userId,
                templateVersionId,
            )
        }

        private fun insertRequest(connection: Connection, id: UUID, now: Timestamp)
        {
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                     state, gates_exchange_closure, aggregate_revision, party_revision,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
                """.trimIndent(),
                id,
                exchangeId,
                templateVersionId,
                organizationId,
                now,
                now,
            )
        }

        private fun insertRuntimeRequirement(
            connection: Connection,
            id: UUID,
            request: UUID,
            occurrencePath: String,
            now: Timestamp,
        )
        {
            execute(
                connection,
                """
                INSERT INTO information_request_requirement
                    (id, information_request_id, source_template_version_id,
                     source_template_requirement_id, source_template_binding_id, occurrence_path,
                     created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id,
                request,
                templateVersionId,
                templateRequirementId,
                templateBindingId,
                occurrencePath,
                now,
            )
        }

        private fun execute(connection: Connection, sql: String, vararg values: Any)
        {
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }
}
