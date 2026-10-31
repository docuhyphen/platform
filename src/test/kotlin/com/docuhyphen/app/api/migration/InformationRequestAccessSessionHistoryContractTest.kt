package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestAccessSessionHistoryContractTest {
    @Test
    fun `automatic session cutoff history is supported on a clean schema and remains append-only`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                insert(connection, fixture, 101, "REVOKE_ACCESS_SESSION", "PARENT_REJECTED")
                assertEquals(
                    "PARENT_REJECTED",
                    queryString(
                        connection,
                        "SELECT reason_code FROM information_request_transition WHERE information_request_id = ? AND mutation = 'REVOKE_ACCESS_SESSION'",
                        fixture.requestId
                    ),
                )
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_transition SET reason_code = NULL WHERE information_request_id = ?",
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
                    insert(connection, fixture, 110, "EXPIRE_ACCESS_SESSION", "PARENT_REJECTED")
                }
            }
        }
    }

    @Test
    fun `a populated V154 schema adds session cutoff history without rewriting access link history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "154").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                insert(connection, fixture, 101, "REVOKE_ACCESS_LINK", "PARTY_REVOKED", keyed = false)
                submissionFlyway(postgres).migrate()
                insert(connection, fixture, 102, "REVOKE_ACCESS_SESSION", "PARENT_DELETED")
                assertEquals(
                    "REVOKE_ACCESS_LINK",
                    queryString(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 101",
                        fixture.requestId
                    ),
                )
                assertEquals(
                    2,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 100",
                        fixture.requestId
                    )
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private fun insert(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        sequence: Int,
        mutation: String,
        reason: String,
        keyed: Boolean = true,
    ) {
        val keyColumn = if (keyed) ", idempotency_key" else ""
        val keyValue = if (keyed) ", 'transition-$sequence'" else ""
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, reason_code, occurred_at, party_id$keyColumn)
            VALUES (?, ?, ?, 'ISSUED', 'ISSUED', ?, 'USER', ?, ?, now(), ?$keyValue)
        """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            mutation,
            fixture.template.userId,
            reason,
            fixture.contributorPartyId
        )
    }
}
