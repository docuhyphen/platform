package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.*
import javax.sql.DataSource

internal object InformationRequestAuditReconciliation {
    fun assertReconciled(
        dataSource: DataSource,
        requestId: UUID,
        mutation: String,
        eventTypeKey: String,
        expected: Int,
        expectedReceipts: Int = expected,
    ) {
        dataSource.connection.use { connection ->
            assertEquals(
                expectedReceipts,
                queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", requestId),
                "committed command receipts",
            )
            assertEquals(
                expected,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation = ?",
                    requestId,
                    mutation,
                ),
                "committed transitions",
            )
            assertEquals(
                expected,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                    requestId.toString(),
                    eventTypeKey,
                ),
                "audit events",
            )
            assertEquals(
                0,
                queryInt(
                    connection,
                    """
                    SELECT count(*) FROM information_request_transition t
                    WHERE t.information_request_id = ? AND t.mutation = ?
                      AND NOT EXISTS (
                          SELECT 1 FROM audit_outbox a
                          WHERE a.business_transaction_id = t.id::text AND a.event_type_key = ?
                      )
                    """.trimIndent(),
                    requestId,
                    mutation,
                    eventTypeKey,
                ),
                "transitions without their own audit event",
            )
        }
    }

    fun grantRequestShare(
        dataSource: DataSource,
        fixture: SubmissionRuntimeSqlFixture,
        partyId: UUID,
        role: String,
        userId: UUID
    ) {
        dataSource.connection.use { connection ->
            val shareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                fixture.requestId,
                userId,
                role,
            )
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
    }
}
