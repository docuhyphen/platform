package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InformationRequestRemovedRequirementRevisionContractTest {
    @Test
    fun `upgrading closes the open revisions of requirements removed before the upgrade and keeps the rest open`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "170").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val next = fixture.publishNextVersion(keepAttestation = false)
                execute(
                    connection,
                    "UPDATE information_request SET template_version_id = ? WHERE id = ?",
                    next.versionId,
                    fixture.requestId
                )
                execute(
                    connection,
                    "UPDATE information_request_requirement SET source_template_version_id = ?, source_template_binding_id = ? WHERE id = ?",
                    next.versionId,
                    next.documentBindingId,
                    fixture.documentRequirementId,
                )
                val amendedAt =
                    Timestamp.from(fixture.template.now.toInstant().plusSeconds(120).truncatedTo(ChronoUnit.MICROS))
                val amendmentId = UUID.randomUUID()
                execute(
                    connection,
                    """
                    INSERT INTO information_request_amendment
                        (id, information_request_id, amendment_number, from_template_version_id, to_template_version_id,
                         amended_by_principal_kind, amended_by_principal_id, amended_at)
                    VALUES (?, ?, 1, ?, ?, 'USER', ?, ?)
                    """.trimIndent(),
                    amendmentId,
                    fixture.requestId,
                    fixture.template.versionId,
                    next.versionId,
                    fixture.template.userId,
                    amendedAt,
                )
                execute(
                    connection,
                    """
                    INSERT INTO information_request_amendment_change
                        (id, amendment_id, information_request_id, template_requirement_id, requirement_key, change_kind,
                         from_template_binding_id, to_template_binding_id)
                    VALUES (?, ?, ?, ?, 'recorded-assertion', 'REMOVED', ?, NULL)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    amendmentId,
                    fixture.requestId,
                    fixture.attestationTemplateRequirementId,
                    fixture.attestationBindingId,
                )
                val groupId = draftGroup(connection, fixture)
                val removedAt =
                    Timestamp.from(fixture.template.now.toInstant().plusSeconds(60).truncatedTo(ChronoUnit.MICROS))
                occurrence(connection, fixture, groupId, "entries[0]", removedAt)
                occurrence(connection, fixture, groupId, "entries[1]", null)
                val removedEntryRevision = requirementAt(connection, fixture, next, "entries[0]")
                val liveEntryRevision = requirementAt(connection, fixture, next, "entries[1]")

                submissionFlyway(postgres).migrate()

                assertEquals(amendedAt.toInstant(), effectiveTo(connection, fixture.attestationRevisionId))
                assertEquals(removedAt.toInstant(), effectiveTo(connection, removedEntryRevision))
                assertNull(effectiveTo(connection, fixture.documentRevisionId))
                assertNull(effectiveTo(connection, liveEntryRevision))
            }
        }
    }

    private fun draftGroup(connection: Connection, fixture: SubmissionRuntimeSqlFixture): UUID {
        val draftVersionId = UUID.randomUUID()
        fixture.template.insertVersion(draftVersionId, 3)
        return UUID.randomUUID().also { groupId ->
            execute(
                connection,
                """
                INSERT INTO information_request_template_requirement_group
                    (id, template_version_id, group_key, min_occurrences, max_occurrences)
                VALUES (?, ?, 'entries', 0, 5)
                """.trimIndent(),
                groupId,
                draftVersionId,
            )
        }
    }

    private fun occurrence(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        groupId: UUID,
        path: String,
        removedAt: Timestamp?,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_group_occurrence
                (id, information_request_id, source_template_group_id, occurrence_index, occurrence_path,
                 removed_at, removed_by_principal_kind, removed_by_principal_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            groupId,
            path.substringAfter('[').substringBefore(']').toInt(),
            path,
            removedAt,
            removedAt?.let { "USER" },
            removedAt?.let { fixture.template.userId },
        )
    }

    private fun requirementAt(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        next: NextSubmissionVersion,
        path: String,
    ): UUID {
        val requirementId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            requirementId,
            fixture.requestId,
            next.versionId,
            fixture.documentTemplateRequirementId,
            next.documentBindingId,
            path,
            fixture.template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                 effective_from, configuration_hash_sha256, optimistic_version, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 1, ?)
            """.trimIndent(),
            revisionId,
            requirementId,
            fixture.requestId,
            next.versionId,
            fixture.documentTemplateRequirementId,
            next.documentBindingId,
            path,
            fixture.template.now,
            "3".repeat(64),
            fixture.template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_current
                (information_request_requirement_id, current_revision_id, current_revision_number, updated_at)
            VALUES (?, ?, 1, ?)
            """.trimIndent(),
            requirementId,
            revisionId,
            fixture.template.now,
        )
        return revisionId
    }

    private fun effectiveTo(connection: Connection, revisionId: UUID): Instant? =
        connection.prepareStatement("SELECT effective_to FROM information_request_requirement_revision WHERE id = ?")
            .use { statement ->
                statement.setObject(1, revisionId)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getTimestamp(1)?.toInstant()
                }
            }
}
