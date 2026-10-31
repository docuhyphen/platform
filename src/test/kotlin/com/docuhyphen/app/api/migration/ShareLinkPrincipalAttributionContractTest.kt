package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.entity.PrincipalKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class ShareLinkPrincipalAttributionContractTest {
    @Test
    fun `access link creators support every canonical principal kind`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val linkId = insertLink(connection, fixture)
                assertCanonicalColumns(connection)
                PrincipalKind.entries.forEach { kind ->
                    val actorId = UUID.randomUUID()
                    execute(
                        connection, """
                        UPDATE share_link SET created_by_principal_kind = ?, created_by_principal_id = ? WHERE id = ?
                    """.trimIndent(), kind.name, actorId, linkId
                    )
                    assertEquals(kind.name, creator(connection, linkId, "kind"))
                    assertEquals(actorId.toString(), creator(connection, linkId, "id"))
                }
                refusedBy(connection, "ck_share_link_created_principal") {
                    execute(connection, "UPDATE share_link SET created_by_principal_kind = NULL WHERE id = ?", linkId)
                }
                refusedBy(connection, "ck_share_link_created_principal") {
                    execute(connection, "UPDATE share_link SET created_by_principal_id = NULL WHERE id = ?", linkId)
                }
                refusedBy(connection, "ck_share_link_created_principal") {
                    execute(
                        connection,
                        "UPDATE share_link SET created_by_principal_kind = 'ACCESS_SESSION' WHERE id = ?",
                        linkId
                    )
                }
                assertReplacedColumnRefused(connection, fixture, linkId)
            }
        }
    }

    @Test
    fun `upgrading populated V160 access links keeps recorded creators and leaves unknown ones absent`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "160").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val recordedLinkId = insertLink(connection, fixture, verified = false)
                val unknownLinkId = insertLink(connection, fixture, verified = false)
                execute(
                    connection,
                    "UPDATE share_link SET created_by_app_user_id = ? WHERE id = ?",
                    fixture.template.userId,
                    recordedLinkId
                )
                val linksBefore = queryString(
                    connection, """
                    SELECT json_agg(json_build_object('id', id, 'share', share_id, 'hash', token_hash, 'status', status,
                        'mode', link_mode, 'used', used_count, 'expires', extract(epoch from expires_at)) ORDER BY id)::text FROM share_link
                """.trimIndent()
                )

                submissionFlyway(postgres).migrate()

                assertCanonicalColumns(connection)
                assertEquals("USER", creator(connection, recordedLinkId, "kind"))
                assertEquals(fixture.template.userId.toString(), creator(connection, recordedLinkId, "id"))
                assertNull(creator(connection, unknownLinkId, "kind"))
                assertNull(creator(connection, unknownLinkId, "id"))
                assertEquals(
                    linksBefore, queryString(
                        connection, """
                    SELECT json_agg(json_build_object('id', id, 'share', share_id, 'hash', token_hash, 'status', status,
                        'mode', link_mode, 'used', used_count, 'expires', extract(epoch from expires_at)) ORDER BY id)::text FROM share_link
                """.trimIndent()
                    )
                )
                assertReplacedColumnRefused(connection, fixture, recordedLinkId)
            }
        }
    }

    private fun insertLink(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        verified: Boolean = true
    ): UUID {
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
        return UUID.randomUUID().also { linkId ->
            execute(
                connection, """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at${if (verified) ", verification_strength" else ""})
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 5, now() + interval '30 days', now()${if (verified) ", 'EMAIL_OTP'" else ""})
            """.trimIndent(), linkId, shareId, UUID.randomUUID().toString()
            )
        }
    }

    private fun assertCanonicalColumns(connection: Connection) {
        assertEquals(
            2, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'share_link'
              AND column_name IN ('created_by_principal_kind', 'created_by_principal_id')
        """.trimIndent()
            )
        )
        assertEquals(
            0, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'share_link'
              AND column_name = 'created_by_app_user_id'
        """.trimIndent()
            )
        )
    }

    private fun assertReplacedColumnRefused(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        linkId: UUID
    ) {
        refusedBy(connection, "created_by_app_user_id") {
            execute(
                connection,
                "UPDATE share_link SET created_by_app_user_id = ? WHERE id = ?",
                fixture.template.userId,
                linkId
            )
        }
    }

    private fun creator(connection: Connection, id: UUID, suffix: String): String? =
        queryString(
            connection,
            "SELECT to_jsonb(t) ->> 'created_by_principal_$suffix' FROM share_link t WHERE id = ?",
            id
        )
}
