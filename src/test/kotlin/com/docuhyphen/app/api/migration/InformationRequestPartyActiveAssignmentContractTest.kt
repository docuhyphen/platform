package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestPartyActiveAssignmentContractTest {
    @Test
    fun `a principal holds one active party per role while other roles and revoked parties stay allowed`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)

                refusedBy(connection, "ux_information_request_party_active_principal") {
                    fixture.insertPartyForUser(UUID.randomUUID(), "CONTRIBUTOR", fixture.contributorUserId)
                }
                fixture.insertPartyForUser(UUID.randomUUID(), "DECISION_MAKER", fixture.contributorUserId)
                revoke(connection, fixture.contributorPartyId)
                fixture.insertPartyForUser(UUID.randomUUID(), "CONTRIBUTOR", fixture.contributorUserId)

                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_party WHERE information_request_id = ? AND principal_id = ? AND role_key = 'CONTRIBUTOR' AND active",
                        fixture.requestId,
                        fixture.contributorUserId,
                    ),
                )
            }
        }
    }

    @Test
    fun `a subject is named by one active subject party of a request`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val subjectId = UUID.randomUUID()
                val partyId = UUID.randomUUID()
                fixture.insertSubject(subjectId, partyId)

                refusedBy(connection, "ux_information_request_party_active_subject") {
                    insertSubjectParty(connection, fixture, subjectId)
                }
                revoke(connection, partyId)
                insertSubjectParty(connection, fixture, subjectId)
            }
        }
    }

    @Test
    fun `an upgraded database whose revoked and active parties repeat no active assignment takes both rules`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "174").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                revoke(connection, fixture.contributorPartyId)
                fixture.insertPartyForUser(UUID.randomUUID(), "CONTRIBUTOR", fixture.contributorUserId)

                submissionFlyway(postgres).migrate()

                assertEquals(
                    setOf(
                        "ux_information_request_party_active_principal",
                        "ux_information_request_party_active_subject"
                    ),
                    queryStrings(
                        connection,
                        "SELECT indexname FROM pg_indexes WHERE indexname LIKE 'ux_information_request_party_active_%'",
                    ),
                )
                refusedBy(connection, "ux_information_request_party_active_principal") {
                    fixture.insertPartyForUser(UUID.randomUUID(), "CONTRIBUTOR", fixture.contributorUserId)
                }
            }
        }
    }

    private fun revoke(connection: Connection, partyId: UUID) {
        execute(
            connection,
            "UPDATE information_request_party SET active = FALSE, revoked_at = now(), party_revision = party_revision + 1 WHERE id = ?",
            partyId,
        )
    }

    private fun insertSubjectParty(connection: Connection, fixture: SubmissionRuntimeSqlFixture, subjectId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, subject_identity_ref_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'SUBJECT', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            fixture.requestId,
            subjectId,
        )
    }
}
