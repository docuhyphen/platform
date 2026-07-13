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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

private class AuditPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<AuditPostgreSQLContainer>(imageName)

class AuditConcurrencyPostgresContractTest
{
    @Test
    fun `database serialization protects approvals downloads and build leases across connections`()
    {
        val postgres = AuditPostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_audit_concurrency")
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

            val approvalExportId = insertExport(postgres, "APPROVAL_PENDING", downloadLimit = 1)
            val approverId = UUID.randomUUID()
            val approvalResults = runConcurrently { _ ->
                postgres.createConnection("").use { connection ->
                    runCatching {
                        connection.prepareStatement(
                            "INSERT INTO audit_export_approval " +
                                "(id, export_id, approved_by_user_id, approved_at) VALUES (?, ?, ?, now())",
                        ).use { statement ->
                            statement.setObject(1, UUID.randomUUID())
                            statement.setObject(2, approvalExportId)
                            statement.setObject(3, approverId)
                            statement.executeUpdate()
                        }
                    }.isSuccess
                }
            }
            assertEquals(listOf(false, true), approvalResults.sorted())

            val downloadExportId = insertExport(postgres, "READY", downloadLimit = 1)
            val downloadResults = runConcurrently { _ ->
                postgres.createConnection("").use { connection ->
                    connection.autoCommit = false
                    val allowed = lockAndConsumeDownload(connection, downloadExportId)
                    connection.commit()
                    allowed
                }
            }
            assertEquals(listOf(false, true), downloadResults.sorted())
            assertEquals(1, readInt(postgres, downloadExportId, "download_count"))

            val leaseExportId = insertExport(postgres, "BUILDING", downloadLimit = 1)
            val leaseResults = runConcurrently { workerIndex ->
                postgres.createConnection("").use { connection ->
                    connection.autoCommit = false
                    val claimed = lockAndClaim(connection, leaseExportId, "worker-$workerIndex")
                    connection.commit()
                    claimed
                }
            }
            assertEquals(listOf(false, true), leaseResults.sorted())
            assertTrue(readString(postgres, leaseExportId, "build_worker_id")!!.startsWith("worker-"))
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun insertExport(postgres: AuditPostgreSQLContainer, status: String, downloadLimit: Int): UUID
    {
        val id = UUID.randomUUID()
        postgres.createConnection("").use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO audit_export
                    (id, requested_by_user_id, requested_at, categories_csv, occurred_after,
                     occurred_before, purpose, status, download_limit, created_at, updated_at)
                VALUES (?, ?, now(), 'SECURITY', ?, ?, 'concurrency contract', ?, ?, now(), now())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, UUID.randomUUID())
                statement.setTimestamp(3, Timestamp.from(Instant.now().minusSeconds(3600)))
                statement.setTimestamp(4, Timestamp.from(Instant.now()))
                statement.setString(5, status)
                statement.setInt(6, downloadLimit)
                statement.executeUpdate()
            }
        }
        return id
    }

    private fun lockAndConsumeDownload(connection: Connection, exportId: UUID): Boolean
    {
        val countAndLimit = connection.prepareStatement(
            "SELECT download_count, download_limit FROM audit_export WHERE id = ? FOR UPDATE",
        ).use { statement ->
            statement.setObject(1, exportId)
            statement.executeQuery().use { result ->
                result.next()
                result.getInt(1) to result.getInt(2)
            }
        }
        if (countAndLimit.first >= countAndLimit.second) return false
        connection.prepareStatement("UPDATE audit_export SET download_count = download_count + 1 WHERE id = ?")
            .use { statement -> statement.setObject(1, exportId); statement.executeUpdate() }
        return true
    }

    private fun lockAndClaim(connection: Connection, exportId: UUID, workerId: String): Boolean
    {
        val existingExpiry = connection.prepareStatement(
            "SELECT build_lease_expires_at FROM audit_export WHERE id = ? FOR UPDATE",
        ).use { statement ->
            statement.setObject(1, exportId)
            statement.executeQuery().use { result -> result.next(); result.getTimestamp(1) }
        }
        if (existingExpiry?.toInstant()?.isAfter(Instant.now()) == true) return false
        connection.prepareStatement(
            "UPDATE audit_export SET build_worker_id = ?, build_lease_expires_at = ? WHERE id = ?",
        ).use { statement ->
            statement.setString(1, workerId)
            statement.setTimestamp(2, Timestamp.from(Instant.now().plusSeconds(300)))
            statement.setObject(3, exportId)
            statement.executeUpdate()
        }
        return true
    }

    private fun <T> runConcurrently(operation: (Int) -> T): List<T>
    {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        return try
        {
            val futures = (1..2).map { index ->
                executor.submit(Callable { start.await(); operation(index) })
            }
            start.countDown()
            futures.map { it.get() }
        }
        finally
        {
            executor.shutdownNow()
        }
    }

    private fun readInt(postgres: AuditPostgreSQLContainer, exportId: UUID, column: String): Int =
        postgres.createConnection("").use { connection ->
            connection.prepareStatement("SELECT $column FROM audit_export WHERE id = ?").use { statement ->
                statement.setObject(1, exportId)
                statement.executeQuery().use { result -> result.next(); result.getInt(1) }
            }
        }

    private fun readString(postgres: AuditPostgreSQLContainer, exportId: UUID, column: String): String? =
        postgres.createConnection("").use { connection ->
            connection.prepareStatement("SELECT $column FROM audit_export WHERE id = ?").use { statement ->
                statement.setObject(1, exportId)
                statement.executeQuery().use { result -> result.next(); result.getString(1) }
            }
        }
}
