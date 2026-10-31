package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLException
import java.util.*

class InformationRequestTransitionIdempotencyKeyContractTest {
    @Test
    fun `a transition always names a non-blank idempotency key of at most the receipt key length`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)

                assertEquals(
                    "NO|256",
                    queryString(
                        connection,
                        """
                        SELECT is_nullable || '|' || character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'information_request_transition'
                          AND column_name = 'idempotency_key'
                        """.trimIndent(),
                    ),
                )
                refusedBy(connection, "idempotency_key") { insertTransition(connection, fixture, 101, null, null) }
                refusedBy(connection, "ck_information_request_transition_idempotency_key") {
                    insertTransition(connection, fixture, 101, "   ", null)
                }
                insertTransition(connection, fixture, 101, "client-key", null)
                assertEquals("client-key", keyOf(connection, fixture, 101))
            }
        }
    }

    @Test
    fun `a transition may name the receipt its own transaction stores afterwards, and never a receipt that is not stored`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                assertEquals(
                    "true|true",
                    queryString(
                        connection,
                        """
                        SELECT constraint_row.condeferrable || '|' || constraint_row.condeferred
                        FROM pg_constraint constraint_row
                                 JOIN pg_attribute attribute
                                      ON attribute.attrelid = constraint_row.conrelid
                                          AND attribute.attnum = ANY (constraint_row.conkey)
                        WHERE constraint_row.conrelid = 'information_request_transition'::regclass
                          AND constraint_row.contype = 'f'
                          AND constraint_row.confrelid = 'command_receipt'::regclass
                          AND attribute.attname = 'command_receipt_id'
                        """.trimIndent(),
                    ),
                )

                val receiptId = UUID.randomUUID()
                connection.autoCommit = false
                insertTransition(connection, fixture, 101, "client-issue-key", receiptId)
                insertReceipt(connection, receiptId, fixture, "client-issue-key")
                connection.commit()
                connection.autoCommit = true
                assertEquals(receiptId.toString(), receiptOf(connection, fixture, 101))

                connection.autoCommit = false
                insertTransition(connection, fixture, 102, "unstored-receipt-key", UUID.randomUUID())
                val refusal = assertThrows<SQLException> { connection.commit() }
                connection.autoCommit = true
                assertTrue(
                    refusal.message.orEmpty().contains("command_receipt"),
                    "Expected the receipt reference to refuse the commit: ${refusal.message}",
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 102",
                        fixture.requestId,
                    ),
                )
            }
        }
    }

    @Test
    fun `upgrading populated history keys a receipted transition by its receipt and any other by its mutation, request, and sequence`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "169").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val receiptId = UUID.randomUUID()
                insertReceipt(connection, receiptId, fixture, "client-issue-key")
                insertHistory(connection, fixture, 1, "ISSUE", receiptId)
                insertHistory(connection, fixture, 2, "RECORD_OVERDUE", null)

                submissionFlyway(postgres).migrate()

                assertEquals("client-issue-key", keyOf(connection, fixture, 1))
                assertEquals(receiptId.toString(), receiptOf(connection, fixture, 1))
                assertEquals("system|record_overdue|${fixture.requestId}|2", keyOf(connection, fixture, 2))
                assertNull(receiptOf(connection, fixture, 2))
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_transition SET idempotency_key = 'rewritten' WHERE information_request_id = ?",
                        fixture.requestId,
                    )
                }
            }
        }
    }

    private fun insertTransition(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        sequence: Int,
        idempotencyKey: String?,
        receiptId: UUID?,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id,
                 command_receipt_id, idempotency_key, occurred_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', 'IN_PROGRESS', 'SAVE_RESPONSE', 'USER', ?, ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            fixture.template.userId,
            receiptId,
            idempotencyKey,
        )
    }

    private fun insertHistory(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        sequence: Int,
        mutation: String,
        receiptId: UUID?,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id,
                 command_receipt_id, occurred_at)
            VALUES (?, ?, ?, 'ISSUED', 'ISSUED', ?, 'USER', ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            sequence,
            mutation,
            fixture.template.userId,
            receiptId,
        )
    }

    private fun insertReceipt(
        connection: Connection,
        receiptId: UUID,
        fixture: SubmissionRuntimeSqlFixture,
        idempotencyKey: String
    ) {
        execute(
            connection,
            """
            INSERT INTO command_receipt
                (id, resource_type, resource_id, operation_name, actor_kind, actor_id, idempotency_key,
                 request_fingerprint_sha256, result_resource_type, result_resource_id, created_at, completed_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'issue-information-request', 'USER', ?, ?, 'fingerprint',
                    'INFORMATION_REQUEST', ?, now(), now())
            """.trimIndent(),
            receiptId,
            fixture.requestId,
            fixture.template.userId,
            idempotencyKey,
            fixture.requestId,
        )
    }

    private fun keyOf(connection: Connection, fixture: SubmissionRuntimeSqlFixture, sequence: Int): String? =
        queryString(
            connection,
            "SELECT idempotency_key FROM information_request_transition WHERE information_request_id = ? AND sequence_number = ?",
            fixture.requestId,
            sequence,
        )

    private fun receiptOf(connection: Connection, fixture: SubmissionRuntimeSqlFixture, sequence: Int): String? =
        queryString(
            connection,
            "SELECT command_receipt_id::text FROM information_request_transition WHERE information_request_id = ? AND sequence_number = ?",
            fixture.requestId,
            sequence,
        )
}
