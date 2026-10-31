package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class RequestAccessSessionStepUpContractTest {
    @Test
    fun `an access session holds one pending step-up code with its attempts and records each step-up`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val sessionId = session(connection, fixture)

                assertEquals("0|0||", stepUpState(connection, sessionId))
                execute(
                    connection, """
                    UPDATE request_access_session
                    SET step_up_code_hash = 'code-hash', step_up_code_expires_at = now() + interval '10 minutes',
                        step_up_challenge_count = 1, step_up_failed_attempts = 2, step_up_locked_until = now(), stepped_up_at = now()
                    WHERE id = ?
                """.trimIndent(), sessionId
                )
                assertEquals("1|2|code-hash|set", stepUpState(connection, sessionId))
                refusedBy(connection, "ck_request_access_session_step_up_code") {
                    execute(
                        connection,
                        "UPDATE request_access_session SET step_up_code_expires_at = NULL WHERE id = ?",
                        sessionId
                    )
                }
                refusedBy(connection, "ck_request_access_session_step_up_attempts") {
                    execute(
                        connection,
                        "UPDATE request_access_session SET step_up_failed_attempts = -1 WHERE id = ?",
                        sessionId
                    )
                }
                refusedBy(connection, "ck_request_access_session_step_up_attempts") {
                    execute(
                        connection,
                        "UPDATE request_access_session SET step_up_challenge_count = -1 WHERE id = ?",
                        sessionId
                    )
                }
                refusedBy(connection, "identity is immutable") {
                    execute(
                        connection,
                        "UPDATE request_access_session SET participant_principal_id = ? WHERE id = ?",
                        UUID.randomUUID(),
                        sessionId
                    )
                }
                transition(connection, fixture, 101, "STEP_UP_ACCESS_SESSION")
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 101",
                        fixture.requestId
                    )
                }
            }
        }
    }

    @Test
    fun `a populated V157 schema gains step-up state without changing its sessions or history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "157").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val sessionId = session(connection, fixture, verified = false)
                transition(connection, fixture, 101, "ISSUE_ACCESS_SESSION", keyed = false)
                val sessionBefore = sessionIdentity(connection, sessionId)

                submissionFlyway(postgres).migrate()

                assertEquals(sessionBefore, sessionIdentity(connection, sessionId))
                assertEquals("0|0||", stepUpState(connection, sessionId))
                assertNull(
                    queryString(
                        connection,
                        "SELECT stepped_up_at::text FROM request_access_session WHERE id = ?",
                        sessionId
                    )
                )
                transition(connection, fixture, 102, "STEP_UP_ACCESS_SESSION")
                assertEquals(
                    "ISSUE_ACCESS_SESSION",
                    queryString(
                        connection,
                        "SELECT mutation FROM information_request_transition WHERE information_request_id = ? AND sequence_number = 101",
                        fixture.requestId
                    )
                )
            }
        }
    }

    private fun session(connection: Connection, fixture: SubmissionRuntimeSqlFixture, verified: Boolean = true): UUID {
        val participantId = UUID.randomUUID()
        val email = "session-${participantId.toString().take(8)}@process.test"
        execute(
            connection, """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, ?, TRUE, now())
        """.trimIndent(), participantId, fixture.template.organizationId, email, email
        )
        val shareId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), shareId, fixture.requestId, participantId
        )
        val linkId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share_link
                (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                 contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at${if (verified) ", verification_strength" else ""})
            VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 1, 0, 5, now() + interval '30 days', now()${if (verified) ", 'EMAIL_OTP'" else ""})
        """.trimIndent(), linkId, shareId, UUID.randomUUID().toString()
        )
        return UUID.randomUUID().also { sessionId ->
            execute(
                connection, """
                INSERT INTO request_access_session
                    (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                     issued_at, expires_at, credential_hash, created_at)
                VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 day', ?, now())
            """.trimIndent(), sessionId, linkId, participantId, "a".repeat(64)
            )
        }
    }

    private fun stepUpState(connection: Connection, sessionId: UUID): String? =
        queryString(
            connection, """
            SELECT concat_ws('|', step_up_challenge_count, step_up_failed_attempts, COALESCE(step_up_code_hash, ''),
                             CASE WHEN stepped_up_at IS NULL THEN '' ELSE 'set' END)
            FROM request_access_session WHERE id = ?
        """.trimIndent(), sessionId
        )

    private fun sessionIdentity(connection: Connection, sessionId: UUID): String? =
        queryString(
            connection, """
            SELECT concat_ws('|', share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                             extract(epoch from issued_at), extract(epoch from expires_at),
                             extract(epoch from revoked_at), use_count, credential_hash)
            FROM request_access_session WHERE id = ?
        """.trimIndent(), sessionId
        )

    private fun transition(
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
}
