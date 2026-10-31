package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestDelegatedAuthorityHistoryContractTest {
    @Test
    fun `delegated authority history appends grant and revocation without permitting rewrites`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val granted = insertTransition(connection, runtime, 101, "GRANT_DELEGATED_AUTHORITY")
                insertTransition(connection, runtime, 102, "REVOKE_DELEGATED_AUTHORITY")
                assertEquals(
                    2,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 100",
                        runtime.requestId
                    )
                )
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_transition SET party_id = NULL WHERE id = ?",
                        granted
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(connection, "DELETE FROM information_request_transition WHERE id = ?", granted)
                }
                refusedBy(connection, "ck_information_request_transition_mutation") {
                    insertTransition(connection, runtime, 103, "CHANGE_AUTHORITY")
                }
            }
        }
    }

    @Test
    fun `a populated schema gains authority history while keeping existing party history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "152").migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val previous = insertTransition(connection, runtime, 101, "ASSIGN_PARTY", keyed = false)
                submissionFlyway(postgres).migrate()
                insertTransition(connection, runtime, 102, "GRANT_DELEGATED_AUTHORITY")
                insertTransition(connection, runtime, 103, "REVOKE_DELEGATED_AUTHORITY")
                assertEquals(
                    "ASSIGN_PARTY",
                    queryString(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE id = ?",
                        previous
                    )
                )
            }
        }
    }

    private fun insertTransition(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        sequence: Int,
        mutation: String,
        keyed: Boolean = true,
    ): UUID {
        val id = UUID.randomUUID()
        val keyColumn = if (keyed) ", idempotency_key" else ""
        val keyValue = if (keyed) ", 'transition-$sequence'" else ""
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id,
                 occurred_at, party_id$keyColumn)
            VALUES (?, ?, ?, 'IN_PROGRESS', 'IN_PROGRESS', ?, 'USER', ?, now(), ?$keyValue)
            """.trimIndent(),
            id, runtime.requestId, sequence, mutation, runtime.template.userId, runtime.contributorPartyId,
        )
        return id
    }
}
