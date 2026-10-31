package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestSubjectShareRoleContractTest {
    @Test
    fun `a request Share cannot hold the subject role, and the request Shares already granted survive the upgrade`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "190").migrate()
            postgres.createConnection("").use { connection ->
                val granted =
                    listOf("CONTRIBUTOR", "PREPARER", "ATTESTOR", "REVIEWER", "DECISION_MAKER", "RECORD_OWNER")
                        .associateWith { role -> insertShare(connection, "INFORMATION_REQUEST", role) }
                val exchangeShare = insertShare(connection, "EXCHANGE", "OWNER")

                submissionFlyway(postgres).migrate()

                granted.forEach { (role, id) ->
                    assertEquals(role, queryString(connection, "SELECT role_name FROM share WHERE id = ?", id))
                }
                assertEquals(
                    "OWNER",
                    queryString(connection, "SELECT role_name FROM share WHERE id = ?", exchangeShare)
                )
                refusedBy(connection, "share_role_name_check") {
                    insertShare(
                        connection,
                        "INFORMATION_REQUEST",
                        "SUBJECT"
                    )
                }
            }
        }
    }

    private fun insertShare(connection: java.sql.Connection, resourceType: String, role: String): UUID =
        UUID.randomUUID().also { id ->
            execute(
                connection, """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, ?, ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(), id, resourceType, UUID.randomUUID(), UUID.randomUUID(), role
            )
        }
}
