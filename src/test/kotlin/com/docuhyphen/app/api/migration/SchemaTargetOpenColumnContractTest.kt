package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class SchemaTargetPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<SchemaTargetPostgreSQLContainer>(imageName)

class SchemaTargetOpenColumnContractTest
{
    @Test
    fun `nothing in the database narrows which resource a schema may be written for`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val constraints = checkConstraintsOn(connection, "schema_definition", "target_resource_type")
                assertTrue(
                    constraints.isEmpty(),
                    "The column is expected to constrain nothing, but found: $constraints",
                )

                assertEquals("character varying", columnTypeOf(connection, "target_resource_type"))
                assertEquals(48, columnLengthOf(connection, "target_resource_type"))
            }
        }
    }

    @Test
    fun `a schema written for a second kind of resource is stored as written`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val exchangeSchema = UUID.randomUUID()
                val informationRequestSchema = UUID.randomUUID()

                insertPlatformSchema(connection, exchangeSchema, "held-data", "EXCHANGE")
                insertPlatformSchema(connection, informationRequestSchema, "collected-data", "INFORMATION_REQUEST")

                assertEquals("EXCHANGE", targetOf(connection, exchangeSchema))
                assertEquals("INFORMATION_REQUEST", targetOf(connection, informationRequestSchema))
            }
        }
    }

    @Test
    fun `a schema that names no resource is written for the one the column always meant`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val id = UUID.randomUUID()
                insertPlatformSchema(connection, id, "unstated-target", target = null)

                assertEquals("EXCHANGE", targetOf(connection, id))
            }
        }
    }

    private fun withPostgres(block: (SchemaTargetPostgreSQLContainer) -> Unit)
    {
        val postgres = SchemaTargetPostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("docuhyphen_schema_target_test")
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

    private fun flyway(postgres: SchemaTargetPostgreSQLContainer): Flyway =
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()

    private fun checkConstraintsOn(connection: Connection, table: String, column: String): List<String>
    {
        val found = mutableListOf<String>()
        connection.prepareStatement(
            """
            SELECT conname, pg_get_constraintdef(oid) AS definition
            FROM pg_constraint
            WHERE conrelid = ?::regclass AND contype = 'c'
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { rows ->
                while (rows.next())
                {
                    val definition = rows.getString("definition")
                    if (definition.contains(column)) found += "${rows.getString("conname")}: $definition"
                }
            }
        }
        return found
    }

    private fun columnTypeOf(connection: Connection, column: String): String =
        columnAttribute(connection, column, "data_type")!!

    private fun columnLengthOf(connection: Connection, column: String): Int =
        columnAttribute(connection, column, "character_maximum_length")!!.toInt()

    private fun columnAttribute(connection: Connection, column: String, attribute: String): String?
    {
        connection.prepareStatement(
            "SELECT $attribute FROM information_schema.columns " +
                "WHERE table_name = 'schema_definition' AND column_name = ?",
        ).use { statement ->
            statement.setString(1, column)
            statement.executeQuery().use { rows ->
                return if (rows.next()) rows.getString(1) else null
            }
        }
    }

    private fun insertPlatformSchema(
        connection: Connection,
        id: UUID,
        schemaKey: String,
        target: String?,
    )
    {
        val now = Timestamp.from(Instant.now())
        val targetColumn = if (target == null) "" else ", target_resource_type"
        val targetValue = if (target == null) "" else ", ?"

        connection.prepareStatement(
            """
            INSERT INTO schema_definition (id, scope_kind, namespace, schema_key, display_name, status,
                                           created_at, updated_at$targetColumn)
            VALUES (?, 'PLATFORM', 'process', ?, 'Process schema', 'PUBLISHED', ?, ?$targetValue)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, schemaKey)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            target?.let { statement.setString(5, it) }
            statement.executeUpdate()
        }
    }

    private fun targetOf(connection: Connection, id: UUID): String
    {
        connection.prepareStatement(
            "SELECT target_resource_type FROM schema_definition WHERE id = ?",
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "No schema stored for $id" }
                return rows.getString(1)
            }
        }
    }
}
