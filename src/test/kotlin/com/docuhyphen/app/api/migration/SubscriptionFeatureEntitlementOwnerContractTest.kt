package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class FeatureEntitlementPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<FeatureEntitlementPostgreSQLContainer>(imageName)

/**
 * A platform-administered decision to add or remove a product feature can only be recorded against
 * an organization, so an individual account is unreachable: whatever a person's plan omits, nobody
 * can grant it to them and nobody can take it away. Storage has to name the owner before a decision
 * about a person can exist at all.
 *
 * These contract tests hold the four facts that make the override owner-scoped and safe: the owner
 * is recorded, each owner type names exactly one owner, one owner's feature codes are their own even
 * when an organization id and a user id happen to be equal, and every decision already recorded
 * against an organization survives untouched.
 */
class SubscriptionFeatureEntitlementOwnerContractTest
{
    private val releasedVersion = "82"

    @Test
    fun `an organization and a person each hold their own overrides`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                insertEntitlement(
                    connection, fixture.organizationEntitlementId, "ORGANIZATION",
                    organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                )
                insertEntitlement(
                    connection, fixture.personalEntitlementId, "USER",
                    appUserId = fixture.ownerId, updatedBy = fixture.adminId,
                )

                assertEquals(
                    fixture.organizationId,
                    organizationOwnerOf(connection, fixture.organizationEntitlementId),
                )
                assertNull(userOwnerOf(connection, fixture.organizationEntitlementId))
                assertEquals(fixture.ownerId, userOwnerOf(connection, fixture.personalEntitlementId))
                assertNull(organizationOwnerOf(connection, fixture.personalEntitlementId))
            }
        }
    }

    @Test
    fun `each owner type names exactly one owner`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                refused(connection, "ck_subscription_feature_entitlement_owner") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "ORGANIZATION",
                        organizationId = fixture.organizationId, appUserId = fixture.ownerId,
                        updatedBy = fixture.adminId,
                    )
                }
                refused(connection, "ck_subscription_feature_entitlement_owner") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "ORGANIZATION",
                        appUserId = fixture.ownerId, updatedBy = fixture.adminId,
                    )
                }
                refused(connection, "ck_subscription_feature_entitlement_owner") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "USER",
                        organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                    )
                }
                refused(connection, "ck_subscription_feature_entitlement_owner") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "USER", updatedBy = fixture.adminId,
                    )
                }
                // No third kind of owner exists, so a row claiming one names nobody.
                refused(connection, "ck_subscription_feature_entitlement_owner") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "PLATFORM",
                        organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                    )
                }
            }
        }
    }

    @Test
    fun `one owner's feature codes are their own`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)

                insertEntitlement(
                    connection, UUID.randomUUID(), "ORGANIZATION",
                    organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                )
                insertEntitlement(
                    connection, UUID.randomUUID(), "USER",
                    appUserId = fixture.ownerId, updatedBy = fixture.adminId,
                )
                insertEntitlement(
                    connection, UUID.randomUUID(), "USER",
                    appUserId = fixture.otherOwnerId, updatedBy = fixture.adminId,
                )

                // The same owner deciding the same feature twice has no answer.
                refused(connection, "ux_subscription_feature_entitlement_owner_code") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "USER",
                        appUserId = fixture.ownerId, updatedBy = fixture.adminId,
                    )
                }
                refused(connection, "ux_subscription_feature_entitlement_owner_code") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "ORGANIZATION",
                        organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                    )
                }
            }
        }
    }

    @Test
    fun `an organization and a person holding the same id do not share a key space`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val sharedId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                val adminId = UUID.randomUUID()
                insertUser(connection, adminId, "platform-admin@process.test")
                insertOrganization(connection, sharedId, "Shared Id Org", "REG-PROCESS-SHARED-ID")
                insertUser(connection, sharedId, "shared-id-owner@process.test")

                insertEntitlement(
                    connection, UUID.randomUUID(), "ORGANIZATION",
                    organizationId = sharedId, updatedBy = adminId,
                )
                insertEntitlement(
                    connection, UUID.randomUUID(), "USER",
                    appUserId = sharedId, updatedBy = adminId,
                )

                assertEquals(2, countEntitlements(connection, "PROCESS_CAPABILITY"))
            }
        }
    }

    @Test
    fun `decisions already recorded against an organization survive the migration`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertOwners(connection, fixture)
                insertReleasedEntitlement(
                    connection,
                    id = fixture.organizationEntitlementId,
                    organizationId = fixture.organizationId,
                    featureCode = "PROCESS_CAPABILITY",
                    enabled = true,
                    updatedBy = fixture.adminId,
                )
                insertReleasedEntitlement(
                    connection,
                    id = fixture.withdrawnEntitlementId,
                    organizationId = fixture.organizationId,
                    featureCode = "WITHDRAWN_CAPABILITY",
                    enabled = false,
                    updatedBy = fixture.adminId,
                )
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("ORGANIZATION", ownerTypeOf(connection, fixture.organizationEntitlementId))
                assertEquals(
                    fixture.organizationId,
                    organizationOwnerOf(connection, fixture.organizationEntitlementId),
                )
                assertNull(userOwnerOf(connection, fixture.organizationEntitlementId))
                assertEquals(true, enabledOf(connection, fixture.organizationEntitlementId))
                assertEquals(false, enabledOf(connection, fixture.withdrawnEntitlementId))

                // The feature codes that organization already decided are still its own alone.
                refused(connection, "ux_subscription_feature_entitlement_owner_code") {
                    insertEntitlement(
                        connection, UUID.randomUUID(), "ORGANIZATION",
                        organizationId = fixture.organizationId, updatedBy = fixture.adminId,
                    )
                }

                // The same feature code is still available to a person, which is the point.
                insertEntitlement(
                    connection, UUID.randomUUID(), "USER",
                    appUserId = fixture.ownerId, updatedBy = fixture.adminId,
                )
                assertEquals(2, countEntitlements(connection, "PROCESS_CAPABILITY"))
            }
        }
    }

    // ── Fixture ─────────────────────────────────────────────────────────────────

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val ownerId: UUID = UUID.randomUUID()
        val otherOwnerId: UUID = UUID.randomUUID()
        val adminId: UUID = UUID.randomUUID()
        val organizationEntitlementId: UUID = UUID.randomUUID()
        val personalEntitlementId: UUID = UUID.randomUUID()
        val withdrawnEntitlementId: UUID = UUID.randomUUID()
    }

    private fun withPostgres(block: (FeatureEntitlementPostgreSQLContainer) -> Unit)
    {
        val postgres = FeatureEntitlementPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_feature_entitlement_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            block(postgres)
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: FeatureEntitlementPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    /**
     * A refused statement aborts the transaction it ran in, so each one is run in its own and rolled
     * back. Everything inserted before it was committed as it went and survives.
     */
    private fun refused(connection: Connection, expected: String, block: () -> Unit)
    {
        connection.autoCommit = false
        val refusal = try
        {
            assertThrows<SQLException>(block)
        }
        finally
        {
            connection.rollback()
            connection.autoCommit = true
        }

        assertTrue(
            refusal.message.orEmpty().contains(expected),
            "Expected $expected to refuse this row: ${refusal.message}",
        )
    }

    private fun insertOwners(connection: Connection, fixture: Fixture)
    {
        insertOrganization(connection, fixture.organizationId, "Process Owner Org", "REG-PROCESS-ENTITLEMENT")
        insertUser(connection, fixture.adminId, "platform-admin@process.test")
        insertUser(connection, fixture.ownerId, "first-owner@process.test")
        insertUser(connection, fixture.otherOwnerId, "second-owner@process.test")
    }

    private fun insertOrganization(
        connection: Connection,
        organizationId: UUID,
        name: String,
        registrationNumber: String,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete,
                                      created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setString(2, name)
            statement.setString(3, registrationNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertUser(connection: Connection, userId: UUID, email: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, userId)
            statement.setTimestamp(2, Timestamp.from(Instant.now()))
            statement.setString(3, email)
            statement.executeUpdate()
        }
    }

    private fun insertEntitlement(
        connection: Connection,
        id: UUID,
        ownerType: String,
        organizationId: UUID? = null,
        appUserId: UUID? = null,
        featureCode: String = "PROCESS_CAPABILITY",
        enabled: Boolean = true,
        updatedBy: UUID,
    )
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO subscription_feature_entitlement
                (id, owner_type, organization_id, app_user_id, feature_code, is_enabled,
                 updated_by_app_user_id, created_date, updated_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, ownerType)
            statement.setObject(3, organizationId)
            statement.setObject(4, appUserId)
            statement.setString(5, featureCode)
            statement.setBoolean(6, enabled)
            statement.setObject(7, updatedBy)
            statement.setTimestamp(8, now)
            statement.setTimestamp(9, now)
            statement.executeUpdate()
        }
    }

    private fun insertReleasedEntitlement(
        connection: Connection,
        id: UUID,
        organizationId: UUID,
        featureCode: String,
        enabled: Boolean,
        updatedBy: UUID,
    )
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO organization_feature_entitlement
                (id, organization_id, feature_code, is_enabled, updated_by_app_user_id, created_date,
                 updated_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, featureCode)
            statement.setBoolean(4, enabled)
            statement.setObject(5, updatedBy)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, now)
            statement.executeUpdate()
        }
    }

    private fun ownerTypeOf(connection: Connection, id: UUID): String? =
        singleValue(connection, "owner_type", id) { it as? String }

    private fun organizationOwnerOf(connection: Connection, id: UUID): UUID? =
        singleValue(connection, "organization_id", id) { it as? UUID }

    private fun userOwnerOf(connection: Connection, id: UUID): UUID? =
        singleValue(connection, "app_user_id", id) { it as? UUID }

    private fun enabledOf(connection: Connection, id: UUID): Boolean? =
        singleValue(connection, "is_enabled", id) { it as? Boolean }

    private fun <T> singleValue(
        connection: Connection,
        column: String,
        id: UUID,
        read: (Any?) -> T?,
    ): T?
    {
        connection.prepareStatement(
            "SELECT $column FROM subscription_feature_entitlement WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "Expected an override row for $id")
                return read(rows.getObject(1))
            }
        }
    }

    private fun countEntitlements(connection: Connection, featureCode: String): Int
    {
        connection.prepareStatement(
            "SELECT COUNT(*) FROM subscription_feature_entitlement WHERE feature_code = ?",
        ).use { statement ->
            statement.setString(1, featureCode)
            statement.executeQuery().use { rows ->
                rows.next()
                return rows.getInt(1)
            }
        }
    }
}
