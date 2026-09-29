package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestAuthorActionsContractTest
{
    @Test
    fun `assigning and revoking a party and sending a reminder are recorded request history`()
    {
        withAuthorActions { connection, runtime ->
            insertTransition(connection, runtime, 101, "ASSIGN_PARTY", runtime.contributorPartyId)
            insertTransition(connection, runtime, 102, "REVOKE_PARTY", runtime.contributorPartyId)
            insertTransition(connection, runtime, 103, "SEND_REMINDER", null)
            refusedBy(connection, "ck_information_request_transition_mutation") {
                insertTransition(connection, runtime, 104, "SEND_SOMETHING", null)
            }
            assertEquals(
                3,
                count(connection, "SELECT count(*) FROM information_request_transition WHERE sequence_number > 100"),
            )
        }
    }

    @Test
    fun `a sent reminder owes one notice per party and names the history row that sent it`()
    {
        withAuthorActions { connection, runtime ->
            val reminderId = insertTransition(connection, runtime, 101, "SEND_REMINDER", null)
            val clocks = ClockSqlFixture(connection, runtime)
            val notices = NoticeSqlFixture(connection, clocks)
            insertReminderIntent(connection, runtime, "RESPONSE_REMINDER", reminderId, clockEventId = null)
            refusedBy(connection, "ux_information_request_notice_intent_transition_party") {
                insertReminderIntent(connection, runtime, "RESPONSE_REMINDER", reminderId, clockEventId = null)
            }
            refusedBy(connection, "ck_information_request_notice_intent_source") {
                insertReminderIntent(connection, runtime, "RESPONSE_OVERDUE", reminderId, clockEventId = null)
            }
            refusedBy(connection, "ck_information_request_notice_intent_source") {
                insertReminderIntent(connection, runtime, "RESPONSE_REMINDER", reminderId, notices.clockEventId)
            }
            refusedBy(connection, "information_request_notice_intent_transition_fkey") {
                insertReminderIntent(connection, runtime, "RESPONSE_REMINDER", UUID.randomUUID(), clockEventId = null)
            }
            notices.insertClockIntent(UUID.randomUUID(), "RESPONSE_REMINDER", notices.clockEventId)
        }
    }

    private fun withAuthorActions(block: (Connection, SubmissionRuntimeSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                block(connection, SubmissionRuntimeSqlFixture(connection))
            }
        }
    }

    private fun insertTransition(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        sequence: Int,
        mutation: String,
        partyId: UUID?,
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id,
                 occurred_at, party_id)
            VALUES (?, ?, ?, 'IN_PROGRESS', 'IN_PROGRESS', ?, 'USER', ?, now(), ?)
            """.trimIndent(),
            id,
            runtime.requestId,
            sequence,
            mutation,
            runtime.template.userId,
            partyId,
        )
        return id
    }

    private fun insertReminderIntent(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        kind: String,
        transitionId: UUID,
        clockEventId: UUID?,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_notice_intent
                (id, information_request_id, party_id, notice_kind, clock_event_id, transition_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            runtime.requestId,
            runtime.contributorPartyId,
            kind,
            clockEventId,
            transitionId,
        )
    }

    private fun count(connection: Connection, sql: String): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1)
            }
        }
}
