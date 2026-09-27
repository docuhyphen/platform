package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestOutboundNoticeContractTest
{
    @Test
    fun `a notice intent names exactly the source its kind requires and no longer stores a delivery state`()
    {
        withNotices { connection, notices ->
            refusedBy(connection, "ck_information_request_notice_intent_source") {
                notices.insertClockIntent(UUID.randomUUID(), "RESPONSE_REMINDER", clockEventId = null)
            }
            refusedBy(connection, "ck_information_request_notice_intent_kind") {
                notices.insertClockIntent(UUID.randomUUID(), "SOMETHING_ELSE", notices.clockEventId)
            }
            val intentId = UUID.randomUUID()
            notices.insertClockIntent(intentId, "RESPONSE_REMINDER", notices.clockEventId)
            refusedBy(connection, "ux_information_request_notice_intent_clock_party") {
                notices.insertClockIntent(UUID.randomUUID(), "RESPONSE_REMINDER", notices.clockEventId)
            }
            refusedBy(connection, "delivery_state") {
                execute(connection, "SELECT delivery_state FROM information_request_notice_intent WHERE id = ?", intentId)
            }
        }
    }

    @Test
    fun `an intent is claimed once and rendered into one immutable notice with its source and rendered hashes`()
    {
        withNotices { connection, notices ->
            val intentId = UUID.randomUUID()
            notices.insertClockIntent(intentId, "RESPONSE_OVERDUE", notices.clockEventId)
            notices.claim(intentId)
            refusedBy(connection, "information_request_notice_claim_pkey") {
                notices.claim(intentId)
            }
            refusedBy(connection, "ck_information_request_outbound_notice_endpoint") {
                notices.insertNotice(UUID.randomUUID(), intentId, endpoint = null, endpointState = "RESOLVED")
            }
            refusedBy(connection, "ck_information_request_outbound_notice_source") {
                notices.insertNotice(UUID.randomUUID(), intentId, sourceKind = "COMMUNICATION")
            }
            refusedBy(connection, "ck_information_request_outbound_notice_hash") {
                notices.insertNotice(UUID.randomUUID(), intentId, renderedHash = "not-a-hash")
            }
            val noticeId = UUID.randomUUID()
            notices.insertNotice(noticeId, intentId)
            refusedBy(connection, "ux_information_request_outbound_notice_intent") {
                notices.insertNotice(UUID.randomUUID(), intentId)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_outbound_notice SET rendered_body = 'changed' WHERE id = ?", noticeId)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_notice_claim WHERE notice_intent_id = ?", intentId)
            }
        }
    }

    @Test
    fun `a sequence is allocated once per key and each delivery attempt is numbered and immutable`()
    {
        withNotices { connection, notices ->
            val intentId = UUID.randomUUID()
            notices.insertClockIntent(intentId, "RESPONSE_REMINDER", notices.clockEventId)
            notices.claim(intentId)
            val noticeId = UUID.randomUUID()
            notices.insertNotice(noticeId, intentId)
            notices.allocate(noticeId, "case-number", 7)
            refusedBy(connection, "ux_information_request_notice_sequence_key") {
                notices.allocate(noticeId, "case-number", 8)
            }
            notices.attempt(noticeId, 1, "FAILED", "DELIVERY_ERROR")
            notices.attempt(noticeId, 2, "DELIVERED", null)
            refusedBy(connection, "ux_information_request_notice_attempt_number") {
                notices.attempt(noticeId, 2, "DELIVERED", null)
            }
            refusedBy(connection, "ck_information_request_notice_attempt_outcome") {
                notices.attempt(noticeId, 3, "BOUNCED", "DELIVERY_ERROR")
            }
            refusedBy(connection, "ck_information_request_notice_attempt_failure") {
                notices.attempt(noticeId, 3, "FAILED", null)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_notice_delivery_attempt SET outcome = 'FAILED' WHERE outbound_notice_id = ?", noticeId)
            }
        }
    }

    @Test
    fun `a clock policy version may name the communications its reminder and overdue notices render`()
    {
        withNotices { connection, notices ->
            val versionId = UUID.randomUUID()
            refusedBy(connection, "fk_clock_policy_version_reminder_communication") {
                notices.clocks.insertVersion(versionId, versionNumber = 2)
                execute(
                    connection,
                    "INSERT INTO information_request_clock_policy_version (id, policy_id, version_number, clock_type, business_timezone, standard_duration_minutes, urgent_duration_minutes, due_effect, published_by_principal_kind, published_by_principal_id, reminder_communication_id) VALUES (?, ?, 3, 'CALENDAR', 'UTC', 60, 60, 'MARK_OVERDUE', 'USER', ?, ?)",
                    UUID.randomUUID(),
                    notices.clocks.policyId,
                    notices.clocks.runtime.template.userId,
                    UUID.randomUUID(),
                )
            }
        }
    }

    private fun withNotices(block: (Connection, NoticeSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                block(connection, NoticeSqlFixture(connection, ClockSqlFixture(connection, runtime)))
            }
        }
    }
}

internal class NoticeSqlFixture(
    private val connection: Connection,
    val clocks: ClockSqlFixture,
)
{
    val clockId: UUID = UUID.randomUUID()
    val clockEventId: UUID = UUID.randomUUID()

    init
    {
        clocks.insertClock(clockId, "response")
        execute(
            connection,
            """
            INSERT INTO information_request_clock_event
                (id, clock_id, information_request_id, event_number, event_kind, due_cycle, point_ordinal, inputs_json,
                 due_at, occurred_at, recorded_by_principal_kind, recorded_by_principal_id)
            VALUES (?, ?, ?, 1, 'REMINDED', 0, 1, '{}', now(), now(), 'SERVICE_ACCOUNT', ?)
            """.trimIndent(),
            clockEventId,
            clockId,
            clocks.runtime.requestId,
            UUID(0, 0),
        )
    }

    fun insertClockIntent(id: UUID, kind: String, clockEventId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_notice_intent (id, information_request_id, party_id, notice_kind, clock_event_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            clocks.runtime.requestId,
            clocks.runtime.contributorPartyId,
            kind,
            clockEventId,
        )
    }

    fun claim(intentId: UUID)
    {
        execute(
            connection,
            "INSERT INTO information_request_notice_claim (notice_intent_id, information_request_id, claimed_by, claimed_at) VALUES (?, ?, 'worker-1', now())",
            intentId,
            clocks.runtime.requestId,
        )
    }

    @Suppress("LongParameterList")
    fun insertNotice(
        id: UUID,
        intentId: UUID,
        endpoint: String? = "contributor@example.test",
        endpointState: String = "RESOLVED",
        sourceKind: String = "PLATFORM_DEFAULT",
        renderedHash: String = "a".repeat(64),
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_outbound_notice
                (id, notice_intent_id, information_request_id, party_id, channel, recipient_endpoint, endpoint_state,
                 rendered_subject, rendered_body, content_hash_algorithm, rendered_content_hash, source_kind,
                 source_communication_id, source_content_hash, idempotency_key, rendered_at)
            VALUES (?, ?, ?, ?, 'EMAIL', ?, ?, 'Subject', 'Body', 'SHA_256', ?, ?, NULL, ?, ?, now())
            """.trimIndent(),
            id,
            intentId,
            clocks.runtime.requestId,
            clocks.runtime.contributorPartyId,
            endpoint,
            endpointState,
            renderedHash,
            sourceKind,
            "b".repeat(64),
            "information_request.notice|$id",
        )
    }

    fun allocate(noticeId: UUID, key: String, value: Long)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_notice_sequence_allocation (id, outbound_notice_id, sequence_key, allocated_value, rendered_value)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            noticeId,
            key,
            value,
            "N-$value",
        )
    }

    fun attempt(noticeId: UUID, number: Int, outcome: String, failure: String?)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_notice_delivery_attempt
                (id, outbound_notice_id, information_request_id, attempt_number, channel, outcome, failure_code, attempted_at)
            VALUES (?, ?, ?, ?, 'EMAIL', ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            noticeId,
            clocks.runtime.requestId,
            number,
            outcome,
            failure,
        )
    }
}
