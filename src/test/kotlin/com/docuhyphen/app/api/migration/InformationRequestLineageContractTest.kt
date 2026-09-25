package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestLineageContractTest
{
    @Test
    fun `a follow-up preserves its source package and records a decision per matching occurrence`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val packageId = UUID.randomUUID()
                val itemId = UUID.randomUUID()
                fixture.insertPackage(packageId, 1)
                fixture.insertItem(itemId, packageId, fixture.documentRequirementId, fixture.documentRevisionId,
                    fixture.documentBindingId, "DOCUMENT", fixture.documentResponseId)
                val successor = insertSuccessor(connection, fixture)
                val lineageId = UUID.randomUUID()

                val otherRequest = SubmissionRuntimeSqlFixture(connection)
                refusedBy(connection, "belongs to the Exchange and owner") {
                    insertLineage(connection, UUID.randomUUID(), otherRequest.requestId, fixture.requestId, null, "SUPPLEMENT")
                }
                refusedBy(connection, "information_request_lineage_package_fkey") {
                    insertLineage(connection, UUID.randomUUID(), successor.requestId, fixture.requestId, UUID.randomUUID(), "SUPPLEMENT")
                }
                refusedBy(connection, "ck_information_request_lineage_recurrence") {
                    insertLineage(connection, UUID.randomUUID(), successor.requestId, fixture.requestId, packageId, "RECURRENCE")
                }
                insertLineage(connection, lineageId, successor.requestId, fixture.requestId, packageId, "SUPPLEMENT")
                refusedBy(connection, "ux_information_request_lineage_successor") {
                    insertLineage(connection, UUID.randomUUID(), successor.requestId, fixture.requestId, packageId, "SUPPLEMENT")
                }

                refusedBy(connection, "ck_information_request_carry_forward_reason") {
                    insertCarryForward(connection, lineageId, successor, packageId, itemId, "INVALIDATED", null)
                }
                refusedBy(connection, "names an item of the package") {
                    insertCarryForward(connection, lineageId, successor, packageId, UUID.randomUUID(), "OFFERED", null)
                }
                insertCarryForward(connection, lineageId, successor, packageId, itemId, "INVALIDATED", "EVIDENCE_REQUIRES_FRESH_COLLECTION")
                assertEquals(
                    "EVIDENCE_REQUIRES_FRESH_COLLECTION",
                    queryString(connection, "SELECT reason_code FROM information_request_carry_forward WHERE lineage_id = ?", lineageId),
                )

                listOf(
                    "UPDATE information_request_lineage SET reason_code = 'changed' WHERE id = ?",
                    "DELETE FROM information_request_carry_forward WHERE lineage_id = ?",
                ).forEach { statement -> refusedBy(connection, "append-only") { execute(connection, statement, lineageId) } }
            }
        }
    }

    @Test
    fun `a recurrence numbers each occurrence once and follows a request of its own series`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val recurrenceId = UUID.randomUUID()
                refusedBy(connection, "ck_information_request_recurrence_count") {
                    insertRecurrence(connection, UUID.randomUUID(), fixture, intervalCount = 0)
                }
                insertRecurrence(connection, recurrenceId, fixture)
                refusedBy(connection, "ux_information_request_recurrence_origin") {
                    insertRecurrence(connection, UUID.randomUUID(), fixture)
                }

                val first = insertSuccessor(connection, fixture)
                val second = insertSuccessor(connection, fixture)
                insertLineage(connection, UUID.randomUUID(), first.requestId, fixture.requestId, null, "RECURRENCE", recurrenceId, 1)
                val outsider = insertSuccessor(connection, fixture)
                refusedBy(connection, "follows a request of its own recurrence") {
                    insertLineage(connection, UUID.randomUUID(), second.requestId, outsider.requestId, null, "RECURRENCE", recurrenceId, 2)
                }
                refusedBy(connection, "ux_information_request_lineage_recurrence") {
                    insertLineage(connection, UUID.randomUUID(), second.requestId, first.requestId, null, "RECURRENCE", recurrenceId, 1)
                }
                insertLineage(connection, UUID.randomUUID(), second.requestId, first.requestId, null, "RECURRENCE", recurrenceId, 2)

                refusedBy(connection, "ck_information_request_refresh_rule_lead") {
                    insertRefreshRule(connection, UUID.randomUUID(), fixture.requestId, -1)
                }
                val ruleId = UUID.randomUUID()
                insertRefreshRule(connection, ruleId, fixture.requestId, 30)
                val refreshed = insertSuccessor(connection, fixture)
                refusedBy(connection, "information_request_lineage_refresh_rule_fkey") {
                    insertLineage(connection, UUID.randomUUID(), refreshed.requestId, first.requestId, null, "REFRESH", refreshRuleId = ruleId)
                }
                insertLineage(connection, UUID.randomUUID(), refreshed.requestId, fixture.requestId, null, "REFRESH", refreshRuleId = ruleId)
                refusedBy(connection, "append-only") {
                    execute(connection, "UPDATE information_request_recurrence SET interval_count = 2 WHERE id = ?", recurrenceId)
                }
            }
        }
    }

    private data class Successor(val requestId: UUID, val requirementId: UUID)

    private fun insertSuccessor(connection: Connection, fixture: SubmissionRuntimeSqlFixture): Successor
    {
        val successor = Successor(UUID.randomUUID(), UUID.randomUUID())
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
            """.trimIndent(),
            successor.requestId,
            fixture.exchangeId,
            fixture.template.versionId,
            fixture.template.organizationId,
            fixture.template.now,
            fixture.template.now,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path)
            VALUES (?, ?, ?, ?, ?, 'root')
            """.trimIndent(),
            successor.requirementId,
            successor.requestId,
            fixture.template.versionId,
            fixture.documentTemplateRequirementId,
            fixture.documentBindingId,
        )
        return successor
    }

    @Suppress("LongParameterList")
    private fun insertLineage(
        connection: Connection,
        id: UUID,
        successorRequestId: UUID,
        sourceRequestId: UUID,
        sourcePackageId: UUID?,
        kind: String,
        recurrenceId: UUID? = null,
        recurrenceSequence: Int? = null,
        refreshRuleId: UUID? = null,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_lineage
                (id, successor_request_id, source_request_id, source_package_id, lineage_kind, recurrence_id,
                 recurrence_sequence, refresh_rule_id, created_by_principal_kind, created_by_principal_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'USER', ?)
            """.trimIndent(),
            id,
            successorRequestId,
            sourceRequestId,
            sourcePackageId,
            kind,
            recurrenceId,
            recurrenceSequence,
            refreshRuleId,
            UUID.randomUUID(),
        )
    }

    @Suppress("LongParameterList")
    private fun insertCarryForward(
        connection: Connection,
        lineageId: UUID,
        successor: Successor,
        packageId: UUID,
        itemId: UUID,
        decision: String,
        reason: String?,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_carry_forward
                (id, lineage_id, information_request_id, information_request_requirement_id, source_package_id,
                 source_item_id, decision, reason_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            lineageId,
            successor.requestId,
            successor.requirementId,
            packageId,
            itemId,
            decision,
            reason,
        )
    }

    private fun insertRecurrence(connection: Connection, id: UUID, fixture: SubmissionRuntimeSqlFixture, intervalCount: Int = 1)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_recurrence
                (id, origin_request_id, interval_unit, interval_count, first_due_at, created_by_principal_kind,
                 created_by_principal_id)
            VALUES (?, ?, 'MONTH', ?, ?, 'USER', ?)
            """.trimIndent(),
            id,
            fixture.requestId,
            intervalCount,
            fixture.template.now,
            fixture.template.userId,
        )
    }

    private fun insertRefreshRule(connection: Connection, id: UUID, requestId: UUID, leadDays: Int)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_refresh_rule
                (id, information_request_id, requirement_key, lead_days, created_by_principal_kind,
                 created_by_principal_id)
            VALUES (?, ?, 'supporting-record', ?, 'USER', ?)
            """.trimIndent(),
            id,
            requestId,
            leadDays,
            UUID.randomUUID(),
        )
    }
}
