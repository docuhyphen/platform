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

private class RequestExecutionUsageReservationPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<RequestExecutionUsageReservationPostgreSQLContainer>(imageName)

class RequestExecutionUsageReservationConcurrencyPostgresContractTest
{
    @Test
    fun `concurrent reservations against a two-unit cap admit exactly two`()
    {
        val postgres = RequestExecutionUsageReservationPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_execution_usage_reservation_concurrency")
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

            val fixture = postgres.createConnection("").use { Fixture(it) }
            val grantId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertGrant(connection, grantId, fixture.requestId, fixture.organizationId, cap = 2L)
            }

            val callers = listOf("caller-1", "caller-2", "caller-3", "caller-4")
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(callers.size)
            val results = try
            {
                callers.map { key ->
                    executor.submit(Callable {
                        start.await()
                        postgres.createConnection("").use { connection ->
                            connection.autoCommit = false
                            val accepted = reserveUnderGrantLock(connection, grantId, key, quantity = 1L, cap = 2L)
                            connection.commit()
                            accepted
                        }
                    })
                }.also { start.countDown() }.map { it.get() }
            }
            finally
            {
                executor.shutdownNow()
            }

            assertEquals(2, results.count { it })
            assertEquals(2, results.count { !it })
            postgres.createConnection("").use { connection ->
                assertEquals(2L, activeQuantity(connection, grantId))
            }
        }
        finally
        {
            postgres.stop()
        }
    }

    @Test
    fun `releasing a reservation frees capacity for a later reservation to reuse`()
    {
        val postgres = RequestExecutionUsageReservationPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_execution_usage_reservation_release")
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

            val fixture = postgres.createConnection("").use { Fixture(it) }
            val grantId = UUID.randomUUID()
            postgres.createConnection("").use { connection ->
                insertGrant(connection, grantId, fixture.requestId, fixture.organizationId, cap = 1L)

                connection.autoCommit = false
                assertEquals(true, reserveUnderGrantLock(connection, grantId, "caller-1", quantity = 1L, cap = 1L))
                connection.commit()

                connection.autoCommit = false
                assertEquals(false, reserveUnderGrantLock(connection, grantId, "caller-2", quantity = 1L, cap = 1L))
                connection.commit()

                releaseReservation(connection, grantId, "caller-1")

                connection.autoCommit = false
                assertEquals(true, reserveUnderGrantLock(connection, grantId, "caller-2", quantity = 1L, cap = 1L))
                connection.commit()

                assertEquals(1L, activeQuantity(connection, grantId))
            }
        }
        finally
        {
            postgres.stop()
        }
    }

    /**
     * Mirrors [com.docuhyphen.app.api.service.informationrequest.InformationRequestExecutionUsageReservationService.reserve]'s
     * lock-then-check-then-insert sequence directly in SQL so the pessimistic lock's actual
     * serialization can be exercised by real concurrent transactions, something a mocked
     * repository cannot prove.
     */
    private fun reserveUnderGrantLock(
        connection: Connection,
        grantId: UUID,
        reservationKey: String,
        quantity: Long,
        cap: Long,
    ): Boolean
    {
        connection.prepareStatement("SELECT id FROM request_execution_grant WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, grantId)
            statement.executeQuery().use { result -> check(result.next()) }
        }
        if (activeQuantity(connection, grantId) + quantity > cap)
        {
            return false
        }
        connection.prepareStatement(
            """
            INSERT INTO request_execution_usage_reservation
                (id, grant_id, usage_kind, reservation_key, quantity, status, reserved_at, created_at)
            VALUES (?, ?, 'ADDITIONAL_RECIPIENT', ?, ?, 'RESERVED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, grantId)
            statement.setString(3, reservationKey)
            statement.setLong(4, quantity)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
        return true
    }

    private fun releaseReservation(connection: Connection, grantId: UUID, reservationKey: String)
    {
        connection.prepareStatement(
            """
            UPDATE request_execution_usage_reservation
            SET status = 'RELEASED', released_at = ?
            WHERE grant_id = ? AND reservation_key = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(Instant.now()))
            statement.setObject(2, grantId)
            statement.setString(3, reservationKey)
            statement.executeUpdate()
        }
    }

    private fun activeQuantity(connection: Connection, grantId: UUID): Long =
        connection.prepareStatement(
            """
            SELECT COALESCE(SUM(quantity), 0)
            FROM request_execution_usage_reservation
            WHERE grant_id = ? AND status IN ('RESERVED', 'CONSUMED')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, grantId)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    private fun insertGrant(
        connection: Connection,
        grantId: UUID,
        requestId: UUID,
        organizationId: UUID,
        cap: Long,
    )
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                 enforcement_mode, additional_recipient_cap, issued_at, created_at)
            VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, grantId)
            statement.setObject(2, requestId)
            statement.setObject(3, organizationId)
            statement.setLong(4, cap)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
    }

    private class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val userId: UUID = UUID.randomUUID()
        val exchangeId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val versionId: UUID = UUID.randomUUID()
        val sectionId: UUID = UUID.randomUUID()
        val templateRequirementId: UUID = UUID.randomUUID()
        val templateBindingId: UUID = UUID.randomUUID()
        val requestId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertUser(connection, userId)
            insertExchange(connection, exchangeId, organizationId, userId)
            insertDefinition(connection, definitionId, organizationId)
            insertVersion(connection, versionId, definitionId)
            insertTemplateSection(connection, sectionId, versionId)
            insertTemplateRequirement(connection, templateRequirementId, definitionId)
            insertTemplateBinding(connection, templateBindingId, versionId, definitionId, templateRequirementId, sectionId)
            publishVersion(connection, versionId, userId)
            insertRequest(connection, requestId, exchangeId, versionId, organizationId)
        }
    }

    private companion object
    {
        fun insertOrganization(connection: Connection, id: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO organization
                    (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Execution usage reservation organization', ?, TRUE, TRUE, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setString(2, "REG-${id.toString().take(8)}")
                statement.setTimestamp(3, Timestamp.from(Instant.now()))
                statement.executeUpdate()
            }
        }

        fun insertUser(connection: Connection, id: UUID)
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
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "reservation-owner-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }

        fun insertExchange(connection: Connection, id: UUID, organizationId: UUID, userId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO exchange
                    (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                     created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, ?, ?, 'Collect process records', 'Please respond',
                        'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, organizationId)
                statement.setObject(3, userId)
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }

        fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_definition
                    (id, scope_kind, scope_org_id, namespace, template_key, display_name,
                     status, created_at, updated_at)
                VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'Collection pattern',
                        'PUBLISHED', ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, organizationId)
                statement.setString(3, "collection-${id.toString().take(8)}")
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, now)
                statement.executeUpdate()
            }
        }

        fun insertVersion(connection: Connection, id: UUID, definitionId: UUID)
        {
            val now = Timestamp.from(Instant.now())
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_version
                    (id, template_definition_id, version_number, status, created_at)
                VALUES (?, ?, 1, 'DRAFT', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, definitionId)
                statement.setTimestamp(3, now)
                statement.executeUpdate()
            }
        }

        fun insertTemplateSection(connection: Connection, id: UUID, versionId: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_section
                    (id, template_version_id, section_key, display_order, title)
                VALUES (?, ?, 'records', 1, 'Records')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, versionId)
                statement.executeUpdate()
            }
        }

        fun insertTemplateRequirement(connection: Connection, id: UUID, definitionId: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_requirement
                    (id, template_definition_id, requirement_key, requirement_type, created_at)
                VALUES (?, ?, 'recorded-assertion', 'RESPONSE_ATTESTATION', ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, definitionId)
                statement.setTimestamp(3, Timestamp.from(Instant.now()))
                statement.executeUpdate()
            }
        }

        fun insertTemplateBinding(
            connection: Connection,
            id: UUID,
            versionId: UUID,
            definitionId: UUID,
            requirementId: UUID,
            sectionId: UUID,
        )
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_requirement_binding
                    (id, template_version_id, template_definition_id, template_requirement_id,
                     template_section_id, display_order, prompt, response_mode, requiredness,
                     contributor_role, review_policy)
                VALUES (?, ?, ?, ?, ?, 1, 'State the response', 'PROVIDE', 'REQUIRED',
                        'CONTRIBUTOR', 'NOT_REQUIRED')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, versionId)
                statement.setObject(3, definitionId)
                statement.setObject(4, requirementId)
                statement.setObject(5, sectionId)
                statement.executeUpdate()
            }
        }

        fun publishVersion(connection: Connection, versionId: UUID, actorId: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request_template_version_capability
                    (id, template_version_id, capability_key, required_contract_version)
                SELECT gen_random_uuid(), version.id, required.capability_key, 1
                FROM information_request_template_version version
                         CROSS JOIN request_template_required_capabilities(version.id) required
                WHERE version.id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, versionId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                UPDATE information_request_template_version
                SET status = 'PUBLISHED', published_at = ?, published_by_app_user_id = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(Instant.now()))
                statement.setObject(2, actorId)
                statement.setObject(3, versionId)
                statement.executeUpdate()
            }
        }

        fun insertRequest(connection: Connection, id: UUID, exchangeId: UUID, versionId: UUID, ownerId: UUID)
        {
            connection.prepareStatement(
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id,
                     state, gates_exchange_closure, aggregate_revision, party_revision,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setObject(1, id)
                statement.setObject(2, exchangeId)
                statement.setObject(3, versionId)
                statement.setObject(4, ownerId)
                statement.setTimestamp(5, now)
                statement.setTimestamp(6, now)
                statement.executeUpdate()
            }
        }
    }
}
