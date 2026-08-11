package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private class OrganizationSeatPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<OrganizationSeatPostgreSQLContainer>(imageName)

class OrganizationSeatConcurrencyPostgresContractTest
{
    @Test
    fun `two concurrent activations cannot consume the same last purchased seat`()
    {
        val postgres = OrganizationSeatPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_seat_concurrency")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")
        postgres.start()
        try
        {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            val organizationId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val candidates = listOf(UUID.randomUUID(), UUID.randomUUID())
            postgres.createConnection("").use { connection ->
                insertOrganization(connection, organizationId)
                insertPolicy(connection, organizationId, purchasedSeats = 2)
                insertUser(connection, ownerId, "owner@example.test")
                candidates.forEachIndexed { index, id -> insertUser(connection, id, "candidate-$index@example.test") }
                insertMembership(connection, ownerId, organizationId)
            }

            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val results = try
            {
                candidates.map { candidateId ->
                    executor.submit(Callable {
                        start.await()
                        postgres.createConnection("").use { connection ->
                            connection.autoCommit = false
                            val activated = activateUnderSeatLock(connection, organizationId, candidateId, 2)
                            connection.commit()
                            activated
                        }
                    })
                }.also { start.countDown() }.map { it.get() }
            }
            finally
            {
                executor.shutdownNow()
            }

            assertEquals(listOf(false, true), results.sorted())
            postgres.createConnection("").use { connection ->
                assertEquals(2, activeSeatCount(connection, organizationId))

                connection.prepareStatement("UPDATE app_user SET is_active = FALSE WHERE id = ?").use { statement ->
                    statement.setObject(1, ownerId)
                    statement.executeUpdate()
                }
                val temporaryRecipientId = UUID.randomUUID()
                insertUser(connection, temporaryRecipientId, "recipient@example.test", temporary = true)
                insertMembership(connection, temporaryRecipientId, organizationId)
                assertEquals(1, activeSeatCount(connection, organizationId))

                connection.autoCommit = false
                val rejectedCandidate = candidates[results.indexOf(false)]
                assertEquals(true, activateUnderSeatLock(connection, organizationId, rejectedCandidate, 2))
                connection.commit()
                assertEquals(2, activeSeatCount(connection, organizationId))
            }
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun activateUnderSeatLock(
        connection: Connection,
        organizationId: UUID,
        appUserId: UUID,
        purchasedSeats: Long,
    ): Boolean
    {
        connection.prepareStatement(
            "SELECT id FROM organization_subscription_policy WHERE organization_id = ? FOR UPDATE",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.executeQuery().use { result -> check(result.next()) }
        }
        if (activeSeatCount(connection, organizationId) >= purchasedSeats)
        {
            return false
        }
        insertMembership(connection, appUserId, organizationId)
        return true
    }

    private fun activeSeatCount(connection: Connection, organizationId: UUID): Long =
        connection.prepareStatement(
            """SELECT COUNT(m.id)
               FROM organization_membership m
               JOIN app_user u ON u.id = m.app_user_id
               WHERE m.organization_id = ?
                 AND m.status = 'ACTIVE'
                 AND u.is_active = TRUE
                 AND u.is_temporary = FALSE
                 AND u.deprovisioned_at IS NULL""",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    private fun insertOrganization(connection: Connection, organizationId: UUID)
    {
        connection.prepareStatement(
            """INSERT INTO organization
               (id, name, registration_number, is_active, verification_complete, created_date)
               VALUES (?, 'Seat concurrency organization', ?, TRUE, TRUE, ?)""",
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setString(2, "SEAT-${organizationId.toString().take(8)}")
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertPolicy(connection: Connection, organizationId: UUID, purchasedSeats: Long)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """INSERT INTO organization_subscription_policy
               (id, organization_id, tier_code, max_users, subscription_status,
                created_date, updated_date)
               VALUES (?, ?, 'BUSINESS', ?, 'ACTIVE', ?, ?)""",
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, organizationId)
            statement.setLong(3, purchasedSeats)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertUser(
        connection: Connection,
        appUserId: UUID,
        email: String,
        temporary: Boolean = false,
    )
    {
        connection.prepareStatement(
            """INSERT INTO app_user
               (id, email, is_active, is_temporary, is_password_temporary, sign_in_attempts,
                multifactor_authentication_type, email_mfa_fallback_enabled, exchange_version, created_date)
               VALUES (?, ?, TRUE, ?, FALSE, 0, 'EMAIL', FALSE, 0, ?)""",
        ).use { statement ->
            statement.setObject(1, appUserId)
            statement.setString(2, email)
            statement.setBoolean(3, temporary)
            statement.setTimestamp(4, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertMembership(connection: Connection, appUserId: UUID, organizationId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """INSERT INTO organization_membership
               (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
               VALUES (?, ?, ?, 'ACTIVE', FALSE, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, appUserId)
            statement.setObject(3, organizationId)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }
}
