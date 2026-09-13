package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class ConditionPolicyPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<ConditionPolicyPostgreSQLContainer>(imageName)

class InformationRequestConditionHiddenDataPolicyContractTest
{
    private val previousVersion = "114"

    @Test
    fun `a condition rule records hidden response data policy and upgrades existing rows to retain securely`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = previousVersion).migrate()

            val ruleId: UUID
            postgres.createConnection("").use { connection ->
                val fixture = Fixture(connection)
                ruleId = fixture.ruleId
                insertConditionRule(connection, ruleId, fixture.versionId)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("RETAIN_SECURELY", hiddenDataPolicy(connection, ruleId))

                updateHiddenDataPolicy(connection, ruleId, "CLEAR_WITH_CONFIRMATION")
                assertEquals("CLEAR_WITH_CONFIRMATION", hiddenDataPolicy(connection, ruleId))

                updateHiddenDataPolicy(connection, ruleId, "ARCHIVE_OUTSIDE_ACTIVE_RESPONSE")
                assertEquals("ARCHIVE_OUTSIDE_ACTIVE_RESPONSE", hiddenDataPolicy(connection, ruleId))

                refused(connection, "ck_request_template_condition_rule_hidden_data_policy") {
                    updateHiddenDataPolicy(connection, ruleId, "DELETE_ACTIVE_RESPONSE")
                }
            }
        }
    }

    private inner class Fixture(connection: Connection)
    {
        val organizationId: UUID = UUID.randomUUID()
        val definitionId: UUID = UUID.randomUUID()
        val versionId: UUID = UUID.randomUUID()
        val ruleId: UUID = UUID.randomUUID()

        init
        {
            insertOrganization(connection, organizationId)
            insertDefinition(connection, definitionId, organizationId)
            insertVersion(connection, versionId, definitionId)
        }
    }

    private fun withPostgres(block: (ConditionPolicyPostgreSQLContainer) -> Unit)
    {
        ConditionPolicyPostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()
            block(postgres)
        }
    }

    private fun flyway(postgres: ConditionPolicyPostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertOrganization(connection: Connection, id: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO organization
                (id, name, registration_number, is_active, verification_complete, created_date)
            VALUES (?, 'Process Owner', ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, "REG-${id.toString().take(8)}")
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertDefinition(connection: Connection, id: UUID, organizationId: UUID)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_definition
                (id, scope_kind, scope_org_id, namespace, template_key, display_name, status,
                 created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, ?, 'collection-pattern', 'Collection pattern', 'DRAFT', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, "process-${id.toString().take(8)}")
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertVersion(connection: Connection, id: UUID, definitionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_version
                (id, template_definition_id, version_number, status, created_at)
            VALUES (?, ?, 1, 'DRAFT', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertConditionRule(connection: Connection, id: UUID, versionId: UUID)
    {
        connection.prepareStatement(
            """
            INSERT INTO information_request_template_condition_rule
                (id, template_version_id, rule_key, expression_version)
            VALUES (?, ?, 'when-recorded-note-applies', 1)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, versionId)
            statement.executeUpdate()
        }
    }

    private fun hiddenDataPolicy(connection: Connection, id: UUID): String =
        connection.prepareStatement(
            "SELECT hidden_data_policy FROM information_request_template_condition_rule WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }

    private fun updateHiddenDataPolicy(connection: Connection, id: UUID, policy: String)
    {
        connection.prepareStatement(
            "UPDATE information_request_template_condition_rule SET hidden_data_policy = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, policy)
            statement.setObject(2, id)
            statement.executeUpdate()
        }
    }

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
            "Expected $expected to refuse this statement: ${refusal.message}",
        )
    }
}
