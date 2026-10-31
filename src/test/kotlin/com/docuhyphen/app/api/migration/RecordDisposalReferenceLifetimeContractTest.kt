package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RecordDisposalReferenceLifetimeContractTest {
    @Test
    fun `a recertification and its evidence gain no reference to a source request under disposal`() {
        withRecertificationSource { connection, fixture ->
            val earlier = fixture.insertRecertification()
            val preservation = PreservationSqlFixture(connection, fixture.runtime)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            preservation.insertClaim(UUID.randomUUID(), scheduleId)

            refusedBy(connection, "a record under disposal cannot gain a reference") {
                fixture.insertRecertification()
            }
            refusedBy(connection, "a record under disposal cannot gain a reference") {
                fixture.insertRecertificationEvidence(earlier, fixture.runtime.evidenceVersionId)
            }
            assertEquals(1, fixture.count("information_request_fact_recertification"))
            assertEquals(0, fixture.count("information_request_fact_recertification_evidence"))
        }
    }

    @Test
    fun `a recertification names every source row it copies and every evidence version it reuses by foreign key`() {
        withRecertificationSource { connection, _ ->
            assertEquals(
                setOf(
                    "information_request_recertification_fact_fkey",
                    "information_request_recertification_source_request_fkey",
                    "information_request_recertification_source_package_fkey",
                    "information_request_recertification_source_item_fkey",
                    "information_request_recertification_source_requirement_fkey",
                    "information_request_recertification_source_revision_fkey",
                    "information_request_recertification_source_review_fkey",
                    "information_request_recertification_evidence_version_fkey",
                ),
                queryStrings(
                    connection,
                    """
                    SELECT constraint_name
                    FROM information_schema.table_constraints
                    WHERE constraint_type = 'FOREIGN KEY'
                      AND constraint_name LIKE 'information_request_recertification_%'
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test
    fun `an upgraded database keeps each recertification and names its source rows by foreign key`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "173").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = recertificationSource(connection)
                val recertificationId = fixture.insertRecertification()
                fixture.insertRecertificationEvidence(recertificationId, fixture.runtime.evidenceVersionId)

                submissionFlyway(postgres).migrate()

                assertEquals(1, fixture.count("information_request_fact_recertification"))
                assertEquals(1, fixture.count("information_request_fact_recertification_evidence"))
                assertEquals(
                    8,
                    queryInt(
                        connection,
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE constraint_type = 'FOREIGN KEY'
                          AND constraint_name LIKE 'information_request_recertification_%'
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    @Test
    fun `a reference that waits on an open claim is refused once the claim commits`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val other = SubmissionRuntimeSqlFixture(connection)
                val scheduleId = finishedWithSchedule(connection, runtime)
                postgres.createConnection("").use { claiming ->
                    claiming.autoCommit = false
                    assertEquals(
                        3,
                        queryInt(
                            claiming,
                            "SELECT record_disposal_lock_information_request_objects(?)",
                            runtime.requestId
                        ),
                    )
                    val claimed = PreservationSqlFixture(claiming, runtime)
                    val claimId = UUID.randomUUID()
                    claimed.insertClaim(claimId, scheduleId)
                    claimed.insertObject(claimId, retained = false)
                    val reference = CompletableFuture.supplyAsync {
                        runCatching {
                            execute(
                                connection,
                                "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)",
                                other.exchangeId,
                                runtime.documentId,
                            )
                        }
                    }
                    postgres.createConnection("").use { observer -> awaitLockWaiter(observer, reference) }
                    assertFalse(reference.isDone)
                    claiming.commit()

                    val failure = reference.get(30, TimeUnit.SECONDS).exceptionOrNull()
                    assertTrue(
                        failure?.message.orEmpty().contains("a record under disposal cannot gain a reference"),
                        failure?.message,
                    )
                }
            }
        }
    }

    @Test
    fun `a claim waits for a reference already being written and then sees it`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val other = SubmissionRuntimeSqlFixture(connection)
                finishedWithSchedule(connection, runtime)
                postgres.createConnection("").use { referencing ->
                    referencing.autoCommit = false
                    execute(
                        referencing,
                        "INSERT INTO exchange_document (exchange_id, documents_id) VALUES (?, ?)",
                        other.exchangeId,
                        runtime.documentId,
                    )
                    postgres.createConnection("").use { claiming ->
                        refusedBy(claiming, "canceling statement due to lock timeout") {
                            execute(claiming, "SET LOCAL lock_timeout = '300ms'")
                            queryInt(
                                claiming,
                                "SELECT record_disposal_lock_information_request_objects(?)",
                                runtime.requestId,
                            )
                        }
                        referencing.commit()
                        claiming.autoCommit = false
                        assertEquals(
                            3,
                            queryInt(
                                claiming,
                                "SELECT record_disposal_lock_information_request_objects(?)",
                                runtime.requestId,
                            ),
                        )
                        assertEquals(
                            1,
                            queryInt(
                                claiming,
                                "SELECT COUNT(*) FROM exchange_document WHERE documents_id = ?",
                                runtime.documentId
                            ),
                        )
                        claiming.rollback()
                    }
                }
            }
        }
    }

    private fun finishedWithSchedule(connection: Connection, runtime: SubmissionRuntimeSqlFixture): UUID {
        execute(
            connection,
            "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
            runtime.requestId,
        )
        val scheduleId = UUID.randomUUID()
        PreservationSqlFixture(connection, runtime).insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
        return scheduleId
    }

    private fun awaitLockWaiter(observer: Connection, pending: CompletableFuture<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!pending.isDone && System.nanoTime() < deadline) {
            if (queryInt(observer, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'") > 0) return
            Thread.onSpinWait()
        }
    }

    private fun withRecertificationSource(block: (Connection, RecertificationSqlFixture) -> Unit) {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection -> block(connection, recertificationSource(connection)) }
        }
    }

    private fun recertificationSource(connection: Connection): RecertificationSqlFixture {
        lateinit var answers: FieldAnswerSqlFixture
        val templateLinkId = UUID.randomUUID()
        val runtime = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
            answers = FieldAnswerSqlFixture(connection, configured.template)
            execute(
                connection,
                "INSERT INTO information_request_template_binding_evidence_link (id, template_binding_id, supporting_template_binding_id, template_version_id) VALUES (?, ?, ?, ?)",
                templateLinkId,
                answers.bindingId,
                configured.documentBindingId,
                configured.template.versionId,
            )
        })
        answers.materialize(runtime)
        return RecertificationSqlFixture(connection, runtime, answers, templateLinkId)
    }
}
