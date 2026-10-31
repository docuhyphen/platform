package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class RecordDisposalRowScopeContractTest {
    @Test
    fun `a disposal claim recognises the worksheet rows, notice numbers and source packages of its request and no other request's`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val preservation = PreservationSqlFixture(connection, runtime)
                val reviews = ReviewSqlFixture(connection, runtime)
                val notices = NoticeSqlFixture(connection, ClockSqlFixture(connection, runtime))
                val review = UUID.randomUUID()
                reviews.insertReview(review, 1)
                val intent = UUID.randomUUID()
                notices.insertClockIntent(intent, "RESPONSE_REMINDER", notices.clockEventId)
                notices.claim(intent)
                val notice = UUID.randomUUID()
                notices.insertNotice(notice, intent)
                val schedule = UUID.randomUUID()
                val claim = UUID.randomUUID()
                preservation.insertSchedule(schedule, 1, 0, 0)
                preservation.insertClaim(claim, schedule)
                preservation.advance(claim)
                queryString(
                    connection,
                    "SELECT set_config('docuhyphen.record_disposal_claim', ?, false)",
                    claim.toString()
                )
                val other = UUID.randomUUID().toString()

                assertAll(
                    {
                        assertEquals(
                            "true",
                            inScope(connection, "information_request_review_draft_item", "review_id", review.toString())
                        )
                    },
                    {
                        assertEquals(
                            "false",
                            inScope(connection, "information_request_review_draft_item", "review_id", other)
                        )
                    },
                    {
                        assertEquals(
                            "true",
                            inScope(
                                connection,
                                "information_request_notice_sequence_allocation",
                                "outbound_notice_id",
                                notice.toString()
                            )
                        )
                    },
                    {
                        assertEquals(
                            "false",
                            inScope(
                                connection,
                                "information_request_notice_sequence_allocation",
                                "outbound_notice_id",
                                other
                            )
                        )
                    },
                    {
                        assertEquals(
                            "true",
                            inScope(
                                connection,
                                "information_request_lineage_source_package",
                                "successor_request_id",
                                runtime.requestId.toString()
                            )
                        )
                    },
                    {
                        assertEquals(
                            "false",
                            inScope(
                                connection,
                                "information_request_lineage_source_package",
                                "successor_request_id",
                                other
                            )
                        )
                    },
                )
            }
        }
    }

    private fun inScope(connection: Connection, table: String, column: String, value: String): String? =
        queryString(
            connection,
            "SELECT record_disposal_row_in_progress(?, jsonb_build_object(?::text, ?::text))::text",
            table,
            column,
            value,
        )
}
