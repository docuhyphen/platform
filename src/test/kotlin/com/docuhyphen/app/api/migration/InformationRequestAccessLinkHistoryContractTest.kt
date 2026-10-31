package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestAccessLinkHistoryContractTest {
    @Test
    fun `bootstrap command history is supported on a clean schema and remains append-only`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                mutations.forEachIndexed { index, mutation -> insert(connection, fixture, 101 + index, mutation) }
                assertEquals(
                    4,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 100",
                        fixture.requestId
                    )
                )
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_transition SET party_id = NULL WHERE information_request_id = ?",
                        fixture.requestId
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_transition WHERE information_request_id = ?",
                        fixture.requestId
                    )
                }
                refusedBy(connection, "ck_information_request_transition_mutation") {
                    insert(
                        connection,
                        fixture,
                        110,
                        "CHANGE_ACCESS_LINK"
                    )
                }
            }
        }
    }

    @Test
    fun `a populated V153 schema adds bootstrap command history without rewriting authority history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "153").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                insert(connection, fixture, 101, "GRANT_DELEGATED_AUTHORITY", keyed = false)
                submissionFlyway(postgres).migrate()
                mutations.forEachIndexed { index, mutation -> insert(connection, fixture, 102 + index, mutation) }
                assertEquals(
                    "GRANT_DELEGATED_AUTHORITY",
                    queryString(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 101",
                        fixture.requestId
                    )
                )
            }
        }
    }

    private fun insert(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        sequence: Int,
        mutation: String,
        keyed: Boolean = true
    ) {
        val keyColumn = if (keyed) ", idempotency_key" else ""
        val keyValue = if (keyed) ", 'transition-$sequence'" else ""
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at, party_id$keyColumn)
            VALUES (?, ?, ?, 'IN_PROGRESS', 'IN_PROGRESS', ?, 'USER', ?, now(), ?$keyValue)
        """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            mutation,
            fixture.template.userId,
            fixture.contributorPartyId
        )
    }

    private val mutations =
        listOf("ISSUE_ACCESS_LINK", "ROTATE_ACCESS_LINK", "REPLACE_ACCESS_LINK", "REVOKE_ACCESS_LINK")
}
