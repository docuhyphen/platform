package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class ShareLinkBootstrapStateContractTest {
    @Test
    fun `access links state their verification strength and use and refuse settings no access link enforces`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                assertColumns(connection)
                val linkId = insertLink(connection, fixture, withStrength = true)
                execute(
                    connection,
                    "UPDATE share_link SET last_used_at = now(), status = 'REVOKED', revoked_at = now() WHERE id = ?",
                    linkId
                )
                assertEquals(
                    "EMAIL_OTP",
                    queryString(connection, "SELECT verification_strength FROM share_link WHERE id = ?", linkId)
                )
                refusedBy(connection, "ck_share_link_bootstrap_verification") {
                    execute(connection, "UPDATE share_link SET verification_strength = NULL WHERE id = ?", linkId)
                }
                refusedBy(connection, "ck_share_link_verification_strength") {
                    execute(connection, "UPDATE share_link SET verification_strength = 'PASSWORD' WHERE id = ?", linkId)
                }
                refusedBy(connection, "ck_share_link_bootstrap_mfa") {
                    execute(connection, "UPDATE share_link SET require_mfa = TRUE WHERE id = ?", linkId)
                }
                listOf("password_hash", "domain_allowlist").forEach { column ->
                    refusedBy(connection, column) {
                        execute(connection, "UPDATE share_link SET $column = NULL WHERE id = ?", linkId)
                    }
                }
            }
        }
    }

    @Test
    fun `upgrading populated V161 access links records their email code verification and keeps their state`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "161").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val activeId = insertLink(connection, fixture, withStrength = false)
                val revokedId = insertLink(connection, fixture, withStrength = false)
                execute(connection, "UPDATE share_link SET status = 'REVOKED', used_count = 2 WHERE id = ?", revokedId)
                val before = linkState(connection)

                submissionFlyway(postgres).migrate()

                assertColumns(connection)
                listOf(activeId, revokedId).forEach { id ->
                    assertEquals(
                        "EMAIL_OTP",
                        queryString(connection, "SELECT verification_strength FROM share_link WHERE id = ?", id)
                    )
                    assertNull(queryString(connection, "SELECT last_used_at::text FROM share_link WHERE id = ?", id))
                    assertNull(queryString(connection, "SELECT revoked_at::text FROM share_link WHERE id = ?", id))
                }
                assertEquals(before, linkState(connection))
            }
        }
    }

    private fun assertColumns(connection: Connection) {
        assertEquals(
            3, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'share_link'
              AND column_name IN ('verification_strength', 'last_used_at', 'revoked_at')
        """.trimIndent()
            )
        )
        assertEquals(
            0, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'share_link'
              AND column_name IN ('password_hash', 'domain_allowlist')
        """.trimIndent()
            )
        )
    }

    private fun linkState(connection: Connection): String? =
        queryString(
            connection, """
            SELECT json_agg(json_build_object('id', id, 'share', share_id, 'hash', token_hash, 'status', status,
                'mode', link_mode, 'used', used_count, 'mfa', require_mfa) ORDER BY id)::text FROM share_link
        """.trimIndent()
        )

    private fun insertLink(connection: Connection, fixture: SubmissionRuntimeSqlFixture, withStrength: Boolean): UUID {
        val participantId = UUID.randomUUID()
        val email = "link-${participantId.toString().take(8)}@process.test"
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
        val strengthColumn = if (withStrength) ", verification_strength" else ""
        val strengthValue = if (withStrength) ", 'EMAIL_OTP'" else ""
        execute(
            connection, """
            INSERT INTO share_link
                (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                 contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at$strengthColumn)
            VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 5, now() + interval '30 days', now()$strengthValue)
        """.trimIndent(), linkId, shareId, UUID.randomUUID().toString()
        )
        return linkId
    }
}
