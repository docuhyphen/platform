package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestLifecycleRecordsContractTest {
    @Test
    fun `an Exchange ending intent names one Workflow instance and the principal that asked to end`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val instanceId = insertWorkflowInstance(connection, runtime.exchangeId)

                insertEndingIntent(connection, runtime.exchangeId, instanceId, "USER")
                refusedBy(connection, "ux_exchange_ending_intent_instance") {
                    insertEndingIntent(connection, runtime.exchangeId, instanceId, "USER")
                }
                refusedBy(connection, "ck_exchange_ending_intent_principal") {
                    insertEndingIntent(
                        connection,
                        runtime.exchangeId,
                        insertWorkflowInstance(connection, runtime.exchangeId),
                        "ROBOT"
                    )
                }
                refusedBy(connection, "exchange_ending_intent_instance_fkey") {
                    insertEndingIntent(connection, runtime.exchangeId, UUID.randomUUID(), "USER")
                }
                assertEquals(
                    1,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM exchange_ending_intent WHERE exchange_id = ?",
                        runtime.exchangeId
                    ),
                )
            }
        }
    }

    @Test
    fun `a reconciliation run keeps each outcome against its own request, refuses change, and is removed and counted on disposal`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val external = ExternalSourceSqlFixture(connection, SubmissionRuntimeSqlFixture(connection))
                val runtime = external.runtime
                val matching = external.insertImportedValue(sourceKind = "MANUAL")
                val differing = external.insertImportedValue(sourceKind = "MANUAL")
                val discrepancy =
                    external.insertDiscrepancy(differing, imported = "\"imported\"", response = "\"answered\"")
                val runId = insertRun(connection, runtime.requestId, runtime.template.userId)

                insertRunItem(
                    connection,
                    runId,
                    runtime.requestId,
                    1,
                    matching,
                    runtime.documentRequirementId,
                    "MATCHES",
                    null,
                    null
                )
                insertRunItem(
                    connection,
                    runId,
                    runtime.requestId,
                    2,
                    differing,
                    runtime.documentRequirementId,
                    "DIFFERS",
                    "\"answered\"",
                    discrepancy
                )
                refusedBy(connection, "ck_information_request_reconciliation_item_difference") {
                    insertRunItem(
                        connection,
                        runId,
                        runtime.requestId,
                        3,
                        differing,
                        runtime.documentRequirementId,
                        "DIFFERS",
                        null,
                        null
                    )
                }
                refusedBy(connection, "ck_information_request_reconciliation_item_outcome") {
                    insertRunItem(
                        connection,
                        runId,
                        runtime.requestId,
                        3,
                        matching,
                        runtime.documentRequirementId,
                        "GUESSED",
                        null,
                        null
                    )
                }
                insertRunItem(
                    connection,
                    runId,
                    runtime.requestId,
                    3,
                    matching,
                    runtime.documentRequirementId,
                    "FIELD_CHANGED",
                    null,
                    null
                )
                refusedBy(connection, "ck_information_request_reconciliation_item_difference") {
                    insertRunItem(
                        connection,
                        runId,
                        runtime.requestId,
                        4,
                        differing,
                        runtime.documentRequirementId,
                        "FIELD_CHANGED",
                        "\"answered\"",
                        discrepancy
                    )
                }
                refusedBy(connection, "ux_information_request_reconciliation_item_number") {
                    insertRunItem(
                        connection,
                        runId,
                        runtime.requestId,
                        1,
                        matching,
                        runtime.documentRequirementId,
                        "MATCHES",
                        null,
                        null
                    )
                }
                val foreignRun = insertRun(connection, external.otherRequestId, runtime.template.userId)
                refusedBy(connection, "a reconciliation outcome names an imported value of its own request") {
                    insertRunItem(
                        connection,
                        foreignRun,
                        external.otherRequestId,
                        1,
                        matching,
                        runtime.documentRequirementId,
                        "MATCHES",
                        null,
                        null
                    )
                }
                listOf(
                    "UPDATE information_request_imported_value_reconciliation_item SET outcome = 'EXPIRED' WHERE reconciliation_id = ?",
                    "DELETE FROM information_request_imported_value_reconciliation_item WHERE reconciliation_id = ?",
                    "UPDATE information_request_imported_value_reconciliation SET recorded_at = now() WHERE id = ?",
                ).forEach { statement ->
                    refusedBy(connection, "append-only") {
                        execute(
                            connection,
                            statement,
                            runId
                        )
                    }
                }

                val removed =
                    dispose(connection, runtime.requestId, runtime.template.organizationId, runtime.template.userId)
                assertTrue(removed.contains("\"information_request_imported_value_reconciliation_item\": 3"), removed)
                assertTrue(removed.contains("\"information_request_imported_value_reconciliation\": 1"), removed)
                assertEquals(
                    0,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_imported_value WHERE information_request_id = ?",
                        runtime.requestId
                    )
                )
            }
        }
    }

    @Test
    fun `a follow-up lists every package it preserves and carries forward items of any of them only`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val source = SubmissionRuntimeSqlFixture(connection, staged = true)
                val recordPackage = UUID.randomUUID()
                val confirmationPackage = UUID.randomUUID()
                val unlistedPackage = UUID.randomUUID()
                val recordItem = UUID.randomUUID()
                val confirmationItem = UUID.randomUUID()
                val unlistedItem = UUID.randomUUID()
                source.insertPackage(recordPackage, 1, stageKey = "record-stage")
                source.insertPackage(confirmationPackage, 2, stageKey = "confirmation-stage")
                source.insertPackage(unlistedPackage, 3, stageKey = "record-stage", previousPackageId = recordPackage)
                source.insertItem(
                    recordItem,
                    recordPackage,
                    source.documentRequirementId,
                    source.documentRevisionId,
                    source.documentBindingId,
                    "DOCUMENT"
                )
                source.insertItem(
                    confirmationItem,
                    confirmationPackage,
                    source.attestationRequirementId,
                    source.attestationRevisionId,
                    source.attestationBindingId,
                    "RESPONSE_ATTESTATION",
                )
                source.insertItem(
                    unlistedItem,
                    unlistedPackage,
                    source.documentRequirementId,
                    source.documentRevisionId,
                    source.documentBindingId,
                    "DOCUMENT"
                )
                val successorId = insertDraft(connection, source)
                val documentRequirement = insertSuccessorRequirement(
                    connection,
                    source,
                    successorId,
                    source.documentTemplateRequirementId,
                    source.documentBindingId
                )
                val attestationRequirement = insertSuccessorRequirement(
                    connection,
                    source,
                    successorId,
                    source.attestationTemplateRequirementId,
                    source.attestationBindingId,
                )
                val lineageId = UUID.randomUUID()
                insertLineage(
                    connection,
                    lineageId,
                    successorId,
                    source.requestId,
                    confirmationPackage,
                    source.template.userId
                )

                insertSourcePackage(connection, lineageId, successorId, source.requestId, recordPackage)
                insertSourcePackage(connection, lineageId, successorId, source.requestId, confirmationPackage)
                refusedBy(connection, "information_request_lineage_source_package_unique") {
                    insertSourcePackage(connection, lineageId, successorId, source.requestId, recordPackage)
                }
                val unrelated = SubmissionRuntimeSqlFixture(connection)
                val unrelatedPackage = UUID.randomUUID()
                unrelated.insertPackage(unrelatedPackage, 1)
                refusedBy(connection, "information_request_lineage_source_package_package_fkey") {
                    insertSourcePackage(connection, lineageId, successorId, source.requestId, unrelatedPackage)
                }
                refusedBy(connection, "a preserved package belongs to the request its follow-up follows") {
                    insertSourcePackage(connection, lineageId, successorId, unrelated.requestId, unrelatedPackage)
                }

                insertCarryForward(
                    connection,
                    lineageId,
                    successorId,
                    documentRequirement,
                    recordPackage,
                    recordItem,
                    "INVALIDATED",
                    "EVIDENCE_REQUIRES_FRESH_COLLECTION"
                )
                insertCarryForward(
                    connection, lineageId, successorId, attestationRequirement, confirmationPackage, confirmationItem,
                    "INVALIDATED", "ATTESTATION_REQUIRES_FRESH_ASSENT",
                )
                refusedBy(connection, "names an item of the packages its follow-up request preserves") {
                    insertCarryForward(
                        connection,
                        lineageId,
                        successorId,
                        documentRequirement,
                        unlistedPackage,
                        unlistedItem,
                        "OFFERED",
                        null
                    )
                }
                refusedBy(connection, "append-only") {
                    execute(
                        connection,
                        "DELETE FROM information_request_lineage_source_package WHERE lineage_id = ?",
                        lineageId
                    )
                }

                val removed = dispose(connection, successorId, source.template.organizationId, source.template.userId)
                assertTrue(removed.contains("\"information_request_lineage_source_package\": 2"), removed)
                assertTrue(removed.contains("\"information_request_carry_forward\": 2"), removed)
            }
        }
    }

    @Test
    fun `an execution grant states its policy, source, features, and continuation actions and keeps them frozen`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val runtime = SubmissionRuntimeSqlFixture(connection)
                val trialGrant = insertTrialGrant(connection, runtime.template.organizationId)
                refusedBy(connection, "ck_request_execution_grant_entitlement_source") {
                    insertGrant(connection, runtime, source = "GIFTED")
                }
                refusedBy(connection, "ck_request_execution_grant_features") {
                    insertGrant(connection, runtime, features = "ARRAY['BUSINESS_FIELDS_AND_SCHEMAS']")
                }
                refusedBy(connection, "ck_request_execution_grant_features") {
                    insertGrant(connection, runtime, features = "ARRAY['INFORMATION_REQUESTS', 'WORKFLOW_AUTOMATION']")
                }
                refusedBy(connection, "ck_request_execution_grant_continuation_actions") {
                    insertGrant(connection, runtime, actions = "ARRAY['RESPOND', 'EXPAND']")
                }
                refusedBy(connection, "ck_request_execution_grant_trial_source") {
                    insertGrant(connection, runtime, source = "PAID", trialGrantId = trialGrant)
                }
                refusedBy(connection, "ck_request_execution_grant_trial_reference") {
                    insertGrant(connection, runtime, source = "TRIAL")
                }
                refusedBy(connection, "ck_request_execution_grant_policy_version") {
                    insertGrant(connection, runtime, policyVersion = " ")
                }
                val grantId = insertGrant(connection, runtime, source = "TRIAL", trialGrantId = trialGrant)
                refusedBy(connection, "ck_request_execution_grant_revocation") {
                    execute(
                        connection,
                        "UPDATE request_execution_grant SET revoked_reason = 'record-review' WHERE id = ?",
                        grantId
                    )
                }
                listOf(
                    "UPDATE request_execution_grant SET policy_version = '2' WHERE id = ?",
                    "UPDATE request_execution_grant SET entitlement_source = 'PAID', trial_grant_id = NULL WHERE id = ?",
                    "UPDATE request_execution_grant SET granted_features = ARRAY['INFORMATION_REQUESTS', 'BUSINESS_FIELDS_AND_SCHEMAS'] WHERE id = ?",
                    "UPDATE request_execution_grant SET continuation_actions = ARRAY['RESPOND'] WHERE id = ?",
                ).forEach { statement ->
                    refusedBy(connection, "an execution grant keeps the position it was issued under") {
                        execute(
                            connection,
                            statement,
                            grantId
                        )
                    }
                }
                execute(
                    connection,
                    "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'record-review' WHERE id = ?",
                    grantId
                )
            }
        }
    }

    @Test
    fun `an upgraded database states the provenance of each existing grant and lists the package each existing follow-up preserves`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, target = "162").migrate()
            val paid = UUID.randomUUID()
            val trial = UUID.randomUUID()
            val unenforced = UUID.randomUUID()
            val personal = UUID.randomUUID()
            val lineageId = UUID.randomUUID()
            val packageId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                val organizationRequest = SubmissionRuntimeSqlFixture(connection)
                insertLegacyGrant(
                    connection,
                    paid,
                    organizationRequest.requestId,
                    organizationRequest.template.organizationId,
                    null,
                    "BUSINESS",
                    "ACTIVE",
                    "ENFORCE",
                    false
                )
                val trialRequest = SubmissionRuntimeSqlFixture(connection)
                insertLegacyGrant(
                    connection,
                    trial,
                    trialRequest.requestId,
                    trialRequest.template.organizationId,
                    null,
                    "BUSINESS",
                    "TRIALING",
                    "ENFORCE",
                    true
                )
                val reported = SubmissionRuntimeSqlFixture(connection)
                insertLegacyGrant(
                    connection,
                    unenforced,
                    reported.requestId,
                    reported.template.organizationId,
                    null,
                    "BUSINESS",
                    "ACTIVE",
                    "REPORT_ONLY",
                    false
                )
                val personalOwner = SubmissionRuntimeSqlFixture(connection)
                insertLegacyGrant(
                    connection,
                    personal,
                    personalOwner.requestId,
                    null,
                    personalOwner.template.userId,
                    "PERSONAL",
                    "ACTIVE",
                    "ENFORCE",
                    false
                )

                organizationRequest.insertPackage(packageId, 1)
                val successorId = insertDraft(connection, organizationRequest)
                insertLineage(
                    connection,
                    lineageId,
                    successorId,
                    organizationRequest.requestId,
                    packageId,
                    organizationRequest.template.userId
                )
            }

            submissionFlyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("1|PAID|INFORMATION_REQUESTS,BUSINESS_FIELDS_AND_SCHEMAS", provenance(connection, paid))
                assertEquals("1|TRIAL|INFORMATION_REQUESTS,BUSINESS_FIELDS_AND_SCHEMAS", provenance(connection, trial))
                assertEquals(
                    "1|UNENFORCED|INFORMATION_REQUESTS,BUSINESS_FIELDS_AND_SCHEMAS",
                    provenance(connection, unenforced)
                )
                assertEquals(
                    "1|PAID|INFORMATION_REQUESTS,BUSINESS_FIELDS_AND_SCHEMAS",
                    provenance(connection, personal)
                )
                assertEquals(
                    10,
                    queryInt(
                        connection,
                        "SELECT cardinality(continuation_actions) FROM request_execution_grant WHERE id = ?",
                        paid
                    ),
                )
                assertEquals(
                    packageId.toString(),
                    queryString(
                        connection,
                        "SELECT package_id::text FROM information_request_lineage_source_package WHERE lineage_id = ?",
                        lineageId
                    ),
                )
            }
        }
    }

    private fun provenance(connection: Connection, grantId: UUID): String? =
        queryString(
            connection,
            """
            SELECT policy_version || '|' || entitlement_source || '|' ||
                   array_to_string(ARRAY(SELECT feature FROM unnest(granted_features) feature ORDER BY feature DESC), ',')
            FROM request_execution_grant WHERE id = ?
            """.trimIndent(),
            grantId,
        )

    private fun insertWorkflowInstance(connection: Connection, exchangeId: UUID): UUID {
        val definitionId = UUID.randomUUID()
        val instanceId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO workflow_definition (id, name, version, scope, trigger_event, steps_json, created_at)
            VALUES (?, ?, 1, 'APP', 'exchange.ending', '{"steps":[]}', now())
            """.trimIndent(),
            definitionId,
            "Ending review ${definitionId.toString().take(8)}",
        )
        execute(
            connection,
            """
            INSERT INTO workflow_instance
                (id, definition_id, definition_version, subject_resource_type, subject_resource_id, status, current_step_index, created_at)
            VALUES (?, ?, 1, 'EXCHANGE', ?, 'RUNNING', 0, now())
            """.trimIndent(),
            instanceId,
            definitionId,
            exchangeId,
        )
        return instanceId
    }

    private fun insertEndingIntent(connection: Connection, exchangeId: UUID, instanceId: UUID, principalKind: String) {
        execute(
            connection,
            """
            INSERT INTO exchange_ending_intent
                (id, exchange_id, workflow_instance_id, cancel_remaining_information_requests, requested_by_principal_kind,
                 requested_by_principal_id)
            VALUES (?, ?, ?, TRUE, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            exchangeId,
            instanceId,
            principalKind,
            UUID.randomUUID(),
        )
    }

    private fun insertRun(connection: Connection, requestId: UUID, recordedBy: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value_reconciliation
                (id, information_request_id, recorded_by_principal_kind, recorded_by_principal_id, recorded_at)
            VALUES (?, ?, 'USER', ?, now())
            """.trimIndent(),
            id,
            requestId,
            recordedBy,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertRunItem(
        connection: Connection,
        runId: UUID,
        requestId: UUID,
        number: Int,
        importedValueId: UUID,
        requirementId: UUID,
        outcome: String,
        responseValue: String?,
        discrepancyId: UUID?,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_imported_value_reconciliation_item
                (id, reconciliation_id, information_request_id, item_number, imported_value_id,
                 information_request_requirement_id, outcome, response_canonical_value, discrepancy_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            runId,
            requestId,
            number,
            importedValueId,
            requirementId,
            outcome,
            responseValue,
            discrepancyId,
        )
    }

    private fun insertDraft(connection: Connection, source: SubmissionRuntimeSqlFixture): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, now(), now())
            """.trimIndent(),
            id,
            source.exchangeId,
            source.template.versionId,
            source.template.organizationId,
        )
        return id
    }

    private fun insertSuccessorRequirement(
        connection: Connection,
        source: SubmissionRuntimeSqlFixture,
        successorId: UUID,
        templateRequirementId: UUID,
        bindingId: UUID,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_requirement
                (id, information_request_id, source_template_version_id, source_template_requirement_id,
                 source_template_binding_id, occurrence_path)
            VALUES (?, ?, ?, ?, ?, 'root')
            """.trimIndent(),
            id,
            successorId,
            source.template.versionId,
            templateRequirementId,
            bindingId,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertLineage(
        connection: Connection,
        id: UUID,
        successorId: UUID,
        sourceId: UUID,
        packageId: UUID,
        createdBy: UUID,
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_lineage
                (id, successor_request_id, source_request_id, source_package_id, lineage_kind, created_by_principal_kind,
                 created_by_principal_id)
            VALUES (?, ?, ?, ?, 'SUPPLEMENT', 'USER', ?)
            """.trimIndent(),
            id,
            successorId,
            sourceId,
            packageId,
            createdBy,
        )
    }

    private fun insertSourcePackage(
        connection: Connection,
        lineageId: UUID,
        successorId: UUID,
        sourceId: UUID,
        packageId: UUID
    ) {
        execute(
            connection,
            """
            INSERT INTO information_request_lineage_source_package (id, lineage_id, successor_request_id, source_request_id, package_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            lineageId,
            successorId,
            sourceId,
            packageId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertCarryForward(
        connection: Connection,
        lineageId: UUID,
        successorId: UUID,
        requirementId: UUID,
        packageId: UUID,
        itemId: UUID,
        decision: String,
        reason: String?,
    ) {
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
            successorId,
            requirementId,
            packageId,
            itemId,
            decision,
            reason,
        )
    }

    private fun insertTrialGrant(connection: Connection, organizationId: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO subscription_trial_grant (id, owner_type, owner_id, plan_code, started_at, ended_at, source, reason)
            VALUES (?, 'ORGANIZATION', ?, 'BUSINESS', now() - INTERVAL '1 day', now() + INTERVAL '29 days', 'AUTOMATIC', 'evaluation')
            """.trimIndent(),
            id,
            organizationId,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertGrant(
        connection: Connection,
        runtime: SubmissionRuntimeSqlFixture,
        policyVersion: String = "1",
        source: String = "PAID",
        trialGrantId: UUID? = null,
        features: String = "ARRAY['INFORMATION_REQUESTS']",
        actions: String = "ARRAY['RESPOND', 'SUBMIT']",
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode,
                 policy_version, entitlement_source, trial_grant_id, granted_features, continuation_actions, issued_at, created_at)
            VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', ?, ?, ?, $features::VARCHAR(64)[], $actions::VARCHAR(64)[], now(), now())
            """.trimIndent(),
            id,
            runtime.requestId,
            runtime.template.organizationId,
            policyVersion,
            source,
            trialGrantId,
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertLegacyGrant(
        connection: Connection,
        id: UUID,
        requestId: UUID,
        organizationId: UUID?,
        userId: UUID?,
        planCode: String,
        status: String,
        mode: String,
        trialExpiry: Boolean,
    ) {
        execute(
            connection,
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, owner_user_id, plan_code, subscription_status,
                 enforcement_mode, trial_expires_at, issued_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """.trimIndent(),
            id,
            requestId,
            if (organizationId != null) "ORGANIZATION" else "USER",
            organizationId,
            userId,
            planCode,
            status,
            mode,
            if (trialExpiry) java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(86_400)) else null,
        )
    }

    private fun dispose(connection: Connection, requestId: UUID, organizationId: UUID, principalId: UUID): String {
        execute(
            connection,
            "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
            requestId
        )
        val scheduleId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO record_retention_schedule (id, owner_kind, owner_id, resource_type, version_number,
                                                   minimum_retention_days, disposal_after_days,
                                                   recorded_by_principal_kind, recorded_by_principal_id)
            VALUES (?, 'ORGANIZATION', ?, 'INFORMATION_REQUEST',
                    (SELECT COALESCE(MAX(version_number), 0) + 1 FROM record_retention_schedule WHERE owner_id = ?), 0, 0, 'USER', ?)
            """.trimIndent(),
            scheduleId,
            organizationId,
            organizationId,
            principalId,
        )
        val claimId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO record_disposal_claim (id, resource_type, resource_id, owner_kind, owner_id, basis,
                                               retention_schedule_id, state, claimed_by_principal_kind,
                                               claimed_by_principal_id, claimed_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'ORGANIZATION', ?, 'RETENTION_SCHEDULE', ?, 'CLAIMED', 'SERVICE_ACCOUNT',
                    '00000000-0000-0000-0000-000000000000', now())
            """.trimIndent(),
            claimId,
            requestId,
            organizationId,
            scheduleId,
        )
        execute(
            connection,
            "INSERT INTO record_disposal_claim_scope (claim_id, resource_type, resource_id, direct) VALUES (?, 'INFORMATION_REQUEST', ?, TRUE)",
            claimId,
            requestId.toString(),
        )
        execute(
            connection,
            "UPDATE record_disposal_claim SET state = 'OBJECTS_DELETED', objects_deleted_at = now(), claim_revision = claim_revision + 1 WHERE id = ?",
            claimId,
        )
        return requireNotNull(queryString(connection, "SELECT record_dispose_information_request(?)", claimId))
    }
}
