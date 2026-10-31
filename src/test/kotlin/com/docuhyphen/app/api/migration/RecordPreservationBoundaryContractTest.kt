package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class RecordPreservationBoundaryContractTest {
    @Test
    fun `claim scope insertion serializes with advancing object deletion`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            val claim = UUID.randomUUID()
            val request = postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val preservation = PreservationSqlFixture(connection, runtime)
                val schedule = UUID.randomUUID()
                preservation.insertSchedule(schedule, 1, 0, 0)
                preservation.insertClaim(claim, schedule)
                runtime.requestId
            }
            postgres.createConnection("").use { scopeConnection ->
                scopeConnection.autoCommit = false
                execute(
                    scopeConnection,
                    "INSERT INTO record_disposal_claim_scope (claim_id, resource_type, resource_id, direct) VALUES (?, 'INFORMATION_REQUEST', ?, TRUE)",
                    claim,
                    request.toString()
                )
                val advancing = java.util.concurrent.CompletableFuture.runAsync {
                    postgres.createConnection("").use { connection ->
                        execute(
                            connection,
                            "UPDATE record_disposal_claim SET state = 'OBJECTS_DELETED', objects_deleted_at = now(), claim_revision = claim_revision + 1 WHERE id = ?",
                            claim
                        )
                    }
                }
                try {
                    assertThrows(java.util.concurrent.TimeoutException::class.java) {
                        advancing.get(2, java.util.concurrent.TimeUnit.SECONDS)
                    }
                } finally {
                    scopeConnection.commit()
                    advancing.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                refusedBy(scopeConnection, "a disposal freezes its scope before deleting objects") {
                    execute(
                        scopeConnection,
                        "INSERT INTO record_disposal_claim_scope (claim_id, resource_type, resource_id, direct) VALUES (?, 'INFORMATION_REQUEST_RECORD_EXPORT', ?, TRUE)",
                        claim,
                        UUID.randomUUID().toString()
                    )
                }
            }
        }
    }

    @Test
    fun `a disposal claim never permits deleting hold history schedules or tombstones`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val preservation = PreservationSqlFixture(connection, runtime)
                val hold = UUID.randomUUID()
                val schedule = UUID.randomUUID()
                val claim = UUID.randomUUID()
                preservation.insertHold(hold, "EXCHANGE", UUID.randomUUID().toString())
                preservation.insertHoldEvent(hold, 1, "PLACED")
                preservation.insertSchedule(schedule, 1, 0, 0)
                val claimSchedule = UUID.randomUUID()
                preservation.insertSchedule(claimSchedule, 2, 0, 0)
                preservation.insertClaim(claim, claimSchedule)
                preservation.advance(claim)
                execute(
                    connection, """INSERT INTO record_disposal_tombstone
                    (id, claim_id, resource_type, resource_id, owner_kind, owner_id, basis, removed_rows_json,
                     deleted_object_count, retained_object_count, disposed_at)
                    VALUES (?, ?, 'INFORMATION_REQUEST', ?, 'ORGANIZATION', ?, 'RETENTION_SCHEDULE', '{}', 0, 0, now())""",
                    UUID.randomUUID(), claim, runtime.requestId, runtime.template.organizationId
                )
                queryString(
                    connection,
                    "SELECT set_config('docuhyphen.record_disposal_claim', ?, false)",
                    claim.toString()
                )
                assertAll(
                    {
                        refusedBy(connection, "record preservation history is append-only") {
                            execute(
                                connection,
                                "DELETE FROM audit_legal_hold_event WHERE hold_id = ?",
                                hold
                            )
                        }
                    },
                    {
                        refusedBy(connection, "record preservation history is append-only") {
                            execute(
                                connection,
                                "DELETE FROM record_retention_schedule WHERE id = ?",
                                schedule
                            )
                        }
                    },
                    {
                        refusedBy(connection, "record preservation history is append-only") {
                            execute(
                                connection,
                                "DELETE FROM record_disposal_tombstone WHERE claim_id = ?",
                                claim
                            )
                        }
                    },
                )
            }
        }
    }

    @Test
    fun `existing hold keys gain canonical resource types and UUID spelling`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, target = "184").migrate()
            val hold = UUID.randomUUID()
            val resource = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                PreservationSqlFixture(connection, runtime).insertHold(
                    hold,
                    "exchange",
                    resource.toString().uppercase()
                )
            }
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                assertEquals(
                    "EXCHANGE|$resource",
                    queryString(
                        connection,
                        "SELECT resource_type || '|' || resource_id FROM audit_legal_hold WHERE id = ?",
                        hold
                    )
                )
            }
        }
    }
}
