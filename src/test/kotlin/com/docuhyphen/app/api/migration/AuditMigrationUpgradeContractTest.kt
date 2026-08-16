package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class KotlinPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<KotlinPostgreSQLContainer>(imageName)

class AuditMigrationUpgradeContractTest
{
    private val migrationDirectory: Path = Path.of("src/main/resources/db/migration")

    @Test
    fun `audit migrations extend the previous schema in version order`()
    {
        val auditTail = Files.list(migrationDirectory).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { it.matches(Regex("V(5[1-4])__.*\\.sql")) }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "V51__workflow_event_outbox.sql",
                "V52__audit_export_build_lease.sql",
                "V53__remove_legacy_audit_storage.sql",
                "V54__audit_archive_format_version.sql",
            ),
            auditTail,
        )
    }

    @Test
    fun `upgrade adds export concurrency columns before removing obsolete audit storage`()
    {
        val leaseMigration = Files.readString(migrationDirectory.resolve("V52__audit_export_build_lease.sql"))
        val cleanupMigration = Files.readString(migrationDirectory.resolve("V53__remove_legacy_audit_storage.sql"))

        assertTrue(leaseMigration.contains("ADD COLUMN build_worker_id"))
        assertTrue(leaseMigration.contains("ADD COLUMN build_lease_expires_at"))
        assertTrue(leaseMigration.contains("ADD COLUMN version"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS auth_audit_event"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS audit_log"))
        assertTrue(cleanupMigration.contains("DROP TABLE IF EXISTS access_audit_log"))
        assertTrue(cleanupMigration.contains("DROP COLUMN IF EXISTS legacy_import"))
    }

    @Test
    fun `released schema upgrades through audit cleanup on PostgreSQL`()
    {
        val postgres = KotlinPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_migration_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            val releasedFlyway = flyway(postgres, target = "51")
            releasedFlyway.migrate()

            postgres.createConnection("").use { connection ->
                assertTrue(tableExists(connection, "auth_audit_event"))
                assertTrue(tableExists(connection, "audit_log"))
                assertTrue(tableExists(connection, "access_audit_log"))
                assertTrue(columnExists(connection, "audit_archive_segment", "legacy_import"))
                assertFalse(columnExists(connection, "audit_export", "build_worker_id"))
            }

            val currentFlyway = flyway(postgres)
            currentFlyway.migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(tableExists(connection, "auth_audit_event"))
                assertFalse(tableExists(connection, "audit_log"))
                assertFalse(tableExists(connection, "access_audit_log"))
                assertFalse(columnExists(connection, "audit_archive_segment", "legacy_import"))
                assertTrue(columnExists(connection, "audit_export", "build_worker_id"))
                assertTrue(columnExists(connection, "audit_export", "build_lease_expires_at"))
                assertTrue(columnExists(connection, "audit_export", "version"))
                assertTrue(columnExists(connection, "audit_archive_segment", "format_version"))
                assertTrue(columnExists(connection, "exchange", "no_auth_access_token_hash"))
                assertTrue(columnExists(connection, "app_user", "authenticator_secret_encrypted"))
                assertTrue(columnExists(connection, "app_user", "email_mfa_fallback_enabled"))
                assertTrue(tableExists(connection, "authenticator_enrollment"))
                val mfaRecordConstraint = checkConstraintDefinition(
                    connection,
                    "mfa_record",
                    "mfa_record_mfa_type_check",
                )
                assertTrue(mfaRecordConstraint?.contains("GOOGLE_AUTHENTICATOR") == true)
                assertTrue(mfaRecordConstraint?.contains("MICROSOFT_AUTHENTICATOR") == true)
                assertTrue(tableExists(connection, "exchange_recipient"))
                assertTrue(columnExists(connection, "exchange_recipient", "direct_share_id"))
                assertTrue(columnExists(connection, "exchange_recipient", "acceptance_status"))
                val participantAcceptanceConstraint = checkConstraintDefinition(
                    connection,
                    "exchange_recipient",
                    "ck_exchange_recipient_participant_acceptance",
                )
                assertTrue(participantAcceptanceConstraint?.contains("NOT_REQUIRED") == true)
                assertTrue(participantAcceptanceConstraint?.contains("TRUSTED_PERSON") == true)
                assertTrue(participantAcceptanceConstraint?.contains("TRUSTED_GROUP") == true)
                assertTrue(tableExists(connection, "organization_trust_relationship"))
                assertTrue(tableExists(connection, "organization_trust_suspension"))
                assertTrue(tableExists(connection, "organization_trust_party_policy"))
                assertTrue(columnExists(connection, "organization_trust_relationship", "version"))
                assertTrue(columnExists(connection, "organization_trust_party_policy", "revision"))
                assertTrue(columnExists(connection, "organization_settings", "discoverable_for_trust_requests"))
                assertTrue(tableExists(connection, "exchange_recipient_attestation"))
                assertTrue(columnExists(connection, "exchange_recipient_attestation", "relationship_id"))
                assertTrue(columnExists(connection, "exchange_recipient_attestation", "subject_group_id"))
                assertTrue(tableExists(connection, "external_identity_resolution"))
                assertTrue(columnExists(connection, "external_identity_resolution", "normalized_email"))
                assertTrue(columnExists(connection, "external_identity_resolution", "consumed_by_exchange_id"))
                assertFalse(tableExists(connection, "organization_exchange_link"))
                assertFalse(columnExists(connection, "organization_settings", "allow_share_without_pairing"))
                assertTrue(columnExists(connection, "organization_settings", "require_trusted_organization_for_b2b"))
                assertTrue(tableExists(connection, "organization_feature_entitlement"))
                assertTrue(columnExists(connection, "organization_feature_entitlement", "feature_code"))
                assertTrue(columnExists(connection, "organization_feature_entitlement", "is_enabled"))
                assertTrue(tableExists(connection, "subscription_trial_grant"))
                assertTrue(columnExists(connection, "subscription_trial_grant", "owner_type"))
                assertTrue(columnExists(connection, "subscription_trial_grant", "granted_by_app_user_id"))
                assertTrue(tableExists(connection, "subscription_trial_request"))
                assertTrue(columnExists(connection, "subscription_trial_request", "requested_by_app_user_id"))
                assertTrue(columnExists(connection, "subscription_trial_request", "trial_grant_id"))
                verifyTrustPersistenceConstraints(connection)
                verifyExchangeRecipientShareBinding(connection)
                verifySubscriptionTrialRequestConstraints(connection)
            }

            assertEquals("75", currentFlyway.info().current().version.toString())
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: KotlinPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { statement ->
            statement.setString(1, "public.$tableName")
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean(1)
            }
        }

    private fun columnExists(connection: Connection, tableName: String, columnName: String): Boolean =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.setString(2, columnName)
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean(1)
            }
        }

    private fun checkConstraintDefinition(
        connection: Connection,
        tableName: String,
        constraintName: String,
    ): String? = connection.prepareStatement(
        """
        SELECT pg_get_constraintdef(constraint_row.oid)
        FROM pg_constraint constraint_row
        JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
        JOIN pg_namespace schema_row ON schema_row.oid = table_row.relnamespace
        WHERE schema_row.nspname = 'public'
          AND table_row.relname = ?
          AND constraint_row.conname = ?
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, tableName)
        statement.setString(2, constraintName)
        statement.executeQuery().use { result ->
            if (result.next()) result.getString(1) else null
        }
    }

    private fun verifyTrustPersistenceConstraints(connection: Connection)
    {
        val organizationAId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val organizationBId = UUID.fromString("20000000-0000-0000-0000-000000000002")
        val outsiderOrganizationId = UUID.fromString("30000000-0000-0000-0000-000000000003")
        val actorId = UUID.fromString("40000000-0000-0000-0000-000000000004")
        val relationshipId = UUID.fromString("50000000-0000-0000-0000-000000000005")
        val now = Timestamp.from(Instant.parse("2026-07-16T08:00:00Z"))

        listOf(organizationAId, organizationBId, outsiderOrganizationId).forEachIndexed { index, organizationId ->
            connection.prepareStatement(
                """INSERT INTO organization
                   (id, is_active, verification_complete, created_date, name, registration_number)
                   VALUES (?, TRUE, TRUE, ?, ?, ?)""",
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.setTimestamp(2, now)
                statement.setString(3, "Trust migration organization $index")
                statement.setString(4, "TRUST-$index")
                statement.executeUpdate()
            }
        }
        connection.prepareStatement(
            """INSERT INTO app_user
               (id, is_active, created_date, email, email_verification_completed, is_temporary,
                sign_in_attempts, exchange_version, multifactor_authentication_type,
                is_password_temporary, email_mfa_fallback_enabled)
               VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
        ).use { statement ->
            statement.setObject(1, actorId)
            statement.setTimestamp(2, now)
            statement.setString(3, "trust-migration@example.test")
            statement.executeUpdate()
        }
        insertPendingTrustRelationship(
            connection,
            relationshipId,
            organizationAId,
            organizationBId,
            organizationAId,
            actorId,
            now,
        )

        listOf(organizationAId, organizationBId).forEach { policyOwnerId ->
            connection.prepareStatement(
                """INSERT INTO organization_trust_party_policy
                   (id, relationship_id, policy_owner_organization_id, updated_at)
                   VALUES (?, ?, ?, ?)""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, relationshipId)
                statement.setObject(3, policyOwnerId)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }

        assertSqlState("23514") {
            connection.prepareStatement(
                """INSERT INTO organization_trust_party_policy
                   (id, relationship_id, policy_owner_organization_id, updated_at)
                   VALUES (?, ?, ?, ?)""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, relationshipId)
                statement.setObject(3, outsiderOrganizationId)
                statement.setTimestamp(4, now)
                statement.executeUpdate()
            }
        }
        assertSqlState("23514") {
            connection.prepareStatement(
                """INSERT INTO organization_trust_suspension
                   (id, relationship_id, suspending_organization_id, reason,
                    suspended_by_app_user_id, suspended_at)
                   VALUES (?, ?, ?, 'Invalid owner', ?, ?)""",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, relationshipId)
                statement.setObject(3, outsiderOrganizationId)
                statement.setObject(4, actorId)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }
        assertSqlState("23514") {
            insertPendingTrustRelationship(
                connection,
                UUID.randomUUID(),
                organizationBId,
                organizationAId,
                organizationAId,
                actorId,
                now,
            )
        }
        assertSqlState("23505") {
            insertPendingTrustRelationship(
                connection,
                UUID.randomUUID(),
                organizationAId,
                organizationBId,
                organizationBId,
                actorId,
                now,
            )
        }
    }

    private fun verifySubscriptionTrialRequestConstraints(connection: Connection)
    {
        val requesterId = UUID.fromString("90000000-0000-0000-0000-000000000001")
        val ownerId = UUID.fromString("90000000-0000-0000-0000-000000000002")
        val now = Timestamp.from(Instant.parse("2026-08-16T08:00:00Z"))
        connection.prepareStatement(
            """INSERT INTO app_user
               (id, is_active, created_date, email, email_verification_completed, is_temporary,
                sign_in_attempts, exchange_version, multifactor_authentication_type,
                is_password_temporary, email_mfa_fallback_enabled)
               VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
        ).use { statement ->
            statement.setObject(1, requesterId)
            statement.setTimestamp(2, now)
            statement.setString(3, "trial-constraint@example.test")
            statement.executeUpdate()
        }

        fun insertPending(
            requestId: UUID,
            planCode: String = "PERSONAL",
            requestOwnerId: UUID = ownerId,
        )
        {
            connection.prepareStatement(
                """INSERT INTO subscription_trial_request
                   (id, owner_type, owner_id, requested_by_app_user_id, plan_code, status,
                    requested_at, updated_at)
                   VALUES (?, 'USER', ?, ?, ?, 'PENDING', ?, ?)""",
            ).use { statement ->
                statement.setObject(1, requestId)
                statement.setObject(2, requestOwnerId)
                statement.setObject(3, requesterId)
                statement.setString(4, planCode)
                statement.setTimestamp(5, now)
                statement.setTimestamp(6, now)
                statement.executeUpdate()
            }
        }

        insertPending(UUID.fromString("90000000-0000-0000-0000-000000000003"))
        assertSqlState("23505") {
            insertPending(UUID.fromString("90000000-0000-0000-0000-000000000004"))
        }
        assertSqlState("23514") {
            insertPending(
                UUID.fromString("90000000-0000-0000-0000-000000000005"),
                "BUSINESS",
                UUID.fromString("90000000-0000-0000-0000-000000000006"),
            )
        }
    }

    private fun verifyExchangeRecipientShareBinding(connection: Connection)
    {
        val exchangeId = UUID.fromString("60000000-0000-0000-0000-000000000006")
        val otherExchangeId = UUID.fromString("60000000-0000-0000-0000-000000000007")
        val directShareId = UUID.fromString("70000000-0000-0000-0000-000000000001")
        val foreignShareId = UUID.fromString("70000000-0000-0000-0000-000000000002")
        val inheritedShareId = UUID.fromString("70000000-0000-0000-0000-000000000003")
        val ownerShareId = UUID.fromString("70000000-0000-0000-0000-000000000004")
        val trustedParticipantShareId = UUID.fromString("70000000-0000-0000-0000-000000000005")
        val ordinaryPendingShareId = UUID.fromString("70000000-0000-0000-0000-000000000006")
        val principalId = UUID.fromString("80000000-0000-0000-0000-000000000001")
        val ownerUserId = UUID.fromString("80000000-0000-0000-0000-000000000009")
        val now = Timestamp.from(Instant.parse("2026-07-16T09:00:00Z"))

        connection.prepareStatement(
            """INSERT INTO app_user
               (id, is_active, created_date, email, email_verification_completed, is_temporary,
                sign_in_attempts, exchange_version, multifactor_authentication_type,
                is_password_temporary, email_mfa_fallback_enabled)
               VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)""",
        ).use { statement ->
            statement.setObject(1, ownerUserId)
            statement.setTimestamp(2, now)
            statement.setString(3, "binding-owner@example.test")
            statement.executeUpdate()
        }
        insertExchange(connection, exchangeId, ownerUserId, now)
        insertExchange(connection, otherExchangeId, ownerUserId, now)
        insertShare(connection, directShareId, exchangeId, "DIRECT", null, "VIEWER", "USER", principalId, now)
        insertShare(connection, foreignShareId, otherExchangeId, "DIRECT", null, "VIEWER", "USER", principalId, now)
        insertShare(
            connection,
            inheritedShareId,
            exchangeId,
            "INHERITED_FROM_GROUP",
            directShareId,
            "VIEWER",
            "USER",
            principalId,
            now,
        )
        insertShare(connection, ownerShareId, exchangeId, "DIRECT", null, "OWNER", "USER", principalId, now)
        insertShare(
            connection,
            trustedParticipantShareId,
            exchangeId,
            "DIRECT",
            null,
            "VIEWER",
            "USER",
            UUID.randomUUID(),
            now,
        )
        insertShare(
            connection,
            ordinaryPendingShareId,
            exchangeId,
            "DIRECT",
            null,
            "VIEWER",
            "USER",
            UUID.randomUUID(),
            now,
        )

        insertExchangeRecipient(connection, UUID.randomUUID(), exchangeId, directShareId, now)

        assertSqlState("23514") {
            insertExchangeRecipient(connection, UUID.randomUUID(), exchangeId, foreignShareId, now)
        }
        assertSqlState("23514") {
            insertExchangeRecipient(connection, UUID.randomUUID(), exchangeId, inheritedShareId, now)
        }
        assertSqlState("23514") {
            insertExchangeRecipient(connection, UUID.randomUUID(), exchangeId, ownerShareId, now)
        }
        insertParticipantRecipient(
            connection,
            UUID.randomUUID(),
            exchangeId,
            trustedParticipantShareId,
            "TRUSTED_PERSON",
            "PENDING",
            now,
        )
        assertSqlState("23514") {
            insertParticipantRecipient(
                connection,
                UUID.randomUUID(),
                exchangeId,
                ordinaryPendingShareId,
                "REGISTERED_USER",
                "PENDING",
                now,
            )
        }
    }

    private fun insertExchange(connection: Connection, exchangeId: UUID, ownerUserId: UUID, now: Timestamp)
    {
        connection.prepareStatement(
            """INSERT INTO exchange
               (id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                description, initial_share_message, name, status, owner_user_id)
               VALUES (?, FALSE, TRUE, ?, ?, 'Binding test', 'Binding test', 'Binding test', 'INITIATED', ?)""",
        ).use { statement ->
            statement.setObject(1, exchangeId)
            statement.setTimestamp(2, now)
            statement.setTimestamp(3, now)
            statement.setObject(4, ownerUserId)
            statement.executeUpdate()
        }
    }

    private fun insertShare(
        connection: Connection,
        shareId: UUID,
        resourceId: UUID,
        source: String,
        sourceShareId: UUID?,
        roleName: String,
        principalKind: String,
        principalId: UUID,
        now: Timestamp,
    )
    {
        connection.prepareStatement(
            """INSERT INTO share
               (id, resource_type, resource_id, principal_kind, principal_id, role_name,
                source, source_share_id, status, granted_at)
               VALUES (?, 'EXCHANGE', ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)""",
        ).use { statement ->
            statement.setObject(1, shareId)
            statement.setObject(2, resourceId)
            statement.setString(3, principalKind)
            statement.setObject(4, principalId)
            statement.setString(5, roleName)
            statement.setString(6, source)
            statement.setObject(7, sourceShareId)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }

    private fun insertExchangeRecipient(
        connection: Connection,
        recipientId: UUID,
        exchangeId: UUID,
        directShareId: UUID,
        now: Timestamp,
    )
    {
        connection.prepareStatement(
            """INSERT INTO exchange_recipient
               (id, exchange_id, direct_share_id, purpose, selection_type, acceptance_status, created_at)
               VALUES (?, ?, ?, 'PARTICIPANT', 'REGISTERED_USER', 'NOT_REQUIRED', ?)""",
        ).use { statement ->
            statement.setObject(1, recipientId)
            statement.setObject(2, exchangeId)
            statement.setObject(3, directShareId)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }

    private fun insertParticipantRecipient(
        connection: Connection,
        recipientId: UUID,
        exchangeId: UUID,
        directShareId: UUID,
        selectionType: String,
        acceptanceStatus: String,
        now: Timestamp,
    )
    {
        connection.prepareStatement(
            """INSERT INTO exchange_recipient
               (id, exchange_id, direct_share_id, purpose, selection_type, acceptance_status, created_at)
               VALUES (?, ?, ?, 'PARTICIPANT', ?, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, recipientId)
            statement.setObject(2, exchangeId)
            statement.setObject(3, directShareId)
            statement.setString(4, selectionType)
            statement.setString(5, acceptanceStatus)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertPendingTrustRelationship(
        connection: Connection,
        relationshipId: UUID,
        organizationAId: UUID,
        organizationBId: UUID,
        requestedByOrganizationId: UUID,
        actorId: UUID,
        requestedAt: Timestamp,
    )
    {
        connection.prepareStatement(
            """INSERT INTO organization_trust_relationship
               (id, organization_a_id, organization_b_id, requested_by_organization_id,
                requested_by_app_user_id, status, requested_at, request_expires_at,
                latest_transition_by_app_user_id)
               VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, relationshipId)
            statement.setObject(2, organizationAId)
            statement.setObject(3, organizationBId)
            statement.setObject(4, requestedByOrganizationId)
            statement.setObject(5, actorId)
            statement.setTimestamp(6, requestedAt)
            statement.setTimestamp(7, Timestamp.from(requestedAt.toInstant().plusSeconds(3600)))
            statement.setObject(8, actorId)
            statement.executeUpdate()
        }
    }

    private fun assertSqlState(expectedSqlState: String, operation: () -> Unit)
    {
        val exception = assertThrows(SQLException::class.java, operation)
        assertEquals(expectedSqlState, exception.sqlState)
    }
}
