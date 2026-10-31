package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestRefreshRuleRetryContractTest {
    @Test
    fun `a refresh rule starts with no retry and holds a retry time exactly while it counts failed attempts`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val ruleId = insertRule(connection, fixture)

                assertEquals("0|", retryOf(connection, ruleId))
                refusedBy(connection, "change only their retry state") {
                    execute(
                        connection,
                        "UPDATE information_request_refresh_rule SET lead_days = 5 WHERE id = ?",
                        ruleId
                    )
                }
                refusedBy(connection, "change only their retry state") {
                    execute(
                        connection,
                        "UPDATE information_request_refresh_rule SET requirement_key = 'other-record' WHERE id = ?",
                        ruleId
                    )
                }
                refusedBy(connection, "change only their retry state") {
                    execute(connection, "DELETE FROM information_request_refresh_rule WHERE id = ?", ruleId)
                }
                refusedBy(connection, "ck_information_request_refresh_rule_attempt_count") {
                    execute(
                        connection,
                        "UPDATE information_request_refresh_rule SET attempt_count = -1 WHERE id = ?",
                        ruleId
                    )
                }
                refusedBy(connection, "ck_information_request_refresh_rule_retry") {
                    execute(
                        connection,
                        "UPDATE information_request_refresh_rule SET attempt_count = 1 WHERE id = ?",
                        ruleId
                    )
                }
                refusedBy(connection, "ck_information_request_refresh_rule_retry") {
                    execute(
                        connection,
                        "UPDATE information_request_refresh_rule SET next_attempt_at = now() WHERE id = ?",
                        ruleId
                    )
                }
                execute(
                    connection,
                    "UPDATE information_request_refresh_rule SET attempt_count = 2, next_attempt_at = TIMESTAMPTZ '2027-01-01 00:00:00+00' WHERE id = ?",
                    ruleId,
                )
                assertEquals("2|2027-01-01 00:00:00+00", retryOf(connection, ruleId))
                execute(
                    connection,
                    "UPDATE information_request_refresh_rule SET attempt_count = 0, next_attempt_at = NULL WHERE id = ?",
                    ruleId
                )
                assertEquals("0|", retryOf(connection, ruleId))
            }
        }
    }

    @Test
    fun `upgrading populated refresh rules leaves every rule ready for its next pass`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "172").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val ruleId = insertRule(connection, fixture)

                submissionFlyway(postgres).migrate()

                assertEquals("0|", retryOf(connection, ruleId))
                assertEquals(
                    "NO|YES",
                    queryString(
                        connection,
                        """
                        SELECT string_agg(is_nullable, '|' ORDER BY column_name)
                        FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'information_request_refresh_rule'
                          AND column_name IN ('attempt_count', 'next_attempt_at')
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    private fun insertRule(connection: Connection, fixture: SubmissionRuntimeSqlFixture): UUID {
        val ruleId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_refresh_rule
                (id, information_request_id, requirement_key, lead_days, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, 'supporting-record', 30, 'USER', ?)
            """.trimIndent(),
            ruleId,
            fixture.requestId,
            fixture.template.userId,
        )
        return ruleId
    }

    private fun retryOf(connection: Connection, ruleId: UUID): String? =
        queryString(
            connection,
            """
            SELECT attempt_count || '|' || COALESCE(to_char(next_attempt_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') || '+00', '')
            FROM information_request_refresh_rule WHERE id = ?
            """.trimIndent(),
            ruleId,
        )
}
