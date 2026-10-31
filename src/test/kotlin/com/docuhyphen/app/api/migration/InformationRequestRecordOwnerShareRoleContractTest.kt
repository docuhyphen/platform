package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestRecordOwnerShareRoleContractTest {
    @Test
    fun `a request Share may hold the read-only record owner role and no other resource may`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                insertShare(connection, "INFORMATION_REQUEST", "RECORD_OWNER")
                refusedBy(connection, "share_role_name_check") { insertShare(connection, "EXCHANGE", "RECORD_OWNER") }
                refusedBy(connection, "share_role_name_check") {
                    insertShare(
                        connection,
                        "INFORMATION_REQUEST",
                        "VIEWER"
                    )
                }
            }
        }
    }

    @Test
    fun `existing request and Exchange Shares survive the upgrade`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "163").migrate()
            postgres.createConnection("").use { connection ->
                val requestShare = insertShare(connection, "INFORMATION_REQUEST", "DECISION_MAKER")
                val exchangeShare = insertShare(connection, "EXCHANGE", "OWNER")
                val retainedRead = insertShare(connection, "INFORMATION_REQUEST", "SUBJECT")
                refusedBy(connection, "share_role_name_check") {
                    insertShare(
                        connection,
                        "INFORMATION_REQUEST",
                        "RECORD_OWNER"
                    )
                }

                submissionFlyway(postgres).migrate()

                assertEquals(
                    "DECISION_MAKER",
                    queryString(connection, "SELECT role_name FROM share WHERE id = ?", requestShare)
                )
                assertEquals(
                    "OWNER",
                    queryString(connection, "SELECT role_name FROM share WHERE id = ?", exchangeShare)
                )
                assertEquals(
                    "RECORD_OWNER",
                    queryString(connection, "SELECT role_name FROM share WHERE id = ?", retainedRead)
                )
                insertShare(connection, "INFORMATION_REQUEST", "RECORD_OWNER")
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
