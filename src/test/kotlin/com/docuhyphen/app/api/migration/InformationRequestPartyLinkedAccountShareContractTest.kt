package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestPartyLinkedAccountShareContractTest {
    @Test
    fun `only a participant party can link the account Share its upgrade granted`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val participantPartyId = participantParty(connection, fixture)
                val accountShareId = accountShare(connection, fixture)

                execute(
                    connection,
                    "UPDATE information_request_party SET linked_user_share_id = ? WHERE id = ?",
                    accountShareId,
                    participantPartyId
                )

                assertEquals(accountShareId.toString(), linkedShare(connection, participantPartyId))
                refusedBy(connection, "information_request_party_linked_user_share_fkey") {
                    execute(
                        connection,
                        "UPDATE information_request_party SET linked_user_share_id = ? WHERE id = ?",
                        UUID.randomUUID(),
                        participantPartyId
                    )
                }
                refusedBy(connection, "ck_information_request_party_linked_user_share") {
                    execute(
                        connection,
                        "UPDATE information_request_party SET linked_user_share_id = ? WHERE id = ?",
                        accountShareId,
                        fixture.contributorPartyId
                    )
                }
                refusedBy(connection, "ck_information_request_party_linked_user_share") {
                    execute(
                        connection,
                        "UPDATE information_request_party SET principal_kind = 'USER' WHERE id = ?",
                        participantPartyId
                    )
                }
            }
        }
    }

    @Test
    fun `a populated V156 schema gains the link without changing its parties`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "156").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val participantPartyId = participantParty(connection, fixture)
                val partiesBefore = parties(connection, fixture)

                submissionFlyway(postgres).migrate()

                assertEquals(partiesBefore, parties(connection, fixture))
                assertNull(linkedShare(connection, participantPartyId))
                val accountShareId = accountShare(connection, fixture)
                execute(
                    connection,
                    "UPDATE information_request_party SET linked_user_share_id = ? WHERE id = ?",
                    accountShareId,
                    participantPartyId
                )
                assertEquals(accountShareId.toString(), linkedShare(connection, participantPartyId))
            }
        }
    }

    private fun participantParty(connection: Connection, fixture: SubmissionRuntimeSqlFixture): UUID {
        val participantId = UUID.randomUUID()
        val email = "linked-${participantId.toString().take(8)}@process.test"
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
        return UUID.randomUUID().also { partyId ->
            execute(
                connection, """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'CONTRIBUTOR', 'PARTICIPANT', ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(), partyId, fixture.requestId, participantId, shareId
            )
        }
    }

    private fun accountShare(connection: Connection, fixture: SubmissionRuntimeSqlFixture): UUID =
        UUID.randomUUID().also { shareId ->
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), shareId, fixture.requestId, fixture.attestorUserId
            )
        }

    private fun linkedShare(connection: Connection, partyId: UUID): String? =
        queryString(
            connection,
            "SELECT linked_user_share_id::text FROM information_request_party WHERE id = ?",
            partyId
        )

    private fun parties(connection: Connection, fixture: SubmissionRuntimeSqlFixture): Set<String> =
        queryStrings(
            connection, """
            SELECT concat_ws('|', id, role_key, principal_kind, principal_id, share_id, active, party_revision)
            FROM information_request_party WHERE information_request_id = ?
        """.trimIndent(), fixture.requestId
        )
}
