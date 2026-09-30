package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class RequestExecutionQuotaMigrationUpgradeTest
{
    @Test
    fun `an upgraded database keeps each grant's cap as its acting-party cap and moves reservations to the new kind`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, target = "149").migrate()
            val grantId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                execute(
                    connection,
                    """
                    INSERT INTO request_execution_grant
                        (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                         enforcement_mode, additional_recipient_cap, issued_at, created_at)
                    VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 3, now(), now())
                    """.trimIndent(),
                    grantId,
                    runtime.requestId,
                    runtime.template.organizationId,
                )
                execute(
                    connection,
                    """
                    INSERT INTO request_execution_usage_reservation
                        (id, grant_id, usage_kind, reservation_key, quantity, status, reserved_at, consumed_at, created_at)
                    VALUES (?, ?, 'ADDITIONAL_RECIPIENT', 'information_request.party|existing', 1, 'CONSUMED', now(), now(), now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    grantId,
                )
            }

            submissionFlyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals(
                    "3||",
                    queryString(
                        connection,
                        """
                        SELECT acting_party_cap || '|' || COALESCE(evidence_file_allowance::text, '') || '|' ||
                               COALESCE(evidence_byte_allowance::text, '')
                        FROM request_execution_grant WHERE id = ?
                        """.trimIndent(),
                        grantId,
                    ),
                )
                assertEquals(
                    setOf("ACTING_PARTY"),
                    queryStrings(connection, "SELECT usage_kind FROM request_execution_usage_reservation WHERE grant_id = ?", grantId),
                )
            }
        }
    }
}
