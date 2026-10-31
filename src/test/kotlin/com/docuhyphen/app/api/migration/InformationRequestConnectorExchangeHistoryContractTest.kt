package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestConnectorExchangeHistoryContractTest {
    @Test
    fun `connector exchange progress history is supported on a clean schema and remains append-only`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                progress.forEachIndexed { index, mutation -> insert(connection, fixture, 101 + index, mutation) }
                assertEquals(
                    progress.size,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 100",
                        fixture.requestId
                    )
                )
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_transition SET reason_code = 'changed' WHERE information_request_id = ? AND sequence_number > 100",
                        fixture.requestId
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 100",
                        fixture.requestId
                    )
                }
                refusedBy(connection, "ck_information_request_transition_mutation") {
                    insert(connection, fixture, 110, "ABANDON_EXTERNAL_SOURCE")
                }
            }
        }
    }

    @Test
    fun `every mutation the application records is accepted by the transition history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                InformationRequestMutation.entries.forEachIndexed { index, mutation ->
                    insert(connection, fixture, 201 + index, mutation.name)
                }
                assertEquals(
                    InformationRequestMutation.entries.map { it.name }.toSet(),
                    queryStrings(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 200",
                        fixture.requestId
                    )
                )
            }
        }
    }

    @Test
    fun `a populated V179 schema adds connector exchange progress without rewriting earlier external source history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "179").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                insert(connection, fixture, 101, "REQUEST_EXTERNAL_SOURCE")
                submissionFlyway(postgres).migrate()
                progress.forEachIndexed { index, mutation -> insert(connection, fixture, 102 + index, mutation) }
                assertEquals(
                    "REQUEST_EXTERNAL_SOURCE",
                    queryString(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 101",
                        fixture.requestId
                    )
                )
                assertEquals(
                    progress.toSet(),
                    queryStrings(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number > 101",
                        fixture.requestId
                    )
                )
            }
        }
    }

    private fun insert(connection: Connection, fixture: SubmissionRuntimeSqlFixture, sequence: Int, mutation: String) {
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at, idempotency_key)
            VALUES (?, ?, ?, 'ISSUED', 'ISSUED', ?, 'SERVICE_ACCOUNT', ?, now(), ?)
        """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            mutation,
            UUID(0, 0),
            "system|${mutation.lowercase()}|${fixture.requestId}|$sequence"
        )
    }

    private val progress = listOf(
        "START_EXTERNAL_SOURCE_ATTEMPT",
        "SCHEDULE_EXTERNAL_SOURCE_RETRY",
        "RECORD_EXTERNAL_SOURCE_PENDING",
        "COMPLETE_EXTERNAL_SOURCE",
        "FAIL_EXTERNAL_SOURCE"
    )
}
