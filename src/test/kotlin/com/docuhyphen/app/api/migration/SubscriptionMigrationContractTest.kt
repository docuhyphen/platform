package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class SubscriptionPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<SubscriptionPostgreSQLContainer>(imageName)

/**
 * Verifies that introducing explicit subscription ownership never removes access from data that
 * already exists: registered accounts keep a working plan, organizations keep their seat
 * capacity, and the new constraints reject plans assigned to the wrong kind of owner.
 */
class SubscriptionMigrationContractTest
{
    private val subscriptionMigrationVersion = "71"
    private val previousVersion = "70"

    @Test
    fun `existing accounts and organizations keep working access after the subscription migration`()
    {
        val postgres = SubscriptionPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_subscription_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            flyway(postgres, target = previousVersion).migrate()

            val registeredUserId = UUID.randomUUID()
            val temporaryUserId = UUID.randomUUID()
            val cappedOrganizationId = UUID.randomUUID()
            val uncappedOrganizationId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertAppUser(connection, registeredUserId, "existing.user@example.com", temporary = false)
                insertAppUser(connection, temporaryUserId, "no.account.recipient@example.com", temporary = true)
                insertOrganization(connection, cappedOrganizationId, "Capped Org", "REG-CAPPED")
                insertOrganization(connection, uncappedOrganizationId, "Uncapped Org", "REG-UNCAPPED")
                insertOrganizationPolicy(connection, cappedOrganizationId, tierCode = "FREE", maxUsers = 25)
            }

            // Stops at the subscription migration so this contract stays about that migration
            // and is not disturbed by later schema work.
            val currentFlyway = flyway(postgres, target = subscriptionMigrationVersion)
            currentFlyway.migrate()

            postgres.createConnection("").use { connection ->
                assertTrue(tableExists(connection, "user_subscription_policy"))

                // An account that already existed is grandfathered so enforcement never takes
                // away a feature it was already using.
                assertEquals("PERSONAL", userPlanCode(connection, registeredUserId))
                assertEquals("ACTIVE", userStatus(connection, registeredUserId))

                // Recipient placeholders are not subscribers.
                assertNull(userPlanCode(connection, temporaryUserId))

                // Purchased seat capacity is preserved exactly.
                assertEquals("BUSINESS", organizationTierCode(connection, cappedOrganizationId))
                assertEquals(25L, organizationMaxUsers(connection, cappedOrganizationId))
                assertEquals("ACTIVE", organizationStatus(connection, cappedOrganizationId))

                // An organization that relied on an implicit default now owns an explicit record
                // and stays uncapped until seats are purchased.
                assertEquals("BUSINESS", organizationTierCode(connection, uncappedOrganizationId))
                assertNull(organizationMaxUsers(connection, uncappedOrganizationId))
            }

            assertEquals(subscriptionMigrationVersion, currentFlyway.info().current().version.toString())
        }
        finally
        {
            postgres.stop()
        }
    }

    @Test
    fun `constraints keep plans on the owner type that can hold them`()
    {
        val postgres = SubscriptionPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_subscription_constraint_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            flyway(postgres).migrate()

            val appUserId = UUID.randomUUID()
            val organizationId = UUID.randomUUID()

            postgres.createConnection("").use { connection ->
                insertAppUser(connection, appUserId, "constraint.user@example.com", temporary = false)
                insertOrganization(connection, organizationId, "Constraint Org", "REG-CONSTRAINT")

                // One individual plan row per account.
                insertUserPolicy(connection, appUserId, planCode = "FREE")
                assertNotNull(userPlanCode(connection, appUserId))
                assertThrows(SQLException::class.java) {
                    insertUserPolicy(connection, appUserId, planCode = "PERSONAL")
                }

                // Business belongs to an organization and is refused on an individual account.
                val secondUserId = UUID.randomUUID()
                insertAppUser(connection, secondUserId, "business.user@example.com", temporary = false)
                assertThrows(SQLException::class.java) {
                    insertUserPolicy(connection, secondUserId, planCode = "BUSINESS")
                }

                assertThrows(SQLException::class.java) {
                    insertOrganizationPolicy(connection, organizationId, tierCode = "PERSONAL", maxUsers = null)
                }
                assertThrows(SQLException::class.java) {
                    insertOrganizationPolicy(connection, organizationId, tierCode = "BUSINESS", maxUsers = 0)
                }

                insertOrganizationPolicy(connection, organizationId, tierCode = "BUSINESS", maxUsers = 10)
                assertThrows(SQLException::class.java) {
                    insertOrganizationPolicy(connection, organizationId, tierCode = "BUSINESS", maxUsers = 10)
                }

                assertFalse(tableExists(connection, "user_subscription_tier"))
            }
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: SubscriptionPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertAppUser(connection: Connection, id: UUID, email: String, temporary: Boolean)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user (id, email, is_active, is_temporary, is_password_temporary,
                                  sign_in_attempts, multifactor_authentication_type,
                                  email_mfa_fallback_enabled, exchange_version, created_date)
            VALUES (?, ?, TRUE, ?, FALSE, 0, 'EMAIL', FALSE, 0, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, email)
            statement.setBoolean(3, temporary)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertOrganization(connection: Connection, id: UUID, name: String, registrationNumber: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, name)
            statement.setString(3, registrationNumber)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertOrganizationPolicy(
        connection: Connection,
        organizationId: UUID,
        tierCode: String,
        maxUsers: Long?,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO organization_subscription_policy (id, organization_id, tier_code, max_users,
                                                          created_date, updated_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, organizationId)
            statement.setString(3, tierCode)
            if (maxUsers == null) statement.setNull(4, java.sql.Types.BIGINT) else statement.setLong(4, maxUsers)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private fun insertUserPolicy(connection: Connection, appUserId: UUID, planCode: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status,
                                                  created_date, updated_date)
            VALUES (?, ?, ?, 'ACTIVE', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, appUserId)
            statement.setString(3, planCode)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun userPlanCode(connection: Connection, appUserId: UUID): String? =
        singleValue(connection, "SELECT plan_code FROM user_subscription_policy WHERE app_user_id = ?", appUserId)

    private fun userStatus(connection: Connection, appUserId: UUID): String? =
        singleValue(
            connection,
            "SELECT subscription_status FROM user_subscription_policy WHERE app_user_id = ?",
            appUserId,
        )

    private fun organizationTierCode(connection: Connection, organizationId: UUID): String? =
        singleValue(
            connection,
            "SELECT tier_code FROM organization_subscription_policy WHERE organization_id = ?",
            organizationId,
        )

    private fun organizationStatus(connection: Connection, organizationId: UUID): String? =
        singleValue(
            connection,
            "SELECT subscription_status FROM organization_subscription_policy WHERE organization_id = ?",
            organizationId,
        )

    private fun organizationMaxUsers(connection: Connection, organizationId: UUID): Long?
    {
        connection.prepareStatement(
            "SELECT max_users FROM organization_subscription_policy WHERE organization_id = ?",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.executeQuery().use { result ->
                if (!result.next())
                {
                    return null
                }
                val value = result.getLong(1)
                return if (result.wasNull()) null else value
            }
        }
    }

    private fun singleValue(connection: Connection, sql: String, parameter: UUID): String?
    {
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, parameter)
            statement.executeQuery().use { result ->
                return if (result.next()) result.getString(1) else null
            }
        }
    }

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { statement ->
            statement.setString(1, "public.$tableName")
            statement.executeQuery().use { result ->
                result.next()
                result.getBoolean(1)
            }
        }
}





