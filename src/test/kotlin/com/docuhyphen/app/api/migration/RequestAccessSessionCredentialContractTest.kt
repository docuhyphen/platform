package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class RequestAccessSessionCredentialContractTest
{
    @Test
    fun `every access session holds a hashed credential and an expiry`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val linkId = shareLink(connection)
                val sessionId = session(connection, linkId, "a".repeat(64))

                refusedBy(connection, "credential_hash") { session(connection, linkId, null) }
                refusedBy(connection, "expires_at") { session(connection, linkId, "b".repeat(64), expires = false) }
                refusedBy(connection, "ck_request_access_session_credential") {
                    session(
                        connection,
                        linkId,
                        "not-a-hash"
                    )
                }
                refusedBy(connection, "uq_request_access_session_credential") {
                    session(
                        connection,
                        linkId,
                        "a".repeat(64)
                    )
                }
                refusedBy(connection, "expires_at") {
                    execute(connection, "UPDATE request_access_session SET expires_at = NULL WHERE id = ?", sessionId)
                }
                refusedBy(connection, "identity is immutable") {
                    execute(
                        connection,
                        "UPDATE request_access_session SET credential_hash = ? WHERE id = ?",
                        "c".repeat(64),
                        sessionId
                    )
                }
                execute(
                    connection,
                    "UPDATE request_access_session SET use_count = use_count + 1, revoked_at = now() WHERE id = ?",
                    sessionId
                )
                assertEquals("1|revoked", state(connection, sessionId))
            }
        }
    }

    @Test
    fun `an upgrade removes sessions that never held a credential and keeps every credentialed session`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "169").migrate()
            postgres.createConnection("").use { connection ->
                val linkId = shareLink(connection)
                val credentialed = session(connection, linkId, "d".repeat(64))
                val legacy = session(connection, linkId, null, expires = false)
                val before = identity(connection, credentialed)

                submissionFlyway(postgres).migrate()

                assertEquals(before, identity(connection, credentialed))
                assertNull(identity(connection, legacy))
            }
        }
    }

    private fun shareLink(connection: Connection): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), shareId, UUID.randomUUID(), UUID.randomUUID()
        )
        return UUID.randomUUID().also { linkId ->
            execute(
                connection, """
                INSERT INTO share_link (id, share_id, token_hash, status, created_at, link_mode, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', now(), 'VERIFICATION_BOOTSTRAP', 'EMAIL_OTP')
            """.trimIndent(), linkId, shareId, "hash-${UUID.randomUUID()}"
            )
        }
    }

    private fun session(connection: Connection, linkId: UUID, credentialHash: String?, expires: Boolean = true): UUID =
        UUID.randomUUID().also { sessionId ->
            execute(
                connection, """
                INSERT INTO request_access_session
                    (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                     issued_at, expires_at, credential_hash, created_at)
                VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), ${if (expires) "now() + interval '1 day'" else "NULL"}, ?, now())
            """.trimIndent(), sessionId, linkId, UUID.randomUUID(), credentialHash
            )
        }

    private fun state(connection: Connection, sessionId: UUID): String? =
        queryString(
            connection, """
            SELECT concat_ws('|', use_count, CASE WHEN revoked_at IS NULL THEN 'active' ELSE 'revoked' END)
            FROM request_access_session WHERE id = ?
        """.trimIndent(), sessionId
        )

    private fun identity(connection: Connection, sessionId: UUID): String? =
        queryString(
            connection, """
            SELECT concat_ws('|', share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                             extract(epoch from issued_at), extract(epoch from expires_at),
                             extract(epoch from revoked_at), use_count, credential_hash)
            FROM request_access_session WHERE id = ?
        """.trimIndent(), sessionId
        )
}
