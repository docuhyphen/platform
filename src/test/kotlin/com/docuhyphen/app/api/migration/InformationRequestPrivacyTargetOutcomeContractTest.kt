package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestPrivacyTargetOutcomeContractTest {
    @Test
    fun `a deletion target records a request still open as skipped without a reason or claim`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val privacy = PrivacySqlFixture(connection, SubmissionRuntimeSqlFixture(connection))
                val skipped = UUID.randomUUID()
                privacy.insertRequest(skipped, kind = "DELETION")

                privacy.insertTarget(skipped, "SKIPPED_OPEN")

                assertEquals("SKIPPED_OPEN", outcomeOf(connection, skipped))
                val refusing = UUID.randomUUID()
                privacy.insertRequest(refusing, kind = "DELETION")
                refusedBy(connection, "ck_information_request_privacy_target_reason") {
                    privacy.insertTarget(refusing, "SKIPPED_OPEN", reason = "INFORMATION_REQUEST_RECORD_NOT_FINISHED")
                }
                refusedBy(connection, "ck_information_request_privacy_target_outcome") {
                    privacy.insertTarget(refusing, "IGNORED")
                }
                refusedBy(connection, "record preservation history is append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_privacy_target SET outcome = 'REFUSED' WHERE privacy_request_id = ?",
                        skipped
                    )
                }
            }
        }
    }

    @Test
    fun `upgrading populated V162 privacy targets keeps every recorded outcome and admits skipped targets`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "162").migrate()
            postgres.createConnection("").use { connection ->
                val privacy = PrivacySqlFixture(connection, SubmissionRuntimeSqlFixture(connection))
                val recorded = listOf("EXPORTED", "CORRECTED", "RESTRICTED", "REFUSED").associateWith { outcome ->
                    UUID.randomUUID().also { id ->
                        privacy.insertRequest(id, kind = "ACCESS")
                        privacy.insertTarget(
                            id,
                            outcome,
                            reason = "INFORMATION_REQUEST_RECORD_HELD".takeIf { outcome == "REFUSED" })
                    }
                }
                val before = targets(connection)
                val pending = UUID.randomUUID()
                privacy.insertRequest(pending, kind = "DELETION")
                refusedBy(connection, "ck_information_request_privacy_target_outcome") {
                    privacy.insertTarget(pending, "SKIPPED_OPEN")
                }

                submissionFlyway(postgres).migrate()

                assertEquals(before, targets(connection))
                recorded.forEach { (outcome, id) -> assertEquals(outcome, outcomeOf(connection, id)) }
                privacy.insertTarget(pending, "SKIPPED_OPEN")
                assertEquals("SKIPPED_OPEN", outcomeOf(connection, pending))
            }
        }
    }

    private fun outcomeOf(connection: Connection, privacyRequestId: UUID): String? =
        queryString(
            connection,
            "SELECT outcome FROM information_request_privacy_target WHERE privacy_request_id = ?",
            privacyRequestId
        )

    private fun targets(connection: Connection): String? =
        queryString(
            connection,
            """
            SELECT json_agg(json_build_object('privacy', privacy_request_id, 'request', information_request_id, 'outcome', outcome,
                'reason', reason_code, 'claim', disposal_claim_id) ORDER BY privacy_request_id)::text
            FROM information_request_privacy_target
            """.trimIndent(),
        )
}
