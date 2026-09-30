package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.entity.ResourceType
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class CleanSchemaPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<CleanSchemaPostgreSQLContainer>(imageName)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CleanSchemaMigrationContractTest
{
    private val postgres = CleanSchemaPostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("docuhyphen_clean_schema_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    private lateinit var flyway: Flyway
    private lateinit var migration: MigrateResult
    private val now: Timestamp = Timestamp.from(Instant.now())

    @BeforeAll
    fun migrateEmptyDatabaseOnce()
    {
        postgres.start()
        flyway = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
        migration = flyway.migrate()
    }

    @AfterAll
    fun stopDatabase()
    {
        postgres.stop()
    }

    @Test
    fun `every migration applies to an empty database in one pass`()
    {
        val versionsOnDisk = migrationVersionsOnDisk()

        assertTrue(migration.success)
        assertEquals(versionsOnDisk.size, migration.migrationsExecuted)
        assertEquals(versionsOnDisk.max().toString(), flyway.info().current().version.version)
        assertTrue(flyway.info().pending().isEmpty())
        inTransaction { connection ->
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success"))
            assertEquals(versionsOnDisk.size, queryInt(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL"))
        }
    }

    @Test
    fun `Fields and Templates keep exactly the platform, organization, and personal scopes`()
    {
        inTransaction { connection ->
            val owner = insertUser(connection)
            insertFieldDefinition(connection, UUID.randomUUID(), "PERSONAL", userId = owner)
            listOf("APP", "ORG", "TEAM").forEach { legacy ->
                connection.refused("ck_field_def_scope_kind") {
                    insertFieldDefinition(connection, UUID.randomUUID(), legacy)
                }
                connection.refused("ck_schema_def_scope_kind") {
                    insertSchemaDefinition(connection, UUID.randomUUID(), legacy)
                }
                connection.refused("ck_request_template_definition_scope_kind") {
                    insertTemplateDefinition(connection, legacy)
                }
            }
            val schemaId = UUID.randomUUID()
            insertSchemaDefinition(connection, schemaId, "PLATFORM")
            val versionId = insertSchemaVersion(connection, schemaId)
            connection.refused("ck_assignment_scope_kind") {
                insertSchemaAssignment(connection, versionId, "ORG")
            }
            insertSchemaAssignment(connection, versionId, "PERSONAL", userId = owner)
        }
    }

    @Test
    fun `Field answers and assignments name only a canonical principal`()
    {
        inTransaction { connection ->
            assertFalse(hasColumn(connection, "field_value", "updated_by_app_user_id"))
            assertFalse(hasColumn(connection, "schema_assignment", "assigned_by_app_user_id"))
            assertFalse(hasColumn(connection, "field_value_revision", "recorded_by_app_user_id"))
            assertTrue(hasColumn(connection, "field_value", "updated_by_principal_kind"))
            assertTrue(hasColumn(connection, "schema_assignment", "assigned_by_principal_kind"))
            assertTrue(hasColumn(connection, "field_value_revision", "recorded_by_principal_kind"))
        }
    }

    @Test
    fun `a Field condition keeps naming one stable Field per Schema Version`()
    {
        inTransaction { connection ->
            val fieldId = UUID.randomUUID()
            insertFieldDefinition(connection, fieldId, "PLATFORM")
            val firstContract = insertFieldContract(connection, fieldId, 1)
            val secondContract = insertFieldContract(connection, fieldId, 2)
            val schemaId = UUID.randomUUID()
            insertSchemaDefinition(connection, schemaId, "PLATFORM")
            val versionId = insertSchemaVersion(connection, schemaId)

            insertBinding(connection, versionId, firstContract, fieldId)
            connection.refused("ux_binding_field_definition") {
                insertBinding(connection, versionId, secondContract, fieldId)
            }
        }
    }

    @Test
    fun `a Document Version states one object store locator, a canonical creator, and its content identity`()
    {
        inTransaction { connection ->
            assertFalse(hasColumn(connection, "document_version", "storage_path"))
            assertFalse(hasColumn(connection, "document_version", "created_by"))
            assertFalse(hasColumn(connection, "document_version", "createdbyemail"))
            val creator = insertUser(connection)
            val documentId = insertDocument(connection)

            insertDocumentVersion(connection, documentId, creator, "OBJECT_STORE", "OBJECT_KEY")
            connection.refused("ck_document_version_storage_locator_provider_kind") {
                insertDocumentVersion(connection, documentId, creator, "LOCAL_FILESYSTEM", "LEGACY_LOCAL_PATH")
            }
            connection.refused("created_by_principal_kind") {
                insertDocumentVersion(connection, documentId, null, "OBJECT_STORE", "OBJECT_KEY")
            }
        }
    }

    @Test
    fun `an Exchange holds exactly its five lifecycle states`()
    {
        inTransaction { connection ->
            val owner = insertUser(connection)
            listOf("INITIATED", "ACCEPTED_STARTED", "ENDED", "REJECTED", "RESCINDED").forEach { status ->
                insertExchange(connection, owner, status)
            }
            listOf("DRAFT", "ACTIVE", "CLOSED").forEach { status ->
                connection.refused("exchange_status_check") { insertExchange(connection, owner, status) }
            }
        }
    }

    @Test
    fun `Share storage admits exactly the Share bearing resource types with resource aware roles`()
    {
        inTransaction { connection ->
            val bearing = mapOf(
                ResourceType.EXCHANGE to "VIEWER",
                ResourceType.DOCUMENT to "VIEWER",
                ResourceType.PRINCIPAL_GROUP to "VIEWER",
                ResourceType.INFORMATION_REQUEST to "CONTRIBUTOR",
            )
            bearing.forEach { (type, role) -> insertShare(connection, type.name, UUID.randomUUID(), role) }
            ResourceType.entries.filterNot { it in bearing.keys }.forEach { type ->
                connection.refused("share_resource_type_check") {
                    insertShare(connection, type.name, UUID.randomUUID(), "VIEWER")
                }
            }
            connection.refused("share_role_name_check") {
                insertShare(connection, ResourceType.EXCHANGE.name, UUID.randomUUID(), "CONTRIBUTOR")
            }
            connection.refused("share_role_name_check") {
                insertShare(connection, ResourceType.INFORMATION_REQUEST.name, UUID.randomUUID(), "VIEWER")
            }
            assertFalse(hasColumn(connection, "share", "granted_by_app_user_id"))
            assertFalse(hasColumn(connection, "share", "revoked_by_app_user_id"))
        }
    }

    @Test
    fun `a Share link is either a direct grant or a verification bootstrap`()
    {
        inTransaction { connection ->
            val shareId = insertShare(connection, ResourceType.EXCHANGE.name, UUID.randomUUID(), "VIEWER")

            insertShareLink(connection, shareId, "DIRECT_GRANT")
            insertShareLink(connection, shareId, "VERIFICATION_BOOTSTRAP")
            connection.refused("share_link_link_mode_check") { insertShareLink(connection, shareId, "PUBLIC_LINK") }
            connection.refused("link_mode") { insertShareLink(connection, shareId, null) }
        }
    }

    @Test
    fun `an External Participant has exactly one owner`()
    {
        inTransaction { connection ->
            val organizationId = insertOrganization(connection)
            val userId = insertUser(connection)

            insertExternalParticipant(connection, organizationId, null)
            insertExternalParticipant(connection, null, userId)
            connection.refused("ck_external_participant_owner") { insertExternalParticipant(connection, null, null) }
            connection.refused("ck_external_participant_owner") {
                insertExternalParticipant(connection, organizationId, userId)
            }
        }
    }

    @Test
    fun `an Exchange recipient stays bound to a direct Share of its own Exchange`()
    {
        inTransaction { connection ->
            val owner = insertUser(connection)
            val exchangeId = insertExchange(connection, owner, "INITIATED")
            val otherExchangeId = insertExchange(connection, owner, "INITIATED")
            val direct = insertShare(connection, ResourceType.EXCHANGE.name, exchangeId, "VIEWER")
            val foreign = insertShare(connection, ResourceType.EXCHANGE.name, otherExchangeId, "VIEWER")
            val requestShare = insertShare(connection, ResourceType.INFORMATION_REQUEST.name, exchangeId, "CONTRIBUTOR")

            insertExchangeRecipient(connection, exchangeId, direct)
            connection.refusedWithState("23514") { insertExchangeRecipient(connection, exchangeId, foreign) }
            connection.refusedWithState("23514") { insertExchangeRecipient(connection, exchangeId, requestShare) }
        }
    }

    @Test
    fun `a domain event names its owner, including a person`()
    {
        inTransaction { connection ->
            insertDomainEvent(connection, "USER", UUID.randomUUID(), null)
            insertDomainEvent(connection, "PLATFORM", null, null)
            connection.refused("owner_kind") { insertDomainEvent(connection, null, null, UUID.randomUUID()) }
            connection.refused("ck_workflow_event_outbox_owner") {
                insertDomainEvent(connection, "USER", null, null)
            }
        }
    }

    @Test
    fun `an audit writer names the owner of what it records and never only an organization`()
    {
        inTransaction { connection ->
            val organizationId = UUID.randomUUID()
            val person = UUID.randomUUID()

            insertAuditOutbox(connection, "USER", person, null)
            connection.refused("owner_type") { insertAuditOutbox(connection, null, null, organizationId) }
            insertAuditLedgerEvent(connection, "USER", person, null)
            connection.refused("owner_type") { insertAuditLedgerEvent(connection, null, null, organizationId) }
            insertAuditAnalyticsFact(connection, "USER", person, null)
            connection.refused("owner_type") { insertAuditAnalyticsFact(connection, null, null, organizationId) }
            insertAuditExport(connection, "USER", person, null)
            connection.refused("owner_type") { insertAuditExport(connection, null, null, organizationId) }
            insertAuditRetentionPolicy(connection, "USER", person, null)
            connection.refused("owner_type") { insertAuditRetentionPolicy(connection, null, null, organizationId) }
            connection.refused("ck_audit_ledger_event_owner") {
                insertAuditLedgerEvent(connection, "USER", person, organizationId)
            }
        }
    }

    @Test
    fun `a preservation hold names its owner, including a person, in the one hold table`()
    {
        inTransaction { connection ->
            assertFalse(hasColumn(connection, "audit_legal_hold", "organization_id"))
            assertEquals(
                setOf("audit_legal_hold", "audit_legal_hold_event"),
                queryStrings(
                    connection,
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name ~ '(^|_)hold(_|$)'",
                ),
            )
            insertHold(connection, "USER", UUID.randomUUID())
            insertHold(connection, "PLATFORM", null)
            connection.refused("ck_audit_legal_hold_owner") { insertHold(connection, "PLATFORM", UUID.randomUUID()) }
            connection.refused("ck_audit_legal_hold_owner") { insertHold(connection, "USER", null) }
        }
    }

    @Test
    fun `the root occurrence is spelled root in Requirements, revisions, and responses`()
    {
        inTransaction { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)

            connection.refused("ck_information_request_requirement_root_path") {
                execute(
                    connection,
                    """
                    INSERT INTO information_request_requirement
                        (id, information_request_id, source_template_version_id, source_template_requirement_id,
                         source_template_binding_id, occurrence_path, created_at)
                    VALUES (?, ?, ?, ?, ?, '$', ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runtime.requestId,
                    runtime.template.versionId,
                    runtime.documentTemplateRequirementId,
                    runtime.documentBindingId,
                    now,
                )
            }
            connection.refused("ck_information_request_requirement_revision_root_path") {
                execute(connection, "SET LOCAL session_replication_role = replica")
                execute(
                    connection,
                    """
                    INSERT INTO information_request_requirement_revision
                        (id, information_request_requirement_id, information_request_id, source_template_version_id,
                         source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                         effective_from, configuration_hash_sha256, optimistic_version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 2, '$', ?, ?, 1, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runtime.documentRequirementId,
                    runtime.requestId,
                    runtime.template.versionId,
                    runtime.documentTemplateRequirementId,
                    runtime.documentBindingId,
                    now,
                    "0".repeat(64),
                    now,
                )
            }
            connection.refused("ck_information_request_response_root_path") {
                execute(connection, "SET LOCAL session_replication_role = replica")
                execute(
                    connection,
                    """
                    INSERT INTO information_request_response
                        (id, information_request_id, information_request_requirement_id, requirement_revision_id,
                         occurrence_path, disposition, response_revision, recorded_by_principal_kind,
                         recorded_by_principal_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, '$', 'PROVIDED', 1, 'USER', ?, ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runtime.requestId,
                    runtime.attestationRequirementId,
                    runtime.attestationRevisionId,
                    runtime.contributorUserId,
                    now,
                    now,
                )
            }
        }
    }

    @Test
    fun `a commercial feature decision is owned by an organization or a person`()
    {
        inTransaction { connection ->
            assertFalse(tableExists(connection, "organization_feature_entitlement"))
            val organizationId = insertOrganization(connection)
            val userId = insertUser(connection)

            insertFeatureEntitlement(connection, "ORGANIZATION", organizationId, null, userId)
            insertFeatureEntitlement(connection, "USER", null, userId, userId)
            connection.refused("ck_subscription_feature_entitlement_owner") {
                insertFeatureEntitlement(connection, "USER", organizationId, userId, userId)
            }
            connection.refused("ck_subscription_feature_entitlement_owner") {
                insertFeatureEntitlement(connection, "ORGANIZATION", null, null, userId)
            }
        }
    }

    @Test
    fun `Workflow triggers describe Information Request subjects without personal data`()
    {
        inTransaction { connection ->
            val subjectFields = queryStrings(
                connection,
                "SELECT subject_fields_json FROM workflow_trigger_event_registry WHERE subject_resource_type = 'INFORMATION_REQUEST'",
            )
            assertTrue(subjectFields.isNotEmpty())
            subjectFields.forEach { fields ->
                val names = Regex(""""name"\s*:\s*"([^"]+)"""").findAll(fields).map { it.groupValues[1] }.toList()
                assertTrue("requestId" in names)
                assertTrue(names.none { it.lowercase().contains("email") || it.lowercase().contains("name") }, names.toString())
            }
            connection.refused("ck_workflow_trigger_event_registry_subject_type") {
                execute(
                    connection,
                    """
                    INSERT INTO workflow_trigger_event_registry (event_name, subject_resource_type, subject_schema_version)
                    VALUES ('process.record.reviewed', 'DOCUMENT', 1)
                    """.trimIndent(),
                )
            }
        }
    }

    @Test
    fun `a Blueprint names one exact Template Version and keeps its existing defaults`()
    {
        inTransaction { connection ->
            listOf(
                "blueprint_participant_default",
                "blueprint_document_default",
                "blueprint_field_default",
            ).forEach { table -> assertTrue(tableExists(connection, table), "$table keeps its defaults") }
            assertTrue(hasColumn(connection, "blueprint_definition", "schema_definition_id"))
            connection.refused("blueprint_definition_request_template_version_fkey") {
                execute(
                    connection,
                    """
                    INSERT INTO blueprint_definition
                        (id, name, scope, config_json, created_at, updated_at, information_request_template_version_id)
                    VALUES (?, 'Process blueprint', 'APP', '{}', ?, ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    now,
                    now,
                    UUID.randomUUID(),
                )
            }
        }
    }

    @Test
    fun `an execution grant keeps what it froze and is revoked once`()
    {
        inTransaction { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val grantId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_user_id, plan_code, subscription_status, enforcement_mode,
                     acting_party_cap, evidence_file_allowance, evidence_byte_allowance, issued_at, created_at)
                VALUES (?, ?, 'USER', ?, 'PERSONAL', 'ACTIVE', 'ENFORCE', 10, 100, 262144000, ?, ?)
                """.trimIndent(),
                grantId,
                runtime.requestId,
                insertUser(connection),
                now,
                now,
            )

            connection.refused("keeps the position it was issued under") {
                execute(connection, "UPDATE request_execution_grant SET acting_party_cap = 11 WHERE id = ?", grantId)
            }
            connection.refused("keeps the position it was issued under") {
                execute(connection, "UPDATE request_execution_grant SET evidence_byte_allowance = NULL WHERE id = ?", grantId)
            }
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = ?, revoked_reason = 'stopped' WHERE id = ?",
                now,
                grantId,
            )
            connection.refused("is revoked once") {
                execute(connection, "UPDATE request_execution_grant SET revoked_reason = 'rewritten' WHERE id = ?", grantId)
            }
            connection.refused("is revoked once") {
                execute(connection, "UPDATE request_execution_grant SET revoked_at = NULL WHERE id = ?", grantId)
            }
            connection.refused("ck_request_execution_usage_reservation_usage_kind") {
                execute(
                    connection,
                    """
                    INSERT INTO request_execution_usage_reservation
                        (id, grant_id, usage_kind, reservation_key, quantity, status, reserved_at, created_at)
                    VALUES (?, ?, 'ADDITIONAL_RECIPIENT', 'party', 1, 'RESERVED', ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    grantId,
                    now,
                    now,
                )
            }
        }
    }

    private fun migrationVersionsOnDisk(): List<Int> =
        File("src/main/resources/db/migration").listFiles().orEmpty()
            .mapNotNull { Regex("""^V(\d+)__.+\.sql$""").find(it.name)?.groupValues?.get(1)?.toInt() }

    private fun inTransaction(block: (Connection) -> Unit)
    {
        postgres.createConnection("").use { connection ->
            connection.autoCommit = false
            try
            {
                block(connection)
            }
            finally
            {
                connection.rollback()
            }
        }
    }

    private fun Connection.refused(expected: String, block: () -> Unit)
    {
        val savepoint = setSavepoint()
        val refusal = assertThrows<SQLException>(block)
        rollback(savepoint)
        assertTrue(
            refusal.message.orEmpty().contains(expected),
            "Expected $expected to refuse this statement: ${refusal.message}",
        )
    }

    private fun Connection.refusedWithState(state: String, block: () -> Unit)
    {
        val savepoint = setSavepoint()
        val refusal = assertThrows<SQLException>(block)
        rollback(savepoint)
        assertEquals(state, refusal.sqlState, refusal.message)
    }

    private fun hasColumn(connection: Connection, table: String, column: String): Boolean =
        queryInt(
            connection,
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
            table,
            column,
        ) == 1

    private fun tableExists(connection: Connection, table: String): Boolean =
        queryInt(
            connection,
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
            table,
        ) == 1

    private fun insertOrganization(connection: Connection): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Process Owner', ?, TRUE, TRUE, ?)
            """.trimIndent(),
            id,
            "REG-${id.toString().take(8)}",
            now,
        )
        return id
    }

    private fun insertUser(connection: Connection): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
            id,
            now,
            "clean-schema-${id.toString().take(8)}@process.test",
        )
        return id
    }

    private fun insertFieldDefinition(connection: Connection, id: UUID, scopeKind: String, userId: UUID? = null)
    {
        execute(
            connection,
            """
            INSERT INTO field_definition
                (id, scope_kind, scope_org_id, scope_user_id, namespace, field_key, status, created_at, updated_at)
            VALUES (?, ?, NULL, ?, 'process', ?, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            id,
            scopeKind,
            userId,
            "field-${id.toString().take(8)}",
            now,
            now,
        )
    }

    private fun insertFieldContract(connection: Connection, fieldId: UUID, contractVersion: Int): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label, created_at)
            VALUES (?, ?, ?, 'SHORT_TEXT', 'Recorded note', ?)
            """.trimIndent(),
            id,
            fieldId,
            contractVersion,
            now,
        )
        return id
    }

    private fun insertSchemaDefinition(connection: Connection, id: UUID, scopeKind: String)
    {
        execute(
            connection,
            """
            INSERT INTO schema_definition
                (id, scope_kind, scope_org_id, namespace, schema_key, display_name, target_resource_type, status,
                 created_at, updated_at)
            VALUES (?, ?, NULL, 'process', ?, 'Process data', 'EXCHANGE', 'PUBLISHED', ?, ?)
            """.trimIndent(),
            id,
            scopeKind,
            "schema-${id.toString().take(8)}",
            now,
            now,
        )
    }

    private fun insertSchemaVersion(connection: Connection, schemaId: UUID): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at, created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
            id,
            schemaId,
            now,
            now,
        )
        return id
    }

    private fun insertBinding(connection: Connection, versionId: UUID, contractId: UUID, fieldId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id, field_definition_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            versionId,
            contractId,
            fieldId,
        )
    }

    private fun insertSchemaAssignment(connection: Connection, versionId: UUID, scopeKind: String, userId: UUID? = null)
    {
        execute(
            connection,
            """
            INSERT INTO schema_assignment
                (id, resource_type, resource_id, schema_version_id, scope_kind, scope_org_id, scope_user_id,
                 assignment_source, assigned_at, assigned_by_principal_kind, assigned_by_principal_id)
            VALUES (?, 'EXCHANGE', ?, ?, ?, NULL, ?, 'MANUAL', ?, 'USER', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            versionId,
            scopeKind,
            userId,
            now,
            userId ?: UUID.randomUUID(),
        )
    }

    private fun insertTemplateDefinition(connection: Connection, scopeKind: String)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, namespace, template_key, display_name, status, created_at, updated_at)
            VALUES (?, ?, 'process', ?, 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            scopeKind,
            "collection-${UUID.randomUUID().toString().take(8)}",
            now,
            now,
        )
    }

    private fun insertDocument(connection: Connection): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO document (id, encryption_mode, is_deleted, created_date, update_date, title, type)
            VALUES (?, 0, FALSE, ?, ?, 'Process record', 'PDF')
            """.trimIndent(),
            id,
            now,
            now,
        )
        return id
    }

    private fun insertDocumentVersion(
        connection: Connection,
        documentId: UUID,
        creator: UUID?,
        provider: String,
        locatorKind: String,
    )
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO document_version
                (id, document_id, created_by_principal_kind, created_by_principal_id, created_date, file_name,
                 version, storage_provider, storage_locator_kind, storage_locator,
                 content_length, content_hash_algorithm, content_hash, content_verification)
            VALUES (?, ?, ?, ?, ?, 'process-record.pdf', '1', ?, ?, ?,
                    3, 'SHA_256', 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'VERIFIED')
            """.trimIndent(),
            id,
            documentId,
            creator?.let { "USER" },
            creator,
            now,
            provider,
            locatorKind,
            "document-versions/$id/process-record.pdf",
        )
    }

    private fun insertExchange(connection: Connection, owner: UUID, status: String): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO exchange
                (id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                 description, initial_share_message, name, status, owner_user_id)
            VALUES (?, FALSE, FALSE, ?, ?, 'Process collection', 'Please respond', 'Process collection', ?, ?)
            """.trimIndent(),
            id,
            now,
            now,
            status,
            owner,
        )
        return id
    }

    private fun insertShare(connection: Connection, resourceType: String, resourceId: UUID, roleName: String): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share
                (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, ?, ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', ?)
            """.trimIndent(),
            id,
            resourceType,
            resourceId,
            UUID.randomUUID(),
            roleName,
            now,
        )
        return id
    }

    private fun insertShareLink(connection: Connection, shareId: UUID, mode: String?)
    {
        execute(
            connection,
            """
            INSERT INTO share_link (id, share_id, token_hash, status, created_at, link_mode)
            VALUES (?, ?, ?, 'ACTIVE', ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            shareId,
            "hash-${UUID.randomUUID()}",
            now,
            mode,
        )
    }

    private fun insertExternalParticipant(connection: Connection, organizationId: UUID?, userId: UUID?)
    {
        val email = "participant-${UUID.randomUUID().toString().take(8)}@process.test"
        execute(
            connection,
            """
            INSERT INTO external_participant (id, owner_organization_id, owner_app_user_id, email, email_lower, created_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            organizationId,
            userId,
            email,
            email,
            now,
        )
    }

    private fun insertExchangeRecipient(connection: Connection, exchangeId: UUID, directShareId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO exchange_recipient
                (id, exchange_id, direct_share_id, purpose, selection_type, acceptance_status, created_at)
            VALUES (?, ?, ?, 'PARTICIPANT', 'REGISTERED_USER', 'NOT_REQUIRED', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            exchangeId,
            directShareId,
            now,
        )
    }

    private fun insertDomainEvent(connection: Connection, ownerKind: String?, ownerId: UUID?, organizationId: UUID?)
    {
        val eventId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO workflow_event_outbox
                (id, event_id, idempotency_key, event_type, owner_kind, owner_id, organization_id, envelope_json,
                 status, attempt_count, created_at, next_attempt_at)
            VALUES (?, ?, ?, 'process.record.recorded', ?, ?, ?, '{}', 'PENDING', 0, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            eventId,
            "process:$eventId",
            ownerKind,
            ownerId,
            organizationId,
            now,
            now,
        )
    }

    private fun insertAuditOutbox(connection: Connection, ownerType: String?, ownerId: UUID?, organizationId: UUID?)
    {
        val eventId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO audit_outbox
                (id, event_id, idempotency_key, event_type_key, category, outcome, owner_type, owner_id,
                 organization_id, payload_json, occurred_at, recorded_at, catalog_version)
            VALUES (?, ?, ?, 'auth.login.succeeded', 'AUTHENTICATION', 'SUCCESS', ?, ?, ?, '{}', ?, ?, 14)
            """.trimIndent(),
            UUID.randomUUID(),
            eventId,
            eventId.toString(),
            ownerType,
            ownerId,
            organizationId,
            now,
            now,
        )
    }

    private fun insertAuditLedgerEvent(connection: Connection, ownerType: String?, ownerId: UUID?, organizationId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO audit_ledger_event
                (id, event_id, event_type_key, category, outcome, schema_version, occurred_at, recorded_at,
                 stream_id, stream_sequence, actor_kind, payload_json, event_hash, owner_type, owner_id, organization_id)
            VALUES (?, ?, 'auth.login.succeeded', 'AUTHENTICATION', 'SUCCESS', 1, ?, ?, ?, 1, 'USER', '{}', ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            now,
            now,
            "stream-${UUID.randomUUID()}",
            "0".repeat(64),
            ownerType,
            ownerId,
            organizationId,
        )
    }

    private fun insertAuditAnalyticsFact(connection: Connection, ownerType: String?, ownerId: UUID?, organizationId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO audit_analytics_fact
                (id, ledger_event_id, stream_id, category, event_type_key, outcome, actor_kind, occurred_at,
                 occurred_date, schema_version, owner_type, owner_id, organization_id)
            VALUES (?, ?, ?, 'AUTHENTICATION', 'auth.login.succeeded', 'SUCCESS', 'USER', ?, CURRENT_DATE, 1, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "stream-${UUID.randomUUID()}",
            now,
            ownerType,
            ownerId,
            organizationId,
        )
    }

    private fun insertAuditExport(connection: Connection, ownerType: String?, ownerId: UUID?, organizationId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO audit_export
                (id, requested_by_user_id, requested_at, categories_csv, occurred_after, occurred_before, purpose,
                 status, owner_type, owner_id, organization_id, created_at, updated_at)
            VALUES (?, ?, ?, 'SECURITY', ?, ?, 'record review', 'REQUESTED', ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            now,
            Timestamp.from(now.toInstant().minusSeconds(3600)),
            now,
            ownerType,
            ownerId,
            organizationId,
            now,
            now,
        )
    }

    private fun insertAuditRetentionPolicy(connection: Connection, ownerType: String?, ownerId: UUID?, organizationId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO audit_retention_policy
                (id, category, ledger_retention_days, archive_retention_days, updated_by_user_id, owner_type, owner_id,
                 organization_id)
            VALUES (?, ?, 365, 730, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            "SECURITY-${UUID.randomUUID().toString().take(8)}",
            UUID.randomUUID(),
            ownerType,
            ownerId,
            organizationId,
        )
    }

    private fun insertHold(connection: Connection, ownerKind: String, ownerId: UUID?)
    {
        execute(
            connection,
            """
            INSERT INTO audit_legal_hold
                (id, resource_type, resource_id, reason, status, owner_kind, owner_id, scope, effective_from,
                 placed_by_principal_kind, placed_by_principal_id)
            VALUES (?, 'EXCHANGE', ?, 'Record review', 'ACTIVE', ?, ?, 'RESOURCE', ?, 'USER', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            ownerKind,
            ownerId,
            now,
            UUID.randomUUID(),
        )
    }

    private fun insertFeatureEntitlement(
        connection: Connection,
        ownerType: String,
        organizationId: UUID?,
        userId: UUID?,
        updatedBy: UUID,
    )
    {
        execute(
            connection,
            """
            INSERT INTO subscription_feature_entitlement
                (id, owner_type, organization_id, app_user_id, feature_code, updated_by_app_user_id)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            ownerType,
            organizationId,
            userId,
            "INFORMATION_REQUESTS",
            updatedBy,
        )
    }
}
