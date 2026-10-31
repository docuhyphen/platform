package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestResponseArchiveContractTest {
    @Test
    fun `an archived answer and a condition effect are append-only and refuse incoherent rows`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val transitionId = insertTransition(connection, runtime)
                val archiveId = insertArchive(connection, runtime)
                val effectId = insertEffect(connection, runtime, transitionId, "ARCHIVED", archiveId = archiveId)
                insertEffect(connection, runtime, transitionId, "REACTIVATED", state = "TRUE")

                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_archive WHERE id = ?",
                        archiveId
                    )
                )
                assertEquals(
                    "ARCHIVED",
                    queryString(
                        connection,
                        "SELECT effect FROM information_request_response_condition_effect WHERE id = ?",
                        effectId
                    ),
                )
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_response_archive SET narrative = 'Rewritten' WHERE id = ?",
                        archiveId
                    )
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(connection, "DELETE FROM information_request_response_archive WHERE id = ?", archiveId)
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_response_condition_effect SET rule_key = 'rewritten' WHERE id = ?",
                        effectId
                    )
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_response_condition_effect WHERE id = ?",
                        effectId
                    )
                }
                refusedBy(connection, "ck_request_response_archive_content") {
                    insertArchive(
                        connection,
                        runtime,
                        disposition = "NOT_ANSWERED",
                        narrative = null,
                        fieldValueJson = null
                    )
                }
                refusedBy(connection, "ck_request_response_archive_field") {
                    insertArchive(connection, runtime, fieldValueJson = "\"retained answer\"", withContract = false)
                }
                refusedBy(connection, "ck_request_response_archive_path") {
                    insertArchive(connection, runtime, occurrencePath = "$")
                }
                refusedBy(connection, "ck_request_response_condition_effect_archived") {
                    insertEffect(connection, runtime, transitionId, "ARCHIVED", archiveId = null)
                }
                refusedBy(connection, "ck_request_response_condition_effect_archived") {
                    insertEffect(connection, runtime, transitionId, "HIDDEN", archiveId = archiveId)
                }
                refusedBy(connection, "ck_request_response_condition_effect_reactivation") {
                    insertEffect(connection, runtime, transitionId, "REACTIVATED", state = "FALSE")
                }
                refusedBy(connection, "ck_request_response_condition_effect_reactivation") {
                    insertEffect(connection, runtime, transitionId, "HIDDEN", state = "TRUE")
                }
                refusedBy(connection, "ck_request_response_condition_effect_cleared") {
                    insertEffect(connection, runtime, transitionId, "CLEARED", policy = "RETAIN_SECURELY")
                }
                refusedBy(connection, "ck_request_response_condition_effect_kind") {
                    insertEffect(connection, runtime, transitionId, "RESTORED")
                }
            }
        }
    }

    @Test
    fun `an archived answer can consist of files alone and names each archived file version of its own Requirement once`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val transitionId = insertTransition(connection, runtime)
                val archiveId = insertArchive(
                    connection,
                    runtime,
                    disposition = "NOT_ANSWERED",
                    narrative = null,
                    evidenceCount = 1
                )
                val memberId = insertArchivedFile(connection, runtime, archiveId)
                insertEffect(connection, runtime, transitionId, "ARCHIVED", archiveId = archiveId)

                assertEquals(
                    "${runtime.evidenceVersionId}|1",
                    queryString(
                        connection,
                        "SELECT evidence_version_id::text || '|' || evidence_version_number FROM information_request_response_archive_evidence WHERE id = ?",
                        memberId,
                    ),
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT evidence_count FROM information_request_response_archive WHERE id = ?",
                        insertArchive(connection, runtime),
                    ),
                )
                refusedBy(connection, "ck_request_response_archive_content") {
                    insertArchive(
                        connection,
                        runtime,
                        disposition = "NOT_ANSWERED",
                        narrative = null,
                        evidenceCount = 0
                    )
                }
                refusedBy(connection, "ck_request_response_archive_evidence_count") {
                    insertArchive(connection, runtime, evidenceCount = -1)
                }
                refusedBy(connection, "ux_request_response_archive_evidence_artifact") {
                    insertArchivedFile(connection, runtime, archiveId)
                }
                refusedBy(connection, "an archived file is a version of a file of the archived Requirement") {
                    insertArchivedFile(
                        connection,
                        runtime,
                        insertArchive(connection, runtime, evidenceCount = 1),
                        versionNumber = 2
                    )
                }
                refusedBy(connection, "an archived file is a version of a file of the archived Requirement") {
                    val otherRequirement = insertArchive(
                        connection,
                        runtime,
                        evidenceCount = 1,
                        requirementId = runtime.attestationRequirementId,
                        responseId = insertAttestationResponse(connection, runtime),
                    )
                    insertArchivedFile(connection, runtime, otherRequirement)
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_response_archive_evidence SET evidence_version_number = 2 WHERE id = ?",
                        memberId
                    )
                }
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_response_archive_evidence WHERE id = ?",
                        memberId
                    )
                }
            }
        }
    }

    @Test
    fun `disposing a request removes its archived answers and condition effects with it`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val preservation = PreservationSqlFixture(connection, SubmissionRuntimeSqlFixture(connection))
                val runtime = preservation.runtime
                val transitionId = insertTransition(connection, runtime)
                val archiveId = insertArchive(connection, runtime, evidenceCount = 1)
                insertArchivedFile(connection, runtime, archiveId)
                insertEffect(connection, runtime, transitionId, "ARCHIVED", archiveId = archiveId)
                insertEffect(connection, runtime, transitionId, "REACTIVATED", state = "TRUE")
                execute(
                    connection,
                    "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                    runtime.requestId
                )
                val scheduleId = UUID.randomUUID()
                preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
                val claimId = UUID.randomUUID()
                preservation.insertClaim(claimId, scheduleId)
                preservation.insertScope(claimId, "INFORMATION_REQUEST", runtime.requestId.toString(), direct = true)
                val objectId = preservation.insertObject(claimId, retained = false)
                execute(
                    connection,
                    "UPDATE record_disposal_object SET deletion_outcome = 'ABSENT', deleted_at = now() WHERE id = ?",
                    objectId
                )
                preservation.advance(claimId)

                queryString(connection, "SELECT record_dispose_information_request(?)", claimId)

                assertEquals(
                    0,
                    queryInt(connection, "SELECT count(*) FROM information_request WHERE id = ?", runtime.requestId)
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_archive WHERE information_request_id = ?",
                        runtime.requestId
                    ),
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_condition_effect WHERE information_request_id = ?",
                        runtime.requestId,
                    ),
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_archive_evidence WHERE information_request_id = ?",
                        runtime.requestId,
                    ),
                )
            }
        }
    }

    @Test
    fun `upgrading a populated V162 schema adds both histories and leaves every recorded response as it was`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "162").migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val transitionId = insertTransition(connection, runtime, keyed = false)
                val responsesBefore = queryString(
                    connection,
                    "SELECT json_agg(response ORDER BY id)::text FROM information_request_response response",
                )
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_schema.tables WHERE table_name IN " +
                                "('information_request_response_archive', 'information_request_response_condition_effect')",
                    ),
                )

                submissionFlyway(postgres).migrate()

                assertEquals(
                    responsesBefore,
                    queryString(
                        connection,
                        "SELECT json_agg(response ORDER BY id)::text FROM information_request_response response"
                    ),
                )
                val archiveId = insertArchive(connection, runtime)
                insertEffect(connection, runtime, transitionId, "ARCHIVED", archiveId = archiveId)
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_archive WHERE response_id = ?",
                        runtime.documentResponseId
                    ),
                )
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT count(*) FROM information_request_response_condition_effect WHERE transition_id = ?",
                        transitionId
                    ),
                )
                refusedBy(connection, "information request history is append-only") {
                    execute(
                        connection,
                        "UPDATE information_request_response_archive SET disposition = 'WAIVED' WHERE id = ?",
                        archiveId
                    )
                }
            }
        }
    }

    private fun insertTransition(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        keyed: Boolean = true
    ): UUID {
        val id = UUID.randomUUID()
        val keyColumn = if (keyed) ", idempotency_key" else ""
        val keyValue = if (keyed) ", 'response-save'" else ""
        execute(
            connection,
            """
            INSERT INTO information_request_transition
                (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at$keyColumn)
            VALUES (?, ?, 1, 'ISSUED', 'IN_PROGRESS', 'SAVE_RESPONSE', 'USER', ?, now()$keyValue)
            """.trimIndent(),
            id,
            runtime.requestId,
            runtime.contributorUserId,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertArchive(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        disposition: String = "PROVIDED",
        narrative: String? = "Previously recorded answer",
        fieldValueJson: String? = null,
        withContract: Boolean = fieldValueJson != null,
        occurrencePath: String = "root",
        evidenceCount: Int = 0,
        requirementId: UUID = runtime.documentRequirementId,
        responseId: UUID = runtime.documentResponseId,
    ): UUID {
        val id = UUID.randomUUID()
        val contractId = if (withContract) insertFieldContract(connection, runtime) else null
        execute(
            connection,
            """
            INSERT INTO information_request_response_archive
                (id, information_request_id, information_request_requirement_id, response_id, occurrence_path,
                 condition_rule_key, response_revision, disposition, narrative, field_contract_id, field_value_type,
                 field_value_json, field_value_set_etag, evidence_count, archived_by_principal_kind,
                 archived_by_principal_id, archived_at)
            VALUES (?, ?, ?, ?, ?, 'when-detail-applies', 2, ?, ?, ?, ?, ?, ?, ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            requirementId,
            responseId,
            occurrencePath,
            disposition,
            narrative,
            contractId,
            if (fieldValueJson != null) "SHORT_TEXT" else null,
            fieldValueJson,
            if (fieldValueJson != null) "\"field-values-1\"" else null,
            evidenceCount,
            runtime.contributorUserId,
        )
        return id
    }

    private fun insertAttestationResponse(connection: Connection, runtime: SubmissionRuntimeSqlFixture): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_response
                (id, information_request_id, information_request_requirement_id, requirement_revision_id,
                 occurrence_path, disposition, response_revision, recorded_by_principal_kind,
                 recorded_by_principal_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'root', 'PROVIDED', 2, 'USER', ?, now(), now())
            """.trimIndent(),
            id,
            runtime.requestId,
            runtime.attestationRequirementId,
            runtime.attestationRevisionId,
            runtime.contributorUserId,
        )
        return id
    }

    private fun insertArchivedFile(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        archiveId: UUID,
        versionNumber: Int = 1,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_response_archive_evidence
                (id, archive_id, information_request_id, evidence_artifact_id, evidence_version_id, evidence_version_number)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            archiveId,
            runtime.requestId,
            runtime.artifactId,
            runtime.evidenceVersionId,
            versionNumber,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertEffect(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        transitionId: UUID,
        effect: String,
        archiveId: UUID? = null,
        state: String = "FALSE",
        policy: String = "ARCHIVE_OUTSIDE_ACTIVE_RESPONSE",
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_response_condition_effect
                (id, information_request_id, information_request_requirement_id, response_id, transition_id,
                 occurrence_path, rule_key, expression_version, evaluation_state, hidden_data_policy, effect,
                 archive_id, response_revision, recorded_by_principal_kind, recorded_by_principal_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, 'root', 'when-detail-applies', 1, ?, ?, ?, ?, 2, 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            runtime.documentRequirementId,
            runtime.documentResponseId,
            transitionId,
            state,
            policy,
            effect,
            archiveId,
            runtime.contributorUserId,
        )
        return id
    }

    private fun insertFieldContract(connection: Connection, runtime: SubmissionRuntimeSqlFixture): UUID {
        val definitionId = UUID.randomUUID()
        val contractId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status, created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'PUBLISHED', now(), now())
            """.trimIndent(),
            definitionId,
            runtime.template.organizationId,
            "archived-note-${definitionId.toString().take(8)}",
        )
        execute(
            connection,
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label, created_at)
            VALUES (?, ?, 1, 'SHORT_TEXT', 'Archived note', now())
            """.trimIndent(),
            contractId,
            definitionId,
        )
        return contractId
    }
}
