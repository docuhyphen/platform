package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.util.*

class InformationRequestRequirementRevisionIntervalContractTest {
    @Test
    fun `a requirement revision closes its effective interval once and changes nothing else`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val revisionId = fixture.documentRevisionId

                refusedBy(connection, "ck_information_request_requirement_revision_interval") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET effective_to = effective_from - interval '1 second' WHERE id = ?",
                        revisionId,
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET configuration_hash_sha256 = ? WHERE id = ?",
                        "1".repeat(64),
                        revisionId,
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET effective_to = now(), revision_number = 5 WHERE id = ?",
                        revisionId,
                    )
                }

                execute(
                    connection,
                    "UPDATE information_request_requirement_revision SET effective_to = effective_from WHERE id = ?",
                    revisionId,
                )

                assertEquals(
                    "true",
                    queryString(
                        connection,
                        "SELECT (effective_to = effective_from)::text FROM information_request_requirement_revision WHERE id = ?",
                        revisionId,
                    ),
                )
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET effective_to = effective_from + interval '1 day' WHERE id = ?",
                        revisionId,
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET effective_to = NULL WHERE id = ?",
                        revisionId
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_requirement_revision WHERE id = ?",
                        fixture.attestationRevisionId
                    )
                }
                assertNull(effectiveTo(connection, fixture.attestationRevisionId))
            }
        }
    }

    @Test
    fun `upgrading populated V163 revisions closes each superseded revision where its successor begins`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "163").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val next = fixture.publishNextVersion()
                execute(
                    connection,
                    "UPDATE information_request SET template_version_id = ? WHERE id = ?",
                    next.versionId,
                    fixture.requestId
                )
                execute(
                    connection,
                    """
                    UPDATE information_request_requirement
                    SET source_template_version_id = ?, source_template_binding_id = ?
                    WHERE id = ?
                    """.trimIndent(),
                    next.versionId,
                    next.documentBindingId,
                    fixture.documentRequirementId,
                )
                val secondRevisionId = UUID.randomUUID()
                val secondFrom = Timestamp.from(fixture.template.now.toInstant().plusSeconds(60))
                execute(
                    connection,
                    """
                    INSERT INTO information_request_requirement_revision
                        (id, information_request_requirement_id, information_request_id, source_template_version_id,
                         source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                         effective_from, configuration_hash_sha256, optimistic_version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 2, 'root', ?, ?, 1, ?)
                    """.trimIndent(),
                    secondRevisionId,
                    fixture.documentRequirementId,
                    fixture.requestId,
                    next.versionId,
                    fixture.documentTemplateRequirementId,
                    next.documentBindingId,
                    secondFrom,
                    "2".repeat(64),
                    secondFrom,
                )
                execute(
                    connection,
                    """
                    UPDATE information_request_requirement_current
                    SET current_revision_id = ?, current_revision_number = 2
                    WHERE information_request_requirement_id = ?
                    """.trimIndent(),
                    secondRevisionId,
                    fixture.documentRequirementId,
                )

                submissionFlyway(postgres).migrate()

                assertEquals(
                    "true",
                    queryString(
                        connection,
                        """
                        SELECT (earlier.effective_to = later.effective_from)::text
                        FROM information_request_requirement_revision earlier, information_request_requirement_revision later
                        WHERE earlier.id = ? AND later.id = ?
                        """.trimIndent(),
                        fixture.documentRevisionId,
                        secondRevisionId,
                    ),
                )
                assertNull(effectiveTo(connection, secondRevisionId))
                assertNull(effectiveTo(connection, fixture.attestationRevisionId))
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement_revision SET effective_to = now() WHERE id = ?",
                        fixture.documentRevisionId,
                    )
                }
            }
        }
    }

    private fun effectiveTo(connection: Connection, revisionId: UUID): String? =
        queryString(
            connection,
            "SELECT effective_to::text FROM information_request_requirement_revision WHERE id = ?",
            revisionId
        )
}
