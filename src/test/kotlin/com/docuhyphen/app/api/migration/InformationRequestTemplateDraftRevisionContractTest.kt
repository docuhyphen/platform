package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestTemplateDraftRevisionContractTest {
    @Test
    fun `a draft Version counts its revisions and a frozen Version keeps its count`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val draftId = UUID.randomUUID()
                fixture.template.insertVersion(draftId, 2)

                assertEquals(0, revisionOf(connection, draftId))
                execute(
                    connection,
                    "UPDATE information_request_template_version SET revision = revision + 1 WHERE id = ?",
                    draftId
                )
                assertEquals(1, revisionOf(connection, draftId))
                refusedBy(connection, "ck_request_template_version_revision") {
                    execute(
                        connection,
                        "UPDATE information_request_template_version SET revision = -1 WHERE id = ?",
                        draftId
                    )
                }
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "UPDATE information_request_template_version SET revision = revision + 1 WHERE id = ?",
                        fixture.template.versionId,
                    )
                }
                execute(
                    connection,
                    "UPDATE information_request_template_version SET status = 'RETIRED', retired_at = now() WHERE id = ?",
                    fixture.template.versionId,
                )
                assertEquals(0, revisionOf(connection, fixture.template.versionId))
            }
        }
    }

    @Test
    fun `upgrading populated V163 Template Versions starts every Version at revision zero`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "163").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val draftId = UUID.randomUUID()
                fixture.template.insertVersion(draftId, 2)
                val before = versionState(connection)

                submissionFlyway(postgres).migrate()

                assertEquals(0, revisionOf(connection, fixture.template.versionId))
                assertEquals(0, revisionOf(connection, draftId))
                assertEquals(before, versionState(connection))
                execute(
                    connection,
                    "UPDATE information_request_template_version SET revision = 4 WHERE id = ?",
                    draftId
                )
                assertEquals(4, revisionOf(connection, draftId))
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "UPDATE information_request_template_version SET revision = 1 WHERE id = ?",
                        fixture.template.versionId,
                    )
                }
            }
        }
    }

    private fun revisionOf(connection: Connection, versionId: UUID): Int =
        queryInt(connection, "SELECT revision FROM information_request_template_version WHERE id = ?", versionId)

    private fun versionState(connection: Connection): String? =
        queryString(
            connection, """
            SELECT json_agg(json_build_object('id', id, 'number', version_number, 'status', status,
                'published', published_at, 'schema', schema_version_id) ORDER BY id)::text
            FROM information_request_template_version
        """.trimIndent()
        )
}
