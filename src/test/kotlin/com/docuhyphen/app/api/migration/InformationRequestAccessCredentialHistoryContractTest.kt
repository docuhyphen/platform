package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestAccessCredentialHistoryContractTest {
    @Test
    fun `contact proof, session issuance, and account link history is supported on a clean schema and remains append-only`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                mutations.forEachIndexed { index, mutation -> insert(connection, fixture, 101 + index, mutation) }
                assertEquals(
                    mutations.size,
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
                        "VERIFY_CONTACT_CHALLENGE"
                    )
                }
            }
        }
    }

    @Test
    fun `a populated V155 schema adds credential history without rewriting session cutoff history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "155").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                insert(connection, fixture, 101, "REVOKE_ACCESS_SESSION", keyed = false)
                submissionFlyway(postgres).migrate()
                mutations.forEachIndexed { index, mutation -> insert(connection, fixture, 102 + index, mutation) }
                assertEquals(
                    "REVOKE_ACCESS_SESSION",
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
            VALUES (?, ?, ?, 'ISSUED', 'ISSUED', ?, 'PARTICIPANT', ?, now(), ?$keyValue)
        """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            mutation,
            UUID.randomUUID(),
            fixture.contributorPartyId
        )
    }

    private val mutations = listOf(
        "ISSUE_CONTACT_CHALLENGE",
        "REFUSE_CONTACT_CHALLENGE",
        "ISSUE_ACCESS_SESSION",
        "LINK_PARTICIPANT_ACCOUNT"
    )
}
