package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier

private class ResumeConcurrencyPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<ResumeConcurrencyPostgreSQLContainer>(imageName)

/**
 * Proves that two organizations clearing their own trust suspensions concurrently cannot both skip
 * the final "last suspension cleared" group access reconciliation. Each resume transaction takes the
 * same per-relationship advisory lock before clearing its suspension and counting the remaining
 * active suspensions, so the transactions serialize and exactly one of them observes zero remaining.
 */
class OrganizationTrustResumeConcurrencyContractTest
{
    private val organizationAId = UUID.fromString("10000000-0000-0000-0000-0000000000a1")
    private val organizationBId = UUID.fromString("20000000-0000-0000-0000-0000000000b2")
    private val actorId = UUID.fromString("40000000-0000-0000-0000-0000000000c3")
    private val relationshipId = UUID.fromString("50000000-0000-0000-0000-0000000000d4")
    private val suspensionAId = UUID.fromString("60000000-0000-0000-0000-0000000000e5")
    private val suspensionBId = UUID.fromString("60000000-0000-0000-0000-0000000000f6")
    private val now = Timestamp.from(Instant.parse("2026-07-16T08:00:00Z"))

    @Test
    fun `concurrent resume serializes so reconciliation observes the cleared last suspension exactly once`()
    {
        val postgres = ResumeConcurrencyPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_resume_concurrency_test")
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

            postgres.createConnection("").use { setup -> seedActiveRelationshipWithBothSuspensions(setup) }

            val observedRemaining = CopyOnWriteArrayList<Int>()
            val barrier = CyclicBarrier(2)
            val partyA = resumeThread(postgres, suspensionAId, barrier, observedRemaining)
            val partyB = resumeThread(postgres, suspensionBId, barrier, observedRemaining)
            partyA.start()
            partyB.start()
            partyA.join()
            partyB.join()

            assertEquals(2, observedRemaining.size)
            assertEquals(
                1,
                observedRemaining.count { it == 0 },
                "Exactly one resume must observe no remaining active suspension",
            )
            assertEquals(
                1,
                observedRemaining.count { it == 1 },
                "The first resume must still observe the partner's active suspension",
            )
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun resumeThread(
        postgres: ResumeConcurrencyPostgreSQLContainer,
        suspensionId: UUID,
        barrier: CyclicBarrier,
        observedRemaining: MutableList<Int>,
    ): Thread = Thread {
        postgres.createConnection("").use { connection ->
            connection.autoCommit = false
            barrier.await()
            connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
            ).use { statement ->
                statement.setString(1, "$organizationAId:$organizationBId")
                statement.executeQuery().use { it.next() }
            }
            connection.prepareStatement(
                "UPDATE organization_trust_suspension SET cleared_by_app_user_id = ?, cleared_at = ? WHERE id = ?",
            ).use { statement ->
                statement.setObject(1, actorId)
                statement.setTimestamp(2, now)
                statement.setObject(3, suspensionId)
                statement.executeUpdate()
            }
            val remaining = connection.prepareStatement(
                "SELECT count(*) FROM organization_trust_suspension WHERE relationship_id = ? AND cleared_at IS NULL",
            ).use { statement ->
                statement.setObject(1, relationshipId)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
            observedRemaining.add(remaining)
            connection.commit()
        }
    }

    private fun seedActiveRelationshipWithBothSuspensions(connection: Connection)
    {
        listOf(organizationAId, organizationBId).forEachIndexed { index, organizationId ->
            connection.prepareStatement(
                """INSERT INTO organization
                   (id, is_active, verification_complete, created_date, name, registration_number)
                   VALUES (?, TRUE, TRUE, ?, ?, ?)""",
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.setTimestamp(2, now)
                statement.setString(3, "Resume concurrency organization $index")
                statement.setString(4, "RESUME-$index")
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
            statement.setString(3, "resume-concurrency@example.test")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """INSERT INTO organization_trust_relationship
               (id, organization_a_id, organization_b_id, requested_by_organization_id,
                requested_by_app_user_id, status, requested_at, request_expires_at, activated_at,
                review_due_at, latest_transition_by_app_user_id)
               VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setObject(1, relationshipId)
            statement.setObject(2, organizationAId)
            statement.setObject(3, organizationBId)
            statement.setObject(4, organizationAId)
            statement.setObject(5, actorId)
            statement.setTimestamp(6, now)
            statement.setTimestamp(7, Timestamp.from(now.toInstant().plusSeconds(3600)))
            statement.setTimestamp(8, now)
            statement.setTimestamp(9, Timestamp.from(now.toInstant().plusSeconds(365L * 24 * 3600)))
            statement.setObject(10, actorId)
            statement.executeUpdate()
        }
        listOf(suspensionAId to organizationAId, suspensionBId to organizationBId).forEach { (suspensionId, ownerId) ->
            connection.prepareStatement(
                """INSERT INTO organization_trust_suspension
                   (id, relationship_id, suspending_organization_id, reason,
                    suspended_by_app_user_id, suspended_at)
                   VALUES (?, ?, ?, 'Concurrent review', ?, ?)""",
            ).use { statement ->
                statement.setObject(1, suspensionId)
                statement.setObject(2, relationshipId)
                statement.setObject(3, ownerId)
                statement.setObject(4, actorId)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }
    }
}
