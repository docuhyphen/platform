package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.entity.PrincipalKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestRuntimeAttributionContractTest {
    @Test
    fun `request creators and party assigners support every canonical principal kind`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                assertCanonicalColumns(connection)
                PrincipalKind.entries.forEach { kind ->
                    val actorId = UUID.randomUUID()
                    execute(
                        connection, """
                        UPDATE information_request
                        SET created_by_principal_kind = ?, created_by_principal_id = ? WHERE id = ?
                    """.trimIndent(), kind.name, actorId, fixture.requestId
                    )
                    execute(
                        connection, """
                        UPDATE information_request_party
                        SET assigned_by_principal_kind = ?, assigned_by_principal_id = ? WHERE id = ?
                    """.trimIndent(), kind.name, actorId, fixture.contributorPartyId
                    )
                    assertEquals(
                        kind.name,
                        actor(connection, "information_request", fixture.requestId, "created", "kind")
                    )
                    assertEquals(
                        actorId.toString(),
                        actor(connection, "information_request", fixture.requestId, "created", "id")
                    )
                    assertEquals(
                        kind.name,
                        actor(connection, "information_request_party", fixture.contributorPartyId, "assigned", "kind")
                    )
                    assertEquals(
                        actorId.toString(),
                        actor(connection, "information_request_party", fixture.contributorPartyId, "assigned", "id")
                    )
                }
                listOf(
                    Triple("information_request", fixture.requestId, "created"),
                    Triple("information_request_party", fixture.contributorPartyId, "assigned"),
                ).forEach { (table, id, operation) ->
                    val constraint = if (table == "information_request") "ck_information_request_created_principal"
                    else "ck_information_request_party_assigned_principal"
                    refusedBy(connection, constraint) {
                        execute(connection, "UPDATE $table SET ${operation}_by_principal_kind = NULL WHERE id = ?", id)
                    }
                    refusedBy(connection, constraint) {
                        execute(connection, "UPDATE $table SET ${operation}_by_principal_id = NULL WHERE id = ?", id)
                    }
                    refusedBy(connection, constraint) {
                        execute(
                            connection,
                            "UPDATE $table SET ${operation}_by_principal_kind = 'ACCESS_SESSION' WHERE id = ?",
                            id
                        )
                    }
                }
                assertReplacedColumnsRefused(connection, fixture)
            }
        }
    }

    @Test
    fun `upgrading populated V159 requests keeps recorded creators and assigners and recovers them from history`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "159").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val applicationId = UUID.randomUUID()
                val serviceAccountId = UUID.randomUUID()
                val recoveredRequestId = UUID.randomUUID()
                val unknownRequestId = UUID.randomUUID()
                val unknownPartyId = UUID.randomUUID()
                execute(
                    connection,
                    "UPDATE information_request SET created_by_app_user_id = ? WHERE id = ?",
                    fixture.template.userId,
                    fixture.requestId
                )
                execute(
                    connection,
                    "UPDATE information_request_party SET assigned_by_app_user_id = ? WHERE id = ?",
                    fixture.template.userId,
                    fixture.contributorPartyId
                )
                insertTransition(
                    connection,
                    fixture.requestId,
                    1,
                    "ISSUED",
                    "ASSIGN_PARTY",
                    "SERVICE_ACCOUNT",
                    serviceAccountId,
                    fixture.attestorPartyId
                )
                listOf(recoveredRequestId, unknownRequestId).forEach { requestId ->
                    insertDraftRequest(
                        connection,
                        fixture,
                        requestId
                    )
                }
                insertTransition(
                    connection,
                    recoveredRequestId,
                    1,
                    "DRAFT",
                    "CREATE_DRAFT",
                    "APPLICATION",
                    applicationId,
                    null
                )
                execute(
                    connection, """
                    INSERT INTO information_request_party
                        (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                         assigned_at, created_at, updated_at)
                    VALUES (?, ?, 'CONTRIBUTOR', 'USER', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(), unknownPartyId, unknownRequestId, fixture.contributorUserId
                )
                val historyBefore = queryString(
                    connection,
                    "SELECT jsonb_agg(to_jsonb(t) - 'idempotency_key' ORDER BY information_request_id, sequence_number)::text FROM information_request_transition t"
                )

                submissionFlyway(postgres).migrate()

                assertCanonicalColumns(connection)
                assertEquals("USER", actor(connection, "information_request", fixture.requestId, "created", "kind"))
                assertEquals(
                    fixture.template.userId.toString(),
                    actor(connection, "information_request", fixture.requestId, "created", "id")
                )
                assertEquals(
                    "APPLICATION",
                    actor(connection, "information_request", recoveredRequestId, "created", "kind")
                )
                assertEquals(
                    applicationId.toString(),
                    actor(connection, "information_request", recoveredRequestId, "created", "id")
                )
                assertNull(actor(connection, "information_request", unknownRequestId, "created", "kind"))
                assertNull(actor(connection, "information_request", unknownRequestId, "created", "id"))
                assertEquals(
                    "USER",
                    actor(connection, "information_request_party", fixture.contributorPartyId, "assigned", "kind")
                )
                assertEquals(
                    fixture.template.userId.toString(),
                    actor(connection, "information_request_party", fixture.contributorPartyId, "assigned", "id")
                )
                assertEquals(
                    "SERVICE_ACCOUNT",
                    actor(connection, "information_request_party", fixture.attestorPartyId, "assigned", "kind")
                )
                assertEquals(
                    serviceAccountId.toString(),
                    actor(connection, "information_request_party", fixture.attestorPartyId, "assigned", "id")
                )
                assertNull(actor(connection, "information_request_party", unknownPartyId, "assigned", "kind"))
                assertNull(actor(connection, "information_request_party", unknownPartyId, "assigned", "id"))
                assertEquals(
                    historyBefore, queryString(
                        connection,
                        "SELECT jsonb_agg(to_jsonb(t) - 'idempotency_key' ORDER BY information_request_id, sequence_number)::text FROM information_request_transition t"
                    )
                )
                assertReplacedColumnsRefused(connection, fixture)
            }
        }
    }

    private fun insertDraftRequest(connection: Connection, fixture: SubmissionRuntimeSqlFixture, requestId: UUID) {
        execute(
            connection, """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
        """.trimIndent(), requestId, fixture.exchangeId, fixture.template.versionId, fixture.template.organizationId
        )
    }

    private fun insertTransition(
        connection: Connection,
        requestId: UUID,
        sequence: Int,
        state: String,
        mutation: String,
        actorKind: String,
        actorId: UUID,
        partyId: UUID?,
    ) {
        execute(
            connection, """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id,
                 party_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """.trimIndent(), UUID.randomUUID(), requestId, sequence, state.takeIf { mutation != "CREATE_DRAFT" }, state,
            mutation, actorKind, actorId, partyId
        )
    }

    private fun assertCanonicalColumns(connection: Connection) {
        assertEquals(
            4, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
              AND ((table_name = 'information_request'
                    AND column_name IN ('created_by_principal_kind', 'created_by_principal_id'))
                OR (table_name = 'information_request_party'
                    AND column_name IN ('assigned_by_principal_kind', 'assigned_by_principal_id')))
        """.trimIndent()
            )
        )
        assertEquals(
            0, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
              AND ((table_name = 'information_request' AND column_name = 'created_by_app_user_id')
                OR (table_name = 'information_request_party' AND column_name = 'assigned_by_app_user_id'))
        """.trimIndent()
            )
        )
    }

    private fun assertReplacedColumnsRefused(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
        refusedBy(connection, "created_by_app_user_id") {
            execute(
                connection,
                "UPDATE information_request SET created_by_app_user_id = ? WHERE id = ?",
                fixture.template.userId,
                fixture.requestId
            )
        }
        refusedBy(connection, "assigned_by_app_user_id") {
            execute(
                connection,
                "UPDATE information_request_party SET assigned_by_app_user_id = ? WHERE id = ?",
                fixture.template.userId,
                fixture.contributorPartyId
            )
        }
    }

    private fun actor(connection: Connection, table: String, id: UUID, operation: String, suffix: String): String? =
        queryString(
            connection,
            "SELECT to_jsonb(t) ->> '${operation}_by_principal_$suffix' FROM $table t WHERE id = ?",
            id
        )
}
