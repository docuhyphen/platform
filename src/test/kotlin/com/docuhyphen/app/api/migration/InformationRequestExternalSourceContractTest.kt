package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestExternalSourceContractTest
{
    @Test
    fun `imported values keep their source, confidence, verification time, expiry, and provenance and stay append-only`()
    {
        withExternalSources { connection, fixture ->
            val manual = fixture.insertImportedValue(sourceKind = "MANUAL")
            refusedBy(connection, "ck_information_request_imported_value_confidence") {
                fixture.insertImportedValue(sourceKind = "MANUAL", confidence = "VERIFIED", verified = false)
            }
            refusedBy(connection, "ck_information_request_imported_value_type") {
                fixture.insertImportedValue(sourceKind = "MANUAL", valueType = "FREE_FORM")
            }
            refusedBy(connection, "ck_information_request_imported_value_source") {
                fixture.insertImportedValue(sourceKind = "CONNECTOR")
            }
            refusedBy(connection, "a connector value comes from a completed exchange of its own request") {
                fixture.insertImportedValue(sourceKind = "CONNECTOR", exchangeId = fixture.insertExchange("PENDING"))
            }
            val completed = fixture.insertExchange("COMPLETED")
            fixture.insertImportedValue(sourceKind = "CONNECTOR", exchangeId = completed, confidence = "VERIFIED", verified = true)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "UPDATE information_request_imported_value SET canonical_value = '\"changed\"' WHERE id = ?", manual)
            }
            refusedBy(connection, "a finished connector exchange is immutable") {
                execute(connection, "UPDATE information_request_connector_exchange SET state = 'PENDING', completed_at = NULL WHERE id = ?", completed)
            }
            val pending = fixture.insertExchange("PENDING")
            refusedBy(connection, "a connector exchange keeps its identity and only moves forward") {
                execute(connection, "UPDATE information_request_connector_exchange SET state = 'REQUESTED' WHERE id = ?", pending)
            }
            refusedBy(connection, "a connector exchange keeps its identity and only moves forward") {
                execute(connection, "UPDATE information_request_connector_exchange SET lookup_reference = 'changed' WHERE id = ?", pending)
            }
            execute(connection, "UPDATE information_request_connector_exchange SET attempt_count = 2, external_reference = 'external-1' WHERE id = ?", pending)

            fixture.insertDecision(manual, "ACCEPTED")
            refusedBy(connection, "ux_information_request_imported_value_decision") { fixture.insertDecision(manual, "REJECTED") }
            refusedBy(connection, "ck_information_request_imported_value_discrepancy_differs") {
                fixture.insertDiscrepancy(manual, imported = "\"same\"", response = "\"same\"")
            }
            refusedBy(connection, "response_canonical_value") {
                fixture.insertDiscrepancy(manual, imported = "\"imported\"", response = null)
            }
            val discrepancy = fixture.insertDiscrepancy(manual, imported = "\"imported\"", response = "\"answered\"")
            fixture.insertResolution(discrepancy)
            refusedBy(connection, "information request history is append-only") {
                execute(connection, "DELETE FROM information_request_imported_value_discrepancy WHERE id = ?", discrepancy)
            }

            fixture.insertGeneratedOutput(fixture.packageId)
            refusedBy(connection, "a generated output names a package of its own request") {
                fixture.insertGeneratedOutput(fixture.otherPackageId)
            }
            refusedBy(connection, "ck_information_request_generated_output_hash") {
                fixture.insertGeneratedOutput(null, hash = "not-a-hash")
            }
            execute(
                connection,
                """
                INSERT INTO information_request_transition
                    (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at)
                VALUES (?, ?, 1, 'ISSUED', 'ISSUED', 'RECORD_EXTERNAL_VALUE', 'USER', ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.runtime.requestId,
                fixture.runtime.template.userId,
            )
        }
    }

    @Test
    fun `exchanges, decisions, discrepancies, and resolutions stay within their own request and Requirement`()
    {
        withExternalSources { connection, fixture ->
            assertEquals(
                "NO",
                queryString(
                    connection,
                    """
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'information_request_connector_exchange' AND column_name = ?
                    """.trimIndent(),
                    "information_request_requirement_id",
                ),
            )
            refusedBy(connection, "a connector exchange names a Requirement of its own request") {
                fixture.insertExchange("REQUESTED", requirementId = null)
            }
            refusedBy(connection, "a connector exchange names a Requirement of its own request") {
                fixture.insertExchange("REQUESTED", requestId = fixture.otherRequestId)
            }
            refusedBy(connection, "ck_information_request_connector_exchange_lookup") {
                fixture.insertExchange("REQUESTED", lookupReference = " ")
            }
            fixture.insertExchange("REQUESTED", lookupReference = "record-1")

            val manual = fixture.insertImportedValue(sourceKind = "MANUAL")
            refusedBy(connection, "a decision belongs to the request of its imported value") {
                fixture.insertDecision(manual, "ACCEPTED", requestId = fixture.otherRequestId)
            }
            refusedBy(connection, "a discrepancy compares an imported value with a response of its own Requirement") {
                fixture.insertDiscrepancy(manual, imported = "\"imported\"", response = "\"answered\"", requestId = fixture.otherRequestId)
            }
            val attestationValue = fixture.insertImportedValue(sourceKind = "MANUAL", requirementId = fixture.runtime.attestationRequirementId)
            refusedBy(connection, "a discrepancy compares an imported value with a response of its own Requirement") {
                fixture.insertDiscrepancy(attestationValue, imported = "\"imported\"", response = "\"answered\"")
            }
            val discrepancy = fixture.insertDiscrepancy(manual, imported = "\"imported\"", response = "\"answered\"")
            refusedBy(connection, "a resolution belongs to the request of its discrepancy") {
                fixture.insertResolution(discrepancy, requestId = fixture.otherRequestId)
            }
            fixture.insertResolution(discrepancy)
        }
    }

    @Test
    fun `disposal removes and counts every external source record of the request`()
    {
        withExternalSources { connection, fixture ->
            val manual = fixture.insertImportedValue(sourceKind = "MANUAL")
            fixture.insertDecision(manual, "REJECTED")
            fixture.insertResolution(fixture.insertDiscrepancy(manual, imported = "\"imported\"", response = "\"answered\""))
            fixture.insertImportedValue(sourceKind = "CONNECTOR", exchangeId = fixture.insertExchange("COMPLETED"), confidence = "MATCHED", verified = true)
            fixture.insertGeneratedOutput(fixture.packageId)

            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.runtime.requestId)
            val preservation = PreservationSqlFixture(connection, fixture.runtime)
            val scheduleId = UUID.randomUUID()
            preservation.insertSchedule(scheduleId, 1, minimumDays = 0, disposalDays = 0)
            val claimId = UUID.randomUUID()
            preservation.insertClaim(claimId, scheduleId)
            preservation.insertScope(claimId, "INFORMATION_REQUEST", fixture.runtime.requestId.toString(), direct = true)
            val objectId = preservation.insertObject(claimId, retained = false)
            execute(connection, "UPDATE record_disposal_object SET deletion_outcome = 'ABSENT', deleted_at = now() WHERE id = ?", objectId)
            preservation.advance(claimId)
            val removed = requireNotNull(queryString(connection, "SELECT record_dispose_information_request(?)", claimId))
            mapOf(
                "information_request_imported_value_discrepancy_resolution" to 1,
                "information_request_imported_value_discrepancy" to 1,
                "information_request_imported_value_decision" to 1,
                "information_request_imported_value" to 2,
                "information_request_connector_exchange" to 1,
                "information_request_generated_output" to 1,
            ).forEach { (table, count) -> assertTrue(removed.contains("\"$table\": $count"), "$table: $removed") }
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM information_request_imported_value WHERE information_request_id = ?", fixture.runtime.requestId))
        }
    }

    private fun withExternalSources(block: (Connection, ExternalSourceSqlFixture) -> Unit)
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                block(connection, ExternalSourceSqlFixture(connection, runtime))
            }
        }
    }
}

internal class ExternalSourceSqlFixture(
    private val connection: Connection,
    val runtime: SubmissionRuntimeSqlFixture,
)
{
    val packageId: UUID = UUID.randomUUID()
    val otherPackageId: UUID = UUID.randomUUID()
    val otherRequestId: UUID = UUID.randomUUID()

    init
    {
        runtime.insertPackage(packageId, 1)
        runtime.insertSiblingRequest(otherRequestId, UUID.randomUUID().also { runtime.insertSubject(it) })
        execute(
            connection,
            """
            INSERT INTO information_request_submission_package
                (id, information_request_id, package_number, template_version_id, content_hash_sha256, manifest_hash_sha256,
                 review_required, completes_request, submitted_by_principal_kind, submitted_by_principal_id, submitted_at)
            SELECT ?, ?, 1, template_version_id, content_hash_sha256, manifest_hash_sha256, review_required, completes_request,
                   submitted_by_principal_kind, submitted_by_principal_id, submitted_at
            FROM information_request_submission_package WHERE id = ?
            """.trimIndent(),
            otherPackageId,
            otherRequestId,
            packageId,
        )
    }

    fun insertExchange(
        state: String,
        requirementId: UUID? = runtime.documentRequirementId,
        requestId: UUID = runtime.requestId,
        lookupReference: String? = null,
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_connector_exchange
                (id, information_request_id, information_request_requirement_id, connector_key, connector_kind, contract_version,
                 state, lookup_reference, attempt_count, requested_by_principal_kind, requested_by_principal_id, requested_at,
                 completed_at, failure_code)
            VALUES (?, ?, ?, 'record-verification', 'EXTERNAL_VERIFICATION', 1, ?, ?, 1, 'USER', ?, now(),
                    CASE WHEN ? IN ('COMPLETED', 'FAILED') THEN now() END, CASE WHEN ? = 'FAILED' THEN 'connector.failed' END)
            """.trimIndent(),
            id,
            requestId,
            requirementId,
            state,
            lookupReference,
            runtime.template.userId,
            state,
            state,
        )
        return id
    }

    @Suppress("LongParameterList")
    fun insertImportedValue(
        sourceKind: String,
        exchangeId: UUID? = null,
        confidence: String = "ASSERTED",
        verified: Boolean = false,
        valueType: String = "SHORT_TEXT",
        requirementId: UUID = runtime.documentRequirementId,
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value
                (id, information_request_id, information_request_requirement_id, source_kind, connector_exchange_id, source_reference,
                 result_key, value_type, canonical_value, confidence, verified_at, expires_at, provenance_reference,
                 recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, ?, ?, 'registry-extract', 'recorded-status', ?, '"active"', ?,
                    CASE WHEN ? THEN now() END, CASE WHEN ? THEN now() + INTERVAL '30 days' END, 'extract-2026-09', 'USER', ?, now())
            """.trimIndent(),
            id,
            runtime.requestId,
            requirementId,
            sourceKind,
            exchangeId,
            valueType,
            confidence,
            verified,
            verified,
            runtime.template.userId,
        )
        return id
    }

    fun insertDecision(importedValueId: UUID, decision: String, requestId: UUID = runtime.requestId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value_decision
                (id, imported_value_id, information_request_id, decision, reason_code, decided_by_principal_kind, decided_by_principal_id, decided_at)
            VALUES (?, ?, ?, ?, 'reviewed', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            importedValueId,
            requestId,
            decision,
            runtime.template.userId,
        )
    }

    fun insertDiscrepancy(importedValueId: UUID, imported: String, response: String?, requestId: UUID = runtime.requestId): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value_discrepancy
                (id, imported_value_id, information_request_id, response_id, response_revision, imported_canonical_value,
                 response_canonical_value, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, ?, 2, ?, ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            importedValueId,
            requestId,
            runtime.documentResponseId,
            imported,
            response,
            runtime.template.userId,
        )
        return id
    }

    fun insertResolution(discrepancyId: UUID, requestId: UUID = runtime.requestId)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value_discrepancy_resolution
                (id, discrepancy_id, information_request_id, resolution, reason_code, resolved_by_principal_kind, resolved_by_principal_id, resolved_at)
            VALUES (?, ?, ?, 'RESPONSE_STANDS', 'answer-confirmed', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            discrepancyId,
            requestId,
            runtime.template.userId,
        )
    }

    fun insertGeneratedOutput(packageId: UUID?, hash: String? = "a".repeat(64))
    {
        execute(
            connection,
            """
            INSERT INTO information_request_generated_output
                (id, information_request_id, package_id, output_key, external_reference, content_hash_sha256, media_type,
                 produced_by_source, produced_at, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, ?, 'summary-output', 'external://outputs/1', ?, 'application/pdf', 'output-service', now(), 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtime.requestId,
            packageId,
            hash,
            runtime.template.userId,
        )
    }
}
