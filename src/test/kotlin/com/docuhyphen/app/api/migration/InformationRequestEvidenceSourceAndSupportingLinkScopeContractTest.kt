package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestEvidenceSourceAndSupportingLinkScopeContractTest {
    @Test
    fun `an evidence version always names an exact Document Version and nothing else`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                assertEvidenceSourceShape(connection)

                refusedBy(connection, "document_version_id") {
                    insertEvidenceVersion(
                        connection,
                        fixture,
                        sourceKind = "DOCUMENT_VERSION",
                        documentVersionId = null
                    )
                }
                refusedBy(connection, "ck_information_request_evidence_version_kind") {
                    insertEvidenceVersion(connection, fixture, sourceKind = "EXTERNAL_REFERENCE")
                }
                refusedBy(connection, "ck_information_request_evidence_version_declared_file") {
                    insertEvidenceVersion(connection, fixture, declaredFileName = "   ")
                }
                refusedBy(connection, "ck_information_request_evidence_version_declared_file") {
                    insertEvidenceVersion(connection, fixture, declaredMediaType = " ")
                }
                insertEvidenceVersion(connection, fixture)
                assertEquals(
                    2,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_evidence_version WHERE evidence_artifact_id = ?",
                        fixture.artifactId,
                    ),
                )
            }
        }
    }

    @Test
    fun `a frozen supporting-link member stays inside its own request and names exactly its link's Requirements`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val own = SubmissionRuntimeSqlFixture(connection)
                val other = SubmissionRuntimeSqlFixture(connection)
                val ownLink = insertSupportingLink(connection, own)
                val otherLink = insertSupportingLink(connection, other)
                val packageId = UUID.randomUUID()
                own.insertPackage(packageId, 1)

                refusedBy(connection, "information_request_submission_supporting_link_link_fkey") {
                    insertLinkMember(
                        connection,
                        packageId,
                        own.requestId,
                        otherLink,
                        other.attestationRequirementId,
                        other.documentRequirementId,
                    )
                }
                refusedBy(connection, "information_request_submission_supporting_link_members_fkey") {
                    insertLinkMember(
                        connection,
                        packageId,
                        own.requestId,
                        ownLink,
                        own.documentRequirementId,
                        own.attestationRequirementId,
                    )
                }
                insertLinkMember(
                    connection,
                    packageId,
                    own.requestId,
                    ownLink,
                    own.attestationRequirementId,
                    own.documentRequirementId
                )
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_submission_supporting_link WHERE package_id = ?",
                        packageId,
                    ),
                )
            }
        }
    }

    @Test
    fun `upgrading populated V162 evidence and frozen links keeps them and applies the narrowed rules`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "162").migrate()
            postgres.createConnection("").use { connection ->
                val own = SubmissionRuntimeSqlFixture(connection)
                val other = SubmissionRuntimeSqlFixture(connection)
                val ownLink = insertSupportingLink(connection, own)
                val otherLink = insertSupportingLink(connection, other)
                val packageId = UUID.randomUUID()
                own.insertPackage(packageId, 1)
                insertLinkMember(
                    connection,
                    packageId,
                    own.requestId,
                    ownLink,
                    own.attestationRequirementId,
                    own.documentRequirementId
                )
                val before = state(connection)

                submissionFlyway(postgres).migrate()

                assertEquals(before, state(connection))
                assertEvidenceSourceShape(connection)
                refusedBy(connection, "information_request_submission_supporting_link_link_fkey") {
                    insertLinkMember(
                        connection,
                        packageId,
                        own.requestId,
                        otherLink,
                        other.attestationRequirementId,
                        other.documentRequirementId,
                    )
                }
                refusedBy(connection, "ck_information_request_evidence_version_kind") {
                    insertEvidenceVersion(connection, own, sourceKind = "EXTERNAL_REFERENCE")
                }
            }
        }
    }

    private fun assertEvidenceSourceShape(connection: Connection) {
        assertEquals(
            0,
            queryInt(
                connection,
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'information_request_evidence_version'
                  AND column_name IN ('external_reference_type', 'external_reference_value')
                """.trimIndent(),
            ),
        )
        assertEquals(
            "NO",
            queryString(
                connection,
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'information_request_evidence_version'
                  AND column_name = 'document_version_id'
                """.trimIndent(),
            ),
        )
        assertEquals(
            setOf(
                "ux_information_request_supporting_evidence_link_request",
                "ux_information_request_supporting_evidence_link_members",
                "information_request_submission_supporting_link_link_fkey",
                "information_request_submission_supporting_link_members_fkey",
            ),
            queryStrings(
                connection,
                """
                SELECT conname FROM pg_constraint
                WHERE conname IN ('ux_information_request_supporting_evidence_link_request',
                                  'ux_information_request_supporting_evidence_link_members',
                                  'information_request_submission_supporting_link_link_fkey',
                                  'information_request_submission_supporting_link_members_fkey')
                """.trimIndent(),
            ),
        )
        assertEquals(
            2,
            queryInt(
                connection,
                """
                SELECT cardinality(conkey) FROM pg_constraint
                WHERE conname = 'information_request_submission_supporting_link_link_fkey'
                """.trimIndent(),
            ),
        )
    }

    private fun state(connection: Connection): String? =
        queryString(
            connection,
            """
            SELECT json_build_object(
                'versions', (SELECT json_agg(json_build_object('id', id, 'artifact', evidence_artifact_id,
                    'number', version_number, 'kind', source_kind, 'content', document_version_id,
                    'file', declared_file_name, 'media', declared_media_type) ORDER BY id)
                    FROM information_request_evidence_version),
                'links', (SELECT json_agg(json_build_object('id', id, 'request', information_request_id,
                    'supported', supported_requirement_id, 'supporting', supporting_requirement_id) ORDER BY id)
                    FROM information_request_supporting_evidence_link),
                'members', (SELECT json_agg(json_build_object('id', id, 'package', package_id,
                    'request', information_request_id, 'link', supporting_evidence_link_id,
                    'supported', supported_requirement_id, 'supporting', supporting_requirement_id) ORDER BY id)
                    FROM information_request_submission_supporting_link)
            )::text
            """.trimIndent(),
        )

    private fun insertSupportingLink(connection: Connection, fixture: SubmissionRuntimeSqlFixture): UUID {
        val linkId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_supporting_evidence_link
                (id, information_request_id, supported_requirement_id, supporting_requirement_id,
                 template_evidence_link_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            linkId,
            fixture.requestId,
            fixture.attestationRequirementId,
            fixture.documentRequirementId,
            fixture.supportingLinkTemplateId,
        )
        return linkId
    }

    @Suppress("LongParameterList")
    private fun insertLinkMember(
        connection: Connection,
        packageId: UUID,
        requestId: UUID,
        linkId: UUID,
        supportedRequirementId: UUID,
        supportingRequirementId: UUID,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_submission_supporting_link
                (id, package_id, information_request_id, supporting_evidence_link_id, supported_requirement_id,
                 supporting_requirement_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            packageId,
            requestId,
            linkId,
            supportedRequirementId,
            supportingRequirementId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertEvidenceVersion(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        sourceKind: String = "DOCUMENT_VERSION",
        documentVersionId: UUID? = fixture.documentVersionId,
        declaredFileName: String = "process-record-2.pdf",
        declaredMediaType: String? = "application/pdf",
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_evidence_version
                (id, evidence_artifact_id, information_request_id, version_number, source_kind, document_version_id,
                 created_at, created_by_principal_kind, created_by_principal_id, declared_file_name, declared_media_type)
            VALUES (?, ?, ?, 2, ?, ?, now(), 'USER', ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            fixture.artifactId,
            fixture.requestId,
            sourceKind,
            documentVersionId,
            fixture.contributorUserId,
            declaredFileName,
            declaredMediaType,
        )
    }
}
