package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class InformationRequestStorageBoundaryContractTest {
    @Test
    fun `persisted actor and verification vocabularies match their storage checks`() {
        val principals = com.docuhyphen.app.api.model.entity.PrincipalKind.entries.map { it.name }.toSet()
        val checks = listOf(
            Triple("information_request_delegated_authority", "grantor_principal_kind", principals),
            Triple("information_request_delegated_authority", "revoked_by_principal_kind", principals),
            Triple("request_access_session", "participant_principal_kind", principals),
            Triple(
                "request_access_session", "verification_strength",
                com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength.entries.map { it.name }
                    .toSet()
            ),
        )
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                assertAll(checks.map { (table, column, expected) ->
                    Executable {
                        val definition = queryString(
                            connection, """
                        SELECT string_agg(pg_get_constraintdef(constraint_record.oid), ' ')
                        FROM pg_constraint constraint_record
                        JOIN pg_class table_record ON table_record.oid = constraint_record.conrelid
                        JOIN pg_attribute attribute ON attribute.attrelid = table_record.oid
                        WHERE table_record.relname = ? AND attribute.attname = ?
                          AND constraint_record.contype = 'c' AND attribute.attnum = ANY(constraint_record.conkey)
                    """.trimIndent(), table, column
                        ).orEmpty()
                        val stored = Regex("'([A-Z][A-Z_0-9]*)'").findAll(definition).map { it.groupValues[1] }.toSet()
                        assertEquals(expected, stored, "$table.$column")
                    }
                })
            }
        }
    }

    @Test
    fun `an archived response belongs to its recorded request and Requirement`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val first = SubmissionRuntimeSqlFixture(connection)
                val other = SubmissionRuntimeSqlFixture(connection)
                fun insert(requestId: java.util.UUID, requirementId: java.util.UUID) = execute(
                    connection, """
                    INSERT INTO information_request_response_archive
                        (id, information_request_id, information_request_requirement_id, response_id, occurrence_path,
                         condition_rule_key, response_revision, disposition, archived_by_principal_kind, archived_by_principal_id)
                    VALUES (?, ?, ?, ?, 'records[1]', 'conditional-record', 1, 'PROVIDED', 'USER', ?)
                """.trimIndent(), java.util.UUID.randomUUID(), requestId, requirementId,
                    first.documentResponseId, first.template.userId
                )
                insert(first.requestId, first.documentRequirementId)
                refusedBy(connection, "an archived response belongs to its request and Requirement") {
                    insert(other.requestId, other.documentRequirementId)
                }
            }
        }
    }

    @Test
    fun `stored execution and condition moments use instant columns`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val columns = listOf(
                    "information_request_template_condition_predicate" to "datetime_value",
                    "request_access_session" to "issued_at", "request_access_session" to "expires_at",
                    "share_link" to "contact_otp_expires_at", "share_link" to "rotated_at",
                    "request_execution_grant" to "issued_at", "command_receipt" to "created_at",
                    "information_request_group_occurrence" to "created_at", "participant_account_link" to "created_at",
                )
                assertAll(columns.map { (table, column) ->
                    Executable {
                        assertEquals(
                            "timestamp with time zone", queryString(
                                connection,
                                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                                table, column
                            ), "$table.$column"
                        )
                    }
                })
            }
        }
    }

    @Test
    fun `owner and retained record queries have unfiltered leading column indexes`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val lookups = listOf(
                    "information_request" to "owner_organization_id", "information_request" to "owner_user_id",
                    "information_request_party" to "share_id", "information_request_party" to "exchange_recipient_id",
                    "information_request" to "superseded_by_request_id",
                    "participant_account_link" to "linked_via_information_request_id",
                    "information_request_evidence_version" to "information_request_id",
                    "information_request_submission_evidence" to "information_request_id",
                    "information_request_item_correction" to "information_request_id",
                    "information_request_imported_value_decision" to "information_request_id",
                    "information_request_imported_value_discrepancy" to "information_request_id",
                    "information_request_imported_value_discrepancy_resolution" to "information_request_id",
                    "information_request_submission_withdrawal" to "information_request_id",
                    "information_request_carry_forward" to "information_request_id",
                    "information_request_correction" to "information_request_id",
                    "information_request_review_remediation" to "information_request_id",
                    "information_request_notice_claim" to "information_request_id",
                    "information_request_review_assignment" to "review_id", "domain_event_consumption" to "event_id",
                    "document_version" to "storage_locator",
                )
                assertAll(lookups.map { (table, column) ->
                    Executable {
                        assertEquals(
                            1, queryInt(
                                connection, """
                        SELECT CASE WHEN EXISTS (
                            SELECT 1 FROM pg_index index_record
                            JOIN pg_class table_record ON table_record.oid = index_record.indrelid
                            JOIN pg_attribute attribute ON attribute.attrelid = table_record.oid
                                AND attribute.attnum = index_record.indkey[0]
                            WHERE table_record.relname = ? AND attribute.attname = ?
                              AND index_record.indisvalid AND index_record.indpred IS NULL
                        ) THEN 1 ELSE 0 END
                    """.trimIndent(), table, column
                            ), "$table.$column"
                        )
                    }
                })
            }
        }
    }
}
