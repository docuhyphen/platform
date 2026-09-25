package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class InformationRequestEvidencePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<InformationRequestEvidencePostgreSQLContainer>(imageName)

class InformationRequestEvidenceArtifactContractTest
{
    @Test
    fun `an evidence artifact is bound to one runtime Requirement occurrence of its own request`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()

                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                assertEquals(1, artifactCount(connection, fixture.requestId))
                assertEquals(1L, artifactRevision(connection, artifactId))

                refused(connection, "information_request_evidence_artifact_requirement_fkey") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.otherRequestRequirementId,
                        "record-2",
                    )
                }
                refused(connection, "information_request_evidence_artifact_requirement_fkey") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        UUID.randomUUID(),
                        "record-3",
                    )
                }
                refused(connection, "ux_information_request_evidence_artifact_key") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        "record-1",
                    )
                }
                refused(connection, "ck_information_request_evidence_artifact_key") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        "  ",
                    )
                }
            }
        }
    }

    @Test
    fun `one Requirement occurrence aggregates several independently versioned artifacts`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val firstArtifactId = UUID.randomUUID()
                val secondArtifactId = UUID.randomUUID()

                insertArtifact(connection, firstArtifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertArtifact(connection, secondArtifactId, fixture.requestId, fixture.runtimeRequirementId, "record-2")

                insertFileVersion(
                    connection,
                    UUID.randomUUID(),
                    firstArtifactId,
                    fixture.requestId,
                    1,
                    fixture.documentVersionId,
                )
                insertExternalVersion(
                    connection,
                    UUID.randomUUID(),
                    secondArtifactId,
                    fixture.requestId,
                    1,
                    "record_reference",
                    "external:record/1",
                )

                assertEquals(2, artifactCount(connection, fixture.requestId))
                assertEquals(1, versionCount(connection, firstArtifactId))
                assertEquals(1, versionCount(connection, secondArtifactId))
            }
        }
    }

    @Test
    fun `an evidence version names exactly one typed source`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                refused(connection, "ck_information_request_evidence_version_source") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        "DOCUMENT_VERSION",
                        fixture.documentVersionId,
                        "record_reference",
                        "external:record/1",
                    )
                }
                refused(connection, "ck_information_request_evidence_version_source") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        "DOCUMENT_VERSION",
                        null,
                        null,
                        null,
                    )
                }
                refused(connection, "ck_information_request_evidence_version_source") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        "EXTERNAL_REFERENCE",
                        fixture.documentVersionId,
                        "record_reference",
                        "external:record/1",
                    )
                }
                refused(connection, "ck_information_request_evidence_version_source") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        "EXTERNAL_REFERENCE",
                        null,
                        "record_reference",
                        "   ",
                    )
                }
                refused(connection, "ck_information_request_evidence_version_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        "INLINE_VALUE",
                        null,
                        null,
                        null,
                    )
                }
                refused(connection, "information_request_evidence_version_document_version_fkey") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        1,
                        UUID.randomUUID(),
                    )
                }
                refused(connection, "information_request_evidence_version_artifact_fkey") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.otherRequestId,
                        1,
                        fixture.documentVersionId,
                    )
                }
            }
        }
    }

    @Test
    fun `evidence versions append in contiguous order for each artifact`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                insertFileVersion(
                    connection,
                    UUID.randomUUID(),
                    artifactId,
                    fixture.requestId,
                    1,
                    fixture.documentVersionId,
                )
                insertExternalVersion(
                    connection,
                    UUID.randomUUID(),
                    artifactId,
                    fixture.requestId,
                    2,
                    "record_reference",
                    "external:record/1",
                )

                assertEquals(2, versionCount(connection, artifactId))

                refused(connection, "ux_information_request_evidence_version_number") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        2,
                        fixture.documentVersionId,
                    )
                }
                refused(connection, "contiguous") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        4,
                        fixture.documentVersionId,
                    )
                }
                refused(connection, "ck_information_request_evidence_version_number") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        0,
                        fixture.documentVersionId,
                    )
                }
            }
        }
    }

    @Test
    fun `stored evidence versions are immutable`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertFileVersion(connection, versionId, artifactId, fixture.requestId, 1, fixture.documentVersionId)

                refused(connection, "append-only") {
                    connection.prepareStatement(
                        """
                        UPDATE information_request_evidence_version
                        SET source_kind = 'EXTERNAL_REFERENCE'
                        WHERE id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, versionId)
                        statement.executeUpdate()
                    }
                }
                refused(connection, "append-only") {
                    connection.prepareStatement(
                        "DELETE FROM information_request_evidence_version WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, versionId)
                        statement.executeUpdate()
                    }
                }

                assertEquals(1, versionCount(connection, artifactId))
            }
        }
    }

    @Test
    fun `an artifact states its creator and starts active with no recorded state change`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()

                insertArtifact(
                    connection,
                    artifactId,
                    fixture.requestId,
                    fixture.runtimeRequirementId,
                    "record-1",
                    creatorKind = "PARTICIPANT",
                    creatorId = fixture.userId,
                )

                assertEquals("PARTICIPANT", artifactValue(connection, artifactId, "created_by_principal_kind"))
                assertEquals(fixture.userId.toString(), artifactValue(connection, artifactId, "created_by_principal_id"))
                assertEquals("ACTIVE", artifactValue(connection, artifactId, "collection_state"))
                assertNull(artifactValue(connection, artifactId, "state_changed_at"))

                refused(connection, "\"created_by_principal_kind\"") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        "record-2",
                        creatorKind = null,
                    )
                }
                refused(connection, "\"created_by_principal_id\"") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        "record-3",
                        creatorId = null,
                    )
                }
                refused(connection, "ck_information_request_evidence_artifact_creator_kind") {
                    insertArtifact(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.runtimeRequirementId,
                        "record-4",
                        creatorKind = "ANONYMOUS",
                    )
                }
            }
        }
    }

    @Test
    fun `a collection state change states who made it and when`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                refused(connection, "ck_information_request_evidence_artifact_state_change") {
                    updateArtifact(connection, artifactId, "collection_state = 'WITHDRAWN'")
                }
                refused(connection, "ck_information_request_evidence_artifact_state_change") {
                    updateArtifact(connection, artifactId, "state_changed_at = CURRENT_TIMESTAMP")
                }
                refused(connection, "ck_information_request_evidence_artifact_state_value") {
                    updateArtifact(connection, artifactId, stateChange("ARCHIVED", fixture.userId, "Recorded in error"))
                }
                refused(connection, "ck_information_request_evidence_artifact_state_reason") {
                    updateArtifact(connection, artifactId, stateChange("WITHDRAWN", fixture.userId, "  "))
                }

                updateArtifact(connection, artifactId, stateChange("WITHDRAWN", fixture.userId, "Recorded in error"))

                assertEquals("WITHDRAWN", artifactValue(connection, artifactId, "collection_state"))
                assertEquals(
                    fixture.userId.toString(),
                    artifactValue(connection, artifactId, "state_changed_by_principal_id"),
                )
                assertEquals("Recorded in error", artifactValue(connection, artifactId, "state_reason"))
            }
        }
    }

    @Test
    fun `an artifact never returns to active and a removal is final`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val withdrawnId = UUID.randomUUID()
                val removedId = UUID.randomUUID()
                insertArtifact(connection, withdrawnId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertArtifact(connection, removedId, fixture.requestId, fixture.runtimeRequirementId, "record-2")
                updateArtifact(connection, withdrawnId, stateChange("WITHDRAWN", fixture.userId, null))
                updateArtifact(connection, removedId, stateChange("REMOVED", fixture.userId, null))

                refused(connection, "evidence artifact state cannot move from WITHDRAWN to ACTIVE") {
                    updateArtifact(
                        connection,
                        withdrawnId,
                        "collection_state = 'ACTIVE', state_changed_at = NULL, " +
                            "state_changed_by_principal_kind = NULL, state_changed_by_principal_id = NULL",
                    )
                }
                refused(connection, "evidence artifact state cannot move from REMOVED to WITHDRAWN") {
                    updateArtifact(connection, removedId, stateChange("WITHDRAWN", fixture.userId, null))
                }

                updateArtifact(connection, withdrawnId, stateChange("REMOVED", fixture.userId, "No longer relevant"))

                assertEquals("REMOVED", artifactValue(connection, withdrawnId, "collection_state"))
            }
        }
    }

    @Test
    fun `an artifact keeps its identity and creator, never lowers its revision, and is never deleted`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                updateArtifact(connection, artifactId, "artifact_revision = 3")

                listOf(
                    "artifact_key = 'record-renamed'",
                    "created_by_principal_id = '${UUID.randomUUID()}'::uuid",
                    "created_by_principal_kind = 'APPLICATION'",
                    "created_at = created_at - INTERVAL '1 day'",
                ).forEach { assignment ->
                    refused(connection, "evidence artifact identity is immutable") {
                        updateArtifact(connection, artifactId, assignment)
                    }
                }
                refused(connection, "evidence artifact revision cannot move backwards") {
                    updateArtifact(connection, artifactId, "artifact_revision = 2")
                }
                refused(connection, "evidence artifact is retained") {
                    connection.prepareStatement("DELETE FROM information_request_evidence_artifact WHERE id = ?")
                        .use { statement ->
                            statement.setObject(1, artifactId)
                            statement.executeUpdate()
                        }
                }

                assertEquals(3L, artifactRevision(connection, artifactId))
            }
        }
    }

    @Test
    fun `a version states its uploader and a file-backed version states its declared file name`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                insertVersion(
                    connection,
                    versionId,
                    artifactId,
                    fixture.requestId,
                    1,
                    "DOCUMENT_VERSION",
                    fixture.documentVersionId,
                    null,
                    null,
                    uploaderKind = "PARTICIPANT",
                    uploaderId = fixture.userId,
                    sessionRef = "verified-session",
                    declaredFileName = "record.pdf",
                    declaredMediaType = "application/pdf",
                )

                assertEquals("PARTICIPANT", versionValue(connection, versionId, "created_by_principal_kind"))
                assertEquals("verified-session", versionValue(connection, versionId, "created_by_session_ref"))
                assertEquals("record.pdf", versionValue(connection, versionId, "declared_file_name"))
                assertEquals("application/pdf", versionValue(connection, versionId, "declared_media_type"))

                refused(connection, "\"created_by_principal_kind\"") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        2,
                        "DOCUMENT_VERSION",
                        fixture.documentVersionId,
                        null,
                        null,
                        uploaderKind = null,
                    )
                }
                refused(connection, "ck_information_request_evidence_version_creator_kind") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        2,
                        "DOCUMENT_VERSION",
                        fixture.documentVersionId,
                        null,
                        null,
                        uploaderKind = "ANONYMOUS",
                    )
                }
                refused(connection, "ck_information_request_evidence_version_declared_file") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        2,
                        "DOCUMENT_VERSION",
                        fixture.documentVersionId,
                        null,
                        null,
                        declaredFileName = null,
                    )
                }
                refused(connection, "ck_information_request_evidence_version_declared_file") {
                    insertVersion(
                        connection,
                        UUID.randomUUID(),
                        artifactId,
                        fixture.requestId,
                        2,
                        "EXTERNAL_REFERENCE",
                        null,
                        "process-register",
                        "REG-4821",
                        declaredFileName = "record.pdf",
                    )
                }
            }
        }
    }

    @Test
    fun `captured attributes are stated in order and never blank`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")

                insertVersion(
                    connection,
                    versionId,
                    artifactId,
                    fixture.requestId,
                    1,
                    "DOCUMENT_VERSION",
                    fixture.documentVersionId,
                    null,
                    null,
                    attributes = mapOf(
                        "issuer" to "Process Registry",
                        "jurisdiction" to "ZZ",
                        "language" to "en",
                        "issued_on" to "2026-01-10",
                        "expires_on" to "2027-01-10",
                        "coverage_starts_on" to "2026-01-01",
                        "coverage_ends_on" to "2026-06-30",
                        "certification_reference" to "CERT-7",
                        "signature_reference" to "SIG-7",
                    ),
                )

                assertEquals("Process Registry", versionValue(connection, versionId, "issuer"))
                assertEquals("2026-06-30", versionValue(connection, versionId, "coverage_ends_on"))

                refused(connection, "ck_information_request_evidence_version_validity") {
                    attributedVersion(connection, fixture, artifactId, "issued_on" to "2026-02-01", "expires_on" to "2026-01-31")
                }
                refused(connection, "ck_information_request_evidence_version_coverage") {
                    attributedVersion(
                        connection,
                        fixture,
                        artifactId,
                        "coverage_starts_on" to "2026-03-01",
                        "coverage_ends_on" to "2026-02-28",
                    )
                }
                refused(connection, "ck_information_request_evidence_version_coverage") {
                    attributedVersion(connection, fixture, artifactId, "coverage_starts_on" to "2026-03-01")
                }
                listOf("issuer", "jurisdiction", "language", "certification_reference", "signature_reference")
                    .forEach { column ->
                        refused(connection, "ck_information_request_evidence_version_attribute_text") {
                            attributedVersion(connection, fixture, artifactId, column to "   ")
                        }
                    }
            }
        }
    }

    @Test
    fun `a version is appended only to an active artifact`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val withdrawnId = UUID.randomUUID()
                val removedId = UUID.randomUUID()
                insertArtifact(connection, withdrawnId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertArtifact(connection, removedId, fixture.requestId, fixture.runtimeRequirementId, "record-2")
                insertFileVersion(
                    connection,
                    UUID.randomUUID(),
                    withdrawnId,
                    fixture.requestId,
                    1,
                    fixture.documentVersionId,
                )
                updateArtifact(connection, withdrawnId, stateChange("WITHDRAWN", fixture.userId, null))
                updateArtifact(connection, removedId, stateChange("REMOVED", fixture.userId, null))

                refused(connection, "evidence versions are appended only to an active artifact") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        withdrawnId,
                        fixture.requestId,
                        2,
                        fixture.documentVersionId,
                    )
                }
                refused(connection, "evidence versions are appended only to an active artifact") {
                    insertFileVersion(
                        connection,
                        UUID.randomUUID(),
                        removedId,
                        fixture.requestId,
                        1,
                        fixture.documentVersionId,
                    )
                }

                assertEquals(1, versionCount(connection, withdrawnId))
            }
        }
    }

    @Test
    fun `an assessment records the exact bytes of its file-backed version`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val versionId = fileBackedVersion(connection, fixture)
                val inspectionId = UUID.randomUUID()
                val scanId = UUID.randomUUID()

                insertAssessment(
                    connection,
                    inspectionId,
                    fixture.requestId,
                    versionId,
                    "CONTENT_INSPECTION",
                    "INSPECTED",
                    columns = mapOf("detected_media_type" to "application/pdf", "page_count" to 3),
                )
                insertAssessment(
                    connection,
                    scanId,
                    fixture.requestId,
                    versionId,
                    "MALWARE_SCAN",
                    "CLEAN",
                    columns = mapOf(
                        "engine_name" to "process-scanner",
                        "engine_version" to "1.0",
                        "signature_version" to "2026.09.25",
                        "production_eligible" to true,
                    ),
                )

                assertEquals("INSPECTED", assessmentValue(connection, inspectionId, "outcome"))
                assertEquals("3", assessmentValue(connection, inspectionId, "page_count"))
                assertEquals("CLEAN", assessmentValue(connection, scanId, "outcome"))
                assertEquals("t", assessmentValue(connection, scanId, "production_eligible"))
            }
        }
    }

    @Test
    fun `an assessment of bytes other than its version's is refused`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val versionId = fileBackedVersion(connection, fixture)

                refused(connection, "an evidence assessment describes the exact bytes of its version") {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        versionId,
                        "CONTENT_INSPECTION",
                        "INSPECTED",
                        hash = "0".repeat(64),
                    )
                }
                refused(connection, "an evidence assessment describes the exact bytes of its version") {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        versionId,
                        "CONTENT_INSPECTION",
                        "INSPECTED",
                        length = 4,
                    )
                }
            }
        }
    }

    @Test
    fun `an external reference is never assessed as content`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val artifactId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertExternalVersion(connection, versionId, artifactId, fixture.requestId, 1, "process-register", "REG-4821")

                refused(connection, "evidence assessments describe file-backed evidence only") {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "CONTENT_INSPECTION", "INSPECTED")
                }
            }
        }
    }

    @Test
    fun `assessment kinds and outcomes come from their closed vocabularies`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val versionId = fileBackedVersion(connection, fixture)

                refused(connection, "ck_information_request_evidence_assessment_kind") {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "POLICY_REVIEW", "INSPECTED")
                }
                refused(connection, "ck_information_request_evidence_assessment_outcome") {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "CONTENT_INSPECTION", "CLEAN")
                }
                refused(connection, "ck_information_request_evidence_assessment_outcome") {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "MALWARE_SCAN", "INSPECTED")
                }
            }
        }
    }

    @Test
    fun `a settled scan names its engine and signatures and an inspection names none`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val versionId = fileBackedVersion(connection, fixture)

                refused(connection, "ck_information_request_evidence_assessment_scan_engine") {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "MALWARE_SCAN", "CLEAN")
                }
                refused(connection, "ck_information_request_evidence_assessment_scan_engine") {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        versionId,
                        "MALWARE_SCAN",
                        "MALWARE_DETECTED",
                        columns = mapOf("engine_name" to "process-scanner", "engine_version" to "1.0"),
                    )
                }
                refused(connection, "ck_information_request_evidence_assessment_inspection") {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        versionId,
                        "CONTENT_INSPECTION",
                        "INSPECTED",
                        columns = mapOf("production_eligible" to true),
                    )
                }

                insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "MALWARE_SCAN", "UNAVAILABLE")
            }
        }
    }

    @Test
    fun `a reused assessment describes the same kind of assessment of the same bytes`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val firstVersionId = fileBackedVersion(connection, fixture, "record-1")
                val secondVersionId = fileBackedVersion(connection, fixture, "record-2")
                val sourceScanId = UUID.randomUUID()
                val inspectionId = UUID.randomUUID()
                val engine = mapOf(
                    "engine_name" to "process-scanner",
                    "engine_version" to "1.0",
                    "signature_version" to "2026.09.25",
                    "production_eligible" to true,
                )
                insertAssessment(connection, sourceScanId, fixture.requestId, firstVersionId, "MALWARE_SCAN", "CLEAN", columns = engine)
                insertAssessment(connection, inspectionId, fixture.requestId, firstVersionId, "CONTENT_INSPECTION", "INSPECTED")

                insertAssessment(
                    connection,
                    UUID.randomUUID(),
                    fixture.requestId,
                    secondVersionId,
                    "MALWARE_SCAN",
                    "CLEAN",
                    columns = engine + ("reused_assessment_id" to sourceScanId),
                )
                refused(connection, "a reused evidence assessment describes the same kind of assessment of the same bytes") {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        secondVersionId,
                        "MALWARE_SCAN",
                        "CLEAN",
                        columns = engine + ("reused_assessment_id" to inspectionId),
                    )
                }
            }
        }
    }

    @Test
    fun `a reused scan repeats the settled outcome, engine, and signatures it reuses`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val firstVersionId = fileBackedVersion(connection, fixture, "record-1")
                val secondVersionId = fileBackedVersion(connection, fixture, "record-2")
                val cleanScanId = UUID.randomUUID()
                val unavailableScanId = UUID.randomUUID()
                val engine = mapOf(
                    "engine_name" to "process-scanner",
                    "engine_version" to "1.0",
                    "signature_version" to "2026.09.25",
                    "production_eligible" to true,
                )
                insertAssessment(connection, cleanScanId, fixture.requestId, firstVersionId, "MALWARE_SCAN", "CLEAN", columns = engine)
                insertAssessment(connection, unavailableScanId, fixture.requestId, firstVersionId, "MALWARE_SCAN", "UNAVAILABLE")

                val refusal = "a reused evidence assessment repeats the settled outcome, engine, and signatures it reuses"
                refused(connection, refusal) {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        secondVersionId,
                        "MALWARE_SCAN",
                        "MALWARE_DETECTED",
                        columns = engine + ("reused_assessment_id" to cleanScanId),
                    )
                }
                refused(connection, refusal) {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        secondVersionId,
                        "MALWARE_SCAN",
                        "CLEAN",
                        columns = engine + mapOf("signature_version" to "2026.09.26", "reused_assessment_id" to cleanScanId),
                    )
                }
                refused(connection, refusal) {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        secondVersionId,
                        "MALWARE_SCAN",
                        "UNAVAILABLE",
                        columns = mapOf("reused_assessment_id" to unavailableScanId),
                    )
                }
            }
        }
    }

    @Test
    fun `opaque content is never inspected and never scanned clean`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val opaqueDocumentVersionId = UUID.randomUUID()
                insertDocumentVersion(connection, opaqueDocumentVersionId, fixture.documentId, fixture.userId, "2", "UNVERIFIED")
                val artifactId = UUID.randomUUID()
                val versionId = UUID.randomUUID()
                insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, "record-1")
                insertFileVersion(connection, versionId, artifactId, fixture.requestId, 1, opaqueDocumentVersionId)

                val refusal = "opaque evidence content is never inspected or scanned clean"
                refused(connection, refusal) {
                    insertAssessment(connection, UUID.randomUUID(), fixture.requestId, versionId, "CONTENT_INSPECTION", "INSPECTED")
                }
                refused(connection, refusal) {
                    insertAssessment(
                        connection,
                        UUID.randomUUID(),
                        fixture.requestId,
                        versionId,
                        "MALWARE_SCAN",
                        "CLEAN",
                        columns = mapOf(
                            "engine_name" to "process-scanner",
                            "engine_version" to "1.0",
                            "signature_version" to "2026.09.25",
                            "production_eligible" to true,
                        ),
                    )
                }

                val skippedId = UUID.randomUUID()
                insertAssessment(connection, skippedId, fixture.requestId, versionId, "MALWARE_SCAN", "SKIPPED")
                assertEquals("SKIPPED", assessmentValue(connection, skippedId, "outcome"))
            }
        }
    }

    @Test
    fun `recorded assessments are append-only`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                val versionId = fileBackedVersion(connection, fixture)
                val assessmentId = UUID.randomUUID()
                insertAssessment(connection, assessmentId, fixture.requestId, versionId, "MALWARE_SCAN", "UNAVAILABLE")

                refused(connection, "append-only") {
                    connection.prepareStatement(
                        "UPDATE information_request_evidence_assessment SET outcome = 'ERROR' WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, assessmentId)
                        statement.executeUpdate()
                    }
                }
                refused(connection, "append-only") {
                    connection.prepareStatement("DELETE FROM information_request_evidence_assessment WHERE id = ?").use { statement ->
                        statement.setObject(1, assessmentId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    @Test
    fun `a supporting evidence link joins a Requirement occurrence to a Document occurrence of its own request`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = LinkedFixture(connection)
                val linkId = UUID.randomUUID()

                insertSupportingLink(
                    connection, linkId, fixture.requestId, fixture.supportedRequirementId, fixture.documentRequirementId,
                    fixture.templateLinkId,
                )

                connection.prepareStatement(
                    "SELECT supported_requirement_id, supporting_requirement_id " +
                        "FROM information_request_supporting_evidence_link WHERE id = ?",
                ).use { statement ->
                    statement.setObject(1, linkId)
                    statement.executeQuery().use {
                        assertTrue(it.next())
                        assertEquals(fixture.supportedRequirementId.toString(), it.getString(1))
                        assertEquals(fixture.documentRequirementId.toString(), it.getString(2))
                    }
                }
            }
        }
    }

    @Test
    fun `a supporting evidence link follows its Template link between the exact bindings of its own request`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = LinkedFixture(connection)
                val refusal = "a supporting evidence link follows its Template link between the exact bindings of its request"

                refused(connection, refusal) {
                    insertSupportingLink(
                        connection, UUID.randomUUID(), fixture.requestId, fixture.documentRequirementId,
                        fixture.supportedRequirementId, fixture.templateLinkId,
                    )
                }
                refused(connection, refusal) {
                    insertSupportingLink(
                        connection, UUID.randomUUID(), fixture.requestId, fixture.supportedRequirementId,
                        fixture.supportedRequirementId, fixture.templateLinkId,
                    )
                }
                refused(connection, "information_request_supporting_evidence_link_supporting_fkey") {
                    insertSupportingLink(
                        connection, UUID.randomUUID(), fixture.requestId, fixture.supportedRequirementId,
                        fixture.otherRequestDocumentRequirementId, fixture.templateLinkId,
                    )
                }
            }
        }
    }

    @Test
    fun `a supporting evidence link is recorded once and never rewritten or deleted`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val fixture = LinkedFixture(connection)
                val linkId = UUID.randomUUID()
                insertSupportingLink(
                    connection, linkId, fixture.requestId, fixture.supportedRequirementId, fixture.documentRequirementId,
                    fixture.templateLinkId,
                )

                refused(connection, "ux_information_request_supporting_evidence_link_pair") {
                    insertSupportingLink(
                        connection, UUID.randomUUID(), fixture.requestId, fixture.supportedRequirementId,
                        fixture.documentRequirementId, fixture.templateLinkId,
                    )
                }
                refused(connection, "append-only") {
                    connection.prepareStatement(
                        "UPDATE information_request_supporting_evidence_link SET created_at = CURRENT_TIMESTAMP WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, linkId)
                        statement.executeUpdate()
                    }
                }
                refused(connection, "append-only") {
                    connection.prepareStatement(
                        "DELETE FROM information_request_supporting_evidence_link WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, linkId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
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
        val runtimeRequirementId: UUID = UUID.randomUUID()
        val otherRequestRequirementId: UUID = UUID.randomUUID()
        val documentId: UUID = UUID.randomUUID()
        val documentVersionId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertTemplateVersion(connection, templateVersionId, definitionId, 1)
            insertTemplateRequirement(connection, templateRequirementId, definitionId)
            insertTemplateSection(connection, sectionId, templateVersionId)
            insertTemplateBinding(
                connection,
                templateBindingId,
                templateVersionId,
                definitionId,
                templateRequirementId,
                sectionId,
            )
            publishVersion(connection, templateVersionId, userId)
            insertRequest(connection, requestId, exchangeId, templateVersionId, organizationId)
            insertRequest(connection, otherRequestId, exchangeId, templateVersionId, organizationId)
            insertRuntimeRequirement(
                connection,
                runtimeRequirementId,
                requestId,
                templateVersionId,
                templateRequirementId,
                templateBindingId,
                "root",
            )
            insertRuntimeRequirement(
                connection,
                otherRequestRequirementId,
                otherRequestId,
                templateVersionId,
                templateRequirementId,
                templateBindingId,
                "root",
            )
            insertDocument(connection, documentId)
            insertDocumentVersion(connection, documentVersionId, documentId, userId)
        }
    }

    private inner class LinkedFixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val templateVersionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val supportedTemplateRequirementId: UUID = UUID.randomUUID()
        val documentTemplateRequirementId: UUID = UUID.randomUUID()
        val supportedBindingId: UUID = UUID.randomUUID()
        val documentBindingId: UUID = UUID.randomUUID()
        val templateLinkId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()
        val otherRequestId: UUID = UUID.randomUUID()
        val supportedRequirementId: UUID = UUID.randomUUID()
        val documentRequirementId: UUID = UUID.randomUUID()
        val otherRequestDocumentRequirementId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertTemplateVersion(connection, templateVersionId, definitionId, 1)
            insertTemplateRequirement(connection, supportedTemplateRequirementId, definitionId)
            insertTemplateRequirement(connection, documentTemplateRequirementId, definitionId, "supporting-record", "DOCUMENT")
            insertTemplateSection(connection, sectionId, templateVersionId)
            insertTemplateBinding(
                connection, supportedBindingId, templateVersionId, definitionId, supportedTemplateRequirementId, sectionId,
            )
            insertTemplateBinding(
                connection, documentBindingId, templateVersionId, definitionId, documentTemplateRequirementId, sectionId, 2,
            )
            insertDocumentPolicy(connection, documentBindingId, templateVersionId)
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_binding_evidence_link
                    (id, template_binding_id, supporting_template_binding_id, template_version_id)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, templateLinkId)
                statement.setObject(2, supportedBindingId)
                statement.setObject(3, documentBindingId)
                statement.setObject(4, templateVersionId)
                statement.executeUpdate()
            }
            publishVersion(connection, templateVersionId, userId)
            insertRequest(connection, requestId, exchangeId, templateVersionId, organizationId)
            insertRequest(connection, otherRequestId, exchangeId, templateVersionId, organizationId)
            insertRuntimeRequirement(
                connection, supportedRequirementId, requestId, templateVersionId, supportedTemplateRequirementId,
                supportedBindingId, "root",
            )
            insertRuntimeRequirement(
                connection, documentRequirementId, requestId, templateVersionId, documentTemplateRequirementId,
                documentBindingId, "root",
            )
            insertRuntimeRequirement(
                connection, otherRequestDocumentRequirementId, otherRequestId, templateVersionId,
                documentTemplateRequirementId, documentBindingId, "root",
            )
        }
    }

    private fun insertDocumentPolicy(connection: Connection, bindingId: UUID, versionId: UUID)
    {
        connection.prepareStatement(
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
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, bindingId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertSupportingLink(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        supportedRequirementId: UUID,
        supportingRequirementId: UUID,
        templateLinkId: UUID,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_supporting_evidence_link
                (id, information_request_id, supported_requirement_id, supporting_requirement_id,
                 template_evidence_link_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, supportedRequirementId)
            statement.setObject(4, supportingRequirementId)
            statement.setObject(5, templateLinkId)
            statement.setTimestamp(6, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun withPostgres(block: (InformationRequestEvidencePostgreSQLContainer) -> Unit)
    {
        val postgres = InformationRequestEvidencePostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_information_request_evidence_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")
        postgres.start()
        try
        {
            block(postgres)
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: InformationRequestEvidencePostgreSQLContainer): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()

    private fun refused(connection: Connection, expected: String, block: () -> Unit)
    {
        connection.autoCommit = false
        val refusal = try
        {
            assertThrows<SQLException>(block)
        }
        finally
        {
            connection.rollback()
            connection.autoCommit = true
        }
        assertTrue(
            refusal.message.orEmpty().contains(expected),
            "Expected $expected to refuse this statement: ${refusal.message}",
        )
    }

    private fun insertArtifact(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        requirementId: UUID,
        artifactKey: String,
        creatorKind: String? = "USER",
        creatorId: UUID? = UUID.randomUUID(),
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_evidence_artifact
                (id, information_request_id, information_request_requirement_id, artifact_key,
                 artifact_revision, created_at, updated_at, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, requirementId)
            statement.setString(4, artifactKey)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.setString(7, creatorKind)
            statement.setObject(8, creatorId)
            statement.executeUpdate()
        }
    }

    private fun updateArtifact(connection: Connection, artifactId: UUID, assignments: String)
    {
        connection.prepareStatement("UPDATE information_request_evidence_artifact SET $assignments WHERE id = ?")
            .use { statement ->
                statement.setObject(1, artifactId)
                statement.executeUpdate()
            }
    }

    private fun stateChange(state: String, principalId: UUID, reason: String?): String =
        "collection_state = '$state', state_changed_at = CURRENT_TIMESTAMP, " +
            "state_changed_by_principal_kind = 'USER', state_changed_by_principal_id = '$principalId'::uuid, " +
            "state_reason = ${reason?.let { "'$it'" } ?: "NULL"}"

    private fun artifactValue(connection: Connection, artifactId: UUID, column: String): String? =
        connection.prepareStatement("SELECT $column FROM information_request_evidence_artifact WHERE id = ?")
            .use { statement ->
                statement.setObject(1, artifactId)
                statement.executeQuery().use {
                    check(it.next()) { "No evidence artifact stored for $artifactId" }
                    it.getString(1)
                }
            }

    private fun versionValue(connection: Connection, versionId: UUID, column: String): String? =
        connection.prepareStatement("SELECT $column FROM information_request_evidence_version WHERE id = ?")
            .use { statement ->
                statement.setObject(1, versionId)
                statement.executeQuery().use {
                    check(it.next()) { "No evidence version stored for $versionId" }
                    it.getString(1)
                }
            }

    private fun attributedVersion(
        connection: Connection,
        fixture: Fixture,
        artifactId: UUID,
        vararg attributes: Pair<String, String>,
    )
    {
        insertVersion(
            connection,
            UUID.randomUUID(),
            artifactId,
            fixture.requestId,
            2,
            "DOCUMENT_VERSION",
            fixture.documentVersionId,
            null,
            null,
            attributes = attributes.toMap(),
        )
    }

    private fun insertFileVersion(
        connection: Connection,
        id: UUID,
        artifactId: UUID,
        requestId: UUID,
        versionNumber: Int,
        documentVersionId: UUID,
    )
    {
        insertVersion(
            connection,
            id,
            artifactId,
            requestId,
            versionNumber,
            "DOCUMENT_VERSION",
            documentVersionId,
            null,
            null,
        )
    }

    private fun insertExternalVersion(
        connection: Connection,
        id: UUID,
        artifactId: UUID,
        requestId: UUID,
        versionNumber: Int,
        referenceType: String,
        referenceValue: String,
    )
    {
        insertVersion(
            connection,
            id,
            artifactId,
            requestId,
            versionNumber,
            "EXTERNAL_REFERENCE",
            null,
            referenceType,
            referenceValue,
        )
    }

    private fun insertVersion(
        connection: Connection,
        id: UUID,
        artifactId: UUID,
        requestId: UUID,
        versionNumber: Int,
        sourceKind: String,
        documentVersionId: UUID?,
        referenceType: String?,
        referenceValue: String?,
        uploaderKind: String? = "USER",
        uploaderId: UUID? = UUID.randomUUID(),
        sessionRef: String? = null,
        declaredFileName: String? = if (sourceKind == "DOCUMENT_VERSION") "process-record.pdf" else null,
        declaredMediaType: String? = null,
        attributes: Map<String, String> = emptyMap(),
    )
    {
        val attributeColumns = attributes.keys.toList()
        val columns = listOf(
            "id", "evidence_artifact_id", "information_request_id", "version_number", "source_kind",
            "document_version_id", "external_reference_type", "external_reference_value", "created_at",
            "created_by_principal_kind", "created_by_principal_id", "created_by_session_ref",
            "declared_file_name", "declared_media_type",
        ) + attributeColumns
        connection.prepareStatement(
            "INSERT INTO information_request_evidence_version (${columns.joinToString(", ")}) " +
                "VALUES (${columns.joinToString(", ") { "?" }})",
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, artifactId)
            statement.setObject(3, requestId)
            statement.setInt(4, versionNumber)
            statement.setString(5, sourceKind)
            statement.setObject(6, documentVersionId)
            statement.setString(7, referenceType)
            statement.setString(8, referenceValue)
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            statement.setString(10, uploaderKind)
            statement.setObject(11, uploaderId)
            statement.setString(12, sessionRef)
            statement.setString(13, declaredFileName)
            statement.setString(14, declaredMediaType)
            attributeColumns.forEachIndexed { index, column ->
                val value = attributes.getValue(column)
                if (column.endsWith("_on")) statement.setDate(15 + index, java.sql.Date.valueOf(value))
                else statement.setString(15 + index, value)
            }
            statement.executeUpdate()
        }
    }

    private fun fileBackedVersion(connection: Connection, fixture: Fixture, artifactKey: String = "record-1"): UUID
    {
        val artifactId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        insertArtifact(connection, artifactId, fixture.requestId, fixture.runtimeRequirementId, artifactKey)
        insertFileVersion(connection, versionId, artifactId, fixture.requestId, 1, fixture.documentVersionId)
        return versionId
    }

    private fun insertAssessment(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        versionId: UUID,
        kind: String,
        outcome: String,
        hash: String = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        length: Long = 3,
        columns: Map<String, Any> = emptyMap(),
    )
    {
        val names = listOf(
            "id", "information_request_id", "evidence_version_id", "assessment_kind", "outcome",
            "content_hash_algorithm", "content_hash", "content_length", "assessed_at",
        ) + columns.keys
        connection.prepareStatement(
            "INSERT INTO information_request_evidence_assessment (${names.joinToString(", ")}) " +
                "VALUES (${names.joinToString(", ") { "?" }})",
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, versionId)
            statement.setString(4, kind)
            statement.setString(5, outcome)
            statement.setString(6, "SHA_256")
            statement.setString(7, hash)
            statement.setLong(8, length)
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            columns.values.forEachIndexed { index, value -> statement.setObject(10 + index, value) }
            statement.executeUpdate()
        }
    }

    private fun assessmentValue(connection: Connection, assessmentId: UUID, column: String): String? =
        connection.prepareStatement("SELECT $column FROM information_request_evidence_assessment WHERE id = ?")
            .use { statement ->
                statement.setObject(1, assessmentId)
                statement.executeQuery().use {
                    check(it.next()) { "No evidence assessment stored for $assessmentId" }
                    it.getString(1)
                }
            }

    private fun insertOrganization(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "Evidence Process Owner")
            statement.setString(3, "REG-${id.toString().take(8)}")
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, "evidence-owner-${id.toString().take(8)}@process.test")
            statement.executeUpdate()
        }
    }

    private fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO exchange
                (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                 created_date, last_activity, description, initial_share_message, name, status)
            VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                    'Process collection', 'ACCEPTED_STARTED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setObject(3, userId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                 status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern', 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, "collection-${id.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateVersion(connection: Connection, id: UUID, definitionId: UUID, versionNumber: Int)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, ?, 'DRAFT', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setInt(3, versionNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertTemplateSection(connection: Connection, id: UUID, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_section
                (id, template_version_id, section_key, display_order, title)
            VALUES (?, ?, 'records', 1, 'Records')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertTemplateRequirement(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        key: String = "recorded-assertion",
        type: String = "RESPONSE_ATTESTATION",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement
                (id, template_definition_id, requirement_key, requirement_type, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, key)
            statement.setString(4, type)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertTemplateBinding(
        connection: Connection,
        id: UUID,
        versionId: UUID,
        definitionId: UUID,
        requirementId: UUID,
        sectionId: UUID,
        displayOrder: Int = 1,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_requirement_binding
                (id, template_version_id, template_definition_id, template_requirement_id,
                 template_section_id, display_order, prompt, response_mode, requiredness,
                 contributor_role, review_policy)
            VALUES (?, ?, ?, ?, ?, ?, 'State the response', 'PROVIDE', 'REQUIRED',
                    'CONTRIBUTOR', 'NOT_REQUIRED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.setObject(3, definitionId)
            statement.setObject(4, requirementId)
            statement.setObject(5, sectionId)
            statement.setInt(6, displayOrder)
            statement.executeUpdate()
        }
    }

    private fun publishVersion(connection: Connection, versionId: UUID, actorId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version_capability
                (id, template_version_id, capability_key, required_contract_version)
            SELECT gen_random_uuid(), version.id, required.capability_key, 1
            FROM information_request_template_version version
                     CROSS JOIN request_template_required_capabilities(version.id) required
            WHERE version.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, versionId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            UPDATE information_request_template_version
            SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, actorId)
            statement.setObject(3, versionId)
            statement.executeUpdate()
        }
    }

    private fun insertRequest(connection: Connection, id: UUID, exchangeId: UUID, versionId: UUID, ownerId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                 state, gates_exchange_closure, aggregate_revision, party_revision,
                 created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, id)
            statement.setObject(2, exchangeId)
            statement.setObject(3, versionId)
            statement.setObject(4, ownerId)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertRuntimeRequirement(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        versionId: UUID,
        requirementId: UUID,
        bindingId: UUID,
        occurrencePath: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, occurrence_path,
                 created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, requestId)
            statement.setObject(3, versionId)
            statement.setObject(4, requirementId)
            statement.setObject(5, bindingId)
            statement.setString(6, occurrencePath)
            statement.setTimestamp(7, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertDocument(connection: Connection, id: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO document
                (id, encryption_mode, is_deleted, created_date, update_date, title, type)
            VALUES (?, 0, FALSE, ?, ?, 'Process record', 'PDF')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, now)
            statement.executeUpdate()
        }
    }

    private fun insertDocumentVersion(
        connection: Connection,
        id: UUID,
        documentId: UUID,
        userId: UUID,
        version: String = "1",
        verification: String = "VERIFIED",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO document_version
                (id, document_id, created_by_principal_kind, created_by_principal_id, created_date, file_name,
                 version, storage_provider, storage_locator_kind, storage_locator,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, 'USER', ?, ?, 'process-record.pdf', ?,
                    'OBJECT_STORE', 'OBJECT_KEY', ?,
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, documentId)
            statement.setObject(3, userId)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.setString(5, version)
            statement.setString(6, "document-versions/$id/process-record.pdf")
            statement.setString(7, verification)
            statement.executeUpdate()
        }
    }

    private fun artifactCount(connection: Connection, requestId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_evidence_artifact WHERE information_request_id = ?",
            requestId,
        )

    private fun versionCount(connection: Connection, artifactId: UUID): Int =
        countOf(
            connection,
            "SELECT COUNT(*) FROM information_request_evidence_version WHERE evidence_artifact_id = ?",
            artifactId,
        )

    private fun artifactRevision(connection: Connection, artifactId: UUID): Long =
        connection.prepareStatement(
            "SELECT artifact_revision FROM information_request_evidence_artifact WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, artifactId)
            statement.executeQuery().use {
                it.next()
                it.getLong(1)
            }
        }

    private fun countOf(connection: Connection, sql: String, id: UUID): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use {
                it.next()
                it.getInt(1)
            }
        }
}
