package com.docuhyphen.app.api.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestEventConsumptionContractTest
{
    @Test
    fun `the trigger registry names every request trigger with a version one request subject schema`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val requestTriggers = rows(
                    connection,
                    """
                    SELECT event_name, subject_schema_version, subject_fields_json
                    FROM workflow_trigger_event_registry
                    WHERE subject_resource_type = 'INFORMATION_REQUEST'
                    ORDER BY event_name
                    """.trimIndent(),
                )
                assertEquals(REQUEST_TRIGGERS.sorted(), requestTriggers.map { it[0] })
                requestTriggers.forEach { row ->
                    assertEquals("1", row[1])
                    val fields = Json.parseToJsonElement(row[2]!!).jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
                    assertTrue(fields.containsAll(listOf("requestId", "exchangeId", "templateVersionId", "state")), "${row[0]}: $fields")
                    assertTrue(fields.none { it.contains("email", ignoreCase = true) || it.contains("name", ignoreCase = true) })
                }
                assertEquals(
                    7,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM workflow_trigger_event_registry WHERE subject_resource_type = 'EXCHANGE' AND subject_schema_version = 1",
                    ),
                )
                refusedBy(connection, "ck_workflow_trigger_event_registry_subject_type") {
                    execute(
                        connection,
                        "INSERT INTO workflow_trigger_event_registry (event_name, subject_resource_type, subject_schema_version) VALUES ('other.event', 'DOCUMENT', 1)",
                    )
                }
                refusedBy(connection, "ck_workflow_trigger_event_registry_schema_version") {
                    execute(
                        connection,
                        "INSERT INTO workflow_trigger_event_registry (event_name, subject_resource_type, subject_schema_version) VALUES ('other.event', 'EXCHANGE', 0)",
                    )
                }
            }
        }
    }

    @Test
    fun `an ordered event states its key and every event receives a rising sequence while an ownerless event is refused`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val first = UUID.randomUUID()
                val second = UUID.randomUUID()
                insertEvent(connection, first, "information_request:${UUID.randomUUID()}")
                insertEvent(connection, second, "information_request:${UUID.randomUUID()}")
                val firstSequence = queryString(connection, "SELECT sequence_number FROM workflow_event_outbox WHERE event_id = ?", first)!!.toLong()
                val secondSequence = queryString(connection, "SELECT sequence_number FROM workflow_event_outbox WHERE event_id = ?", second)!!.toLong()
                assertTrue(secondSequence > firstSequence)
                refusedBy(connection, "ck_workflow_event_outbox_ordering_key") {
                    insertEvent(connection, UUID.randomUUID(), " ")
                }
                refusedBy(connection, "owner_kind") {
                    execute(
                        connection,
                        """
                        INSERT INTO workflow_event_outbox
                            (id, event_id, idempotency_key, event_type, envelope_json, status, attempt_count,
                             created_at, next_attempt_at)
                        VALUES (?, ?, ?, 'workflow.test', '{}', 'PENDING', 0, now(), now())
                        """.trimIndent(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "workflow:${UUID.randomUUID()}",
                    )
                }
            }
        }
    }

    @Test
    fun `a consumer records one append-only receipt per event`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val eventId = UUID.randomUUID()
                insertReceipt(connection, "workflow-trigger", eventId, "APPLIED")
                insertReceipt(connection, "notification-fan-out", eventId, "SKIPPED")
                refusedBy(connection, "domain_event_consumption_pkey") {
                    insertReceipt(connection, "workflow-trigger", eventId, "APPLIED")
                }
                refusedBy(connection, "ck_domain_event_consumption_outcome") {
                    insertReceipt(connection, "workflow-trigger", UUID.randomUUID(), "RETRIED")
                }
                refusedBy(connection, "domain event consumption is append-only") {
                    execute(connection, "UPDATE domain_event_consumption SET outcome = 'SKIPPED' WHERE event_id = ?", eventId)
                }
            }
        }
    }

    @Test
    fun `a request records when it was first viewed, started, and expired and records its first view and start once`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                refusedBy(connection, "ck_information_request_terminal_dates") {
                    execute(connection, "UPDATE information_request SET state = 'IN_PROGRESS' WHERE id = ?", runtime.requestId)
                }
                execute(
                    connection,
                    "UPDATE information_request SET state = 'IN_PROGRESS', first_viewed_at = now(), started_at = now() WHERE id = ?",
                    runtime.requestId,
                )
                refusedBy(connection, "ck_information_request_terminal_dates") {
                    execute(connection, "UPDATE information_request SET state = 'EXPIRED' WHERE id = ?", runtime.requestId)
                }
                insertTransition(connection, runtime, 1, "RECORD_FIRST_VIEW")
                refusedBy(connection, "ux_information_request_transition_first_view") {
                    insertTransition(connection, runtime, 2, "RECORD_FIRST_VIEW")
                }
                insertTransition(connection, runtime, 2, "START_RESPONSE")
                refusedBy(connection, "ux_information_request_transition_start") {
                    insertTransition(connection, runtime, 3, "START_RESPONSE")
                }
                PHASE_NINE_MUTATIONS.forEachIndexed { index, mutation ->
                    insertTransition(connection, runtime, 3 + index, mutation)
                }
                execute(
                    connection,
                    "UPDATE information_request SET state = 'EXPIRED', expired_at = now() WHERE id = ?",
                    runtime.requestId,
                )
            }
        }
    }

    private fun insertEvent(connection: Connection, eventId: UUID, orderingKey: String)
    {
        val now = Timestamp.from(Instant.now())
        execute(
            connection,
            """
            INSERT INTO workflow_event_outbox
                (id, event_id, idempotency_key, event_type, owner_kind, owner_id, organization_id, ordering_key,
                 envelope_json, status, attempt_count, created_at, next_attempt_at)
            VALUES (?, ?, ?, 'information_request.request.issue', 'PLATFORM', NULL, NULL, ?, '{}', 'PENDING', 0, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            eventId,
            "information_request:$eventId",
            orderingKey,
            now,
            now,
        )
    }

    private fun insertReceipt(connection: Connection, consumer: String, eventId: UUID, outcome: String)
    {
        execute(
            connection,
            "INSERT INTO domain_event_consumption (consumer_key, event_id, outcome, consumed_at) VALUES (?, ?, ?, now())",
            consumer,
            eventId,
            outcome,
        )
    }

    private fun insertTransition(connection: Connection, runtime: SubmissionRuntimeSqlFixture, sequence: Int, mutation: String)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', 'IN_PROGRESS', ?, 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtime.requestId,
            sequence,
            mutation,
            runtime.template.userId,
        )
    }

    private fun rows(connection: Connection, sql: String): List<List<String?>> =
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { result ->
                val columns = result.metaData.columnCount
                buildList {
                    while (result.next())
                    {
                        add((1..columns).map { result.getString(it) })
                    }
                }
            }
        }

    private companion object
    {
        val REQUEST_TRIGGERS = listOf(
            "information_request.request.issue",
            "information_request.request.view",
            "information_request.request.start",
            "information_request.request.submit",
            "information_request.request.correction",
            "information_request.request.close",
            "information_request.request.expire",
            "information_request.request.cancel",
            "information_request.request.supersede",
            "information_request.request.overdue",
        )

        val PHASE_NINE_MUTATIONS = listOf(
            "CHANGE_COMPLETION_GATE",
            "START_CLOCK",
            "PAUSE_CLOCK",
            "RESUME_CLOCK",
            "EXTEND_CLOCK",
            "RECORD_REMINDER",
            "RECORD_OVERDUE",
            "RECORD_ESCALATION",
        )
    }
}
