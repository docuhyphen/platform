package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestClockContractTest
{
    @Test
    fun `a clock policy is owned once, numbers immutable versions, and states a valid calendar`()
    {
        withClocks { connection, clocks ->
            refusedBy(connection, "ck_information_request_clock_policy_owner") {
                execute(
                    connection,
                    """
                    INSERT INTO information_request_clock_policy
                        (id, owner_type, owner_organization_id, owner_user_id, policy_key, display_name,
                         created_by_principal_kind, created_by_principal_id)
                    VALUES (?, 'ORGANIZATION', NULL, ?, 'response-window', 'Response window', 'USER', ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    clocks.runtime.template.userId,
                    clocks.runtime.template.userId,
                )
            }
            refusedBy(connection, "ux_information_request_clock_policy_organization_key") {
                clocks.insertPolicy(UUID.randomUUID())
            }
            refusedBy(connection, "ux_information_request_clock_policy_version_number") {
                clocks.insertVersion(UUID.randomUUID(), versionNumber = 1)
            }
            refusedBy(connection, "ck_information_request_clock_policy_version_duration") {
                clocks.insertVersion(UUID.randomUUID(), versionNumber = 2, standard = 60, urgent = 120)
            }
            refusedBy(connection, "ck_information_request_clock_policy_version_type") {
                clocks.insertVersion(UUID.randomUUID(), versionNumber = 2, clockType = "WEEKLY")
            }
            refusedBy(connection, "ck_information_request_clock_policy_period_window") {
                clocks.insertPeriod(clocks.versionId, day = 1, start = 600, end = 540)
            }
            refusedBy(connection, "ck_information_request_clock_policy_period_day") {
                clocks.insertPeriod(clocks.versionId, day = 8, start = 540, end = 600)
            }
            refusedBy(connection, "ux_information_request_clock_policy_reminder_minutes") {
                clocks.insertReminder(clocks.versionId, ordinal = 3, minutes = 480)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(
                    connection,
                    "UPDATE information_request_clock_policy_version SET standard_duration_minutes = 10 WHERE id = ?",
                    clocks.versionId,
                )
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_clock_policy_holiday WHERE policy_version_id = ?", clocks.versionId)
            }
        }
    }

    @Test
    fun `a request clock keeps its frozen inputs and one clock per key`()
    {
        withClocks { connection, clocks ->
            val clockId = UUID.randomUUID()
            clocks.insertClock(clockId, "response")
            refusedBy(connection, "ux_information_request_clock_key") {
                clocks.insertClock(UUID.randomUUID(), "response")
            }
            refusedBy(connection, "a request clock keeps its frozen inputs") {
                execute(connection, "UPDATE information_request_clock SET urgency = 'URGENT' WHERE id = ?", clockId)
            }
            refusedBy(connection, "a request clock keeps its frozen inputs") {
                execute(connection, "UPDATE information_request_clock SET received_at = now() - INTERVAL '1 day' WHERE id = ?", clockId)
            }
            refusedBy(connection, "ck_information_request_clock_pause") {
                execute(connection, "UPDATE information_request_clock SET state = 'PAUSED', clock_revision = 2 WHERE id = ?", clockId)
            }
            execute(
                connection,
                "UPDATE information_request_clock SET state = 'PAUSED', remaining_seconds = 3600, clock_revision = 2 WHERE id = ?",
                clockId,
            )
            refusedBy(connection, "a request clock revision never moves backwards") {
                execute(connection, "UPDATE information_request_clock SET clock_revision = 1 WHERE id = ?", clockId)
            }
        }
    }

    @Test
    fun `clock history is append-only and records each reminder, overdue, and escalation point once per due cycle`()
    {
        withClocks { connection, clocks ->
            val clockId = UUID.randomUUID()
            clocks.insertClock(clockId, "response")
            clocks.insertEvent(clockId, 1, "STARTED", cycle = 0)
            clocks.insertEvent(clockId, 2, "REMINDED", cycle = 0, ordinal = 1)
            refusedBy(connection, "ux_information_request_clock_event_point") {
                clocks.insertEvent(clockId, 3, "REMINDED", cycle = 0, ordinal = 1)
            }
            clocks.insertEvent(clockId, 3, "OVERDUE", cycle = 0)
            refusedBy(connection, "ux_information_request_clock_event_point") {
                clocks.insertEvent(clockId, 4, "OVERDUE", cycle = 0)
            }
            clocks.insertEvent(clockId, 4, "EXTENDED", cycle = 1)
            clocks.insertEvent(clockId, 5, "OVERDUE", cycle = 1)
            refusedBy(connection, "ux_information_request_clock_event_number") {
                clocks.insertEvent(clockId, 5, "RESUMED", cycle = 1)
            }
            refusedBy(connection, "ck_information_request_clock_event_kind") {
                clocks.insertEvent(clockId, 6, "SNOOZED", cycle = 1)
            }
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_clock_event SET reason_code = 'changed' WHERE clock_id = ?", clockId)
            }
        }
    }

    private fun withClocks(block: (Connection, ClockSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                block(connection, ClockSqlFixture(connection, SubmissionRuntimeSqlFixture(connection)))
            }
        }
    }
}

internal class ClockSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
    val policyId: UUID = UUID.randomUUID(),
    val versionId: UUID = UUID.randomUUID(),
    seed: Boolean = true,
)
{
    init
    {
        if (seed) seed()
    }

    private fun seed()
    {
        insertPolicy(policyId)
        insertVersion(versionId, versionNumber = 1)
        (1..5).forEach { insertPeriod(versionId, day = it, start = 540, end = 1020) }
        insertReminder(versionId, ordinal = 1, minutes = 480)
        insertReminder(versionId, ordinal = 2, minutes = 60)
        execute(
            connection,
            "INSERT INTO information_request_clock_policy_holiday (id, policy_version_id, holiday_date) VALUES (?, ?, DATE '2026-12-25')",
            UUID.randomUUID(),
            versionId,
        )
    }

    fun insertPolicy(id: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock_policy
                (id, owner_type, owner_organization_id, policy_key, display_name, created_by_principal_kind,
                 created_by_principal_id)
            VALUES (?, 'ORGANIZATION', ?, 'response-window', 'Response window', 'USER', ?)
            """.trimIndent(),
            id,
            runtime.template.organizationId,
            runtime.template.userId,
        )
    }

    @Suppress("LongParameterList")
    fun insertVersion(
        id: UUID,
        versionNumber: Int,
        clockType: String = "BUSINESS",
        standard: Int = 2400,
        urgent: Int = 480,
        escalation: Int? = 240,
        dueEffect: String = "MARK_OVERDUE",
        overdueCommunicationId: UUID? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock_policy_version
                (id, policy_id, version_number, clock_type, business_timezone, standard_duration_minutes,
                 urgent_duration_minutes, escalation_after_minutes, due_effect, published_by_principal_kind,
                 published_by_principal_id, overdue_communication_id)
            VALUES (?, ?, ?, ?, 'Africa/Johannesburg', ?, ?, ?, ?, 'USER', ?, ?)
            """.trimIndent(),
            id,
            policyId,
            versionNumber,
            clockType,
            standard,
            urgent,
            escalation,
            dueEffect,
            runtime.template.userId,
            overdueCommunicationId,
        )
    }

    fun insertPeriod(version: UUID, day: Int, start: Int, end: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock_policy_period (id, policy_version_id, day_of_week, start_minute, end_minute)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            version,
            day,
            start,
            end,
        )
    }

    fun insertReminder(version: UUID, ordinal: Int, minutes: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock_policy_reminder (id, policy_version_id, reminder_ordinal, minutes_before_due)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            version,
            ordinal,
            minutes,
        )
    }

    fun insertClock(id: UUID, key: String)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock
                (id, information_request_id, clock_key, policy_version_id, urgency, received_at, state, due_at,
                 due_cycle, clock_revision, started_by_principal_kind, started_by_principal_id, started_at)
            VALUES (?, ?, ?, ?, 'STANDARD', now(), 'RUNNING', now() + INTERVAL '5 days', 0, 1, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            key,
            versionId,
            runtime.template.userId,
        )
    }

    fun insertEvent(clockId: UUID, number: Int, kind: String, cycle: Int, ordinal: Int? = null)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_clock_event
                (id, clock_id, information_request_id, event_number, event_kind, due_cycle, point_ordinal, inputs_json,
                 due_at, occurred_at, recorded_by_principal_kind, recorded_by_principal_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, '{}', now() + INTERVAL '5 days', now(), 'SERVICE_ACCOUNT', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            clockId,
            runtime.requestId,
            number,
            kind,
            cycle,
            ordinal,
            UUID(0, 0),
        )
    }
}
