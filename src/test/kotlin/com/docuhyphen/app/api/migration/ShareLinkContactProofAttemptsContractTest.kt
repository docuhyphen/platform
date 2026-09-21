package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.SQLException

class ShareLinkContactProofAttemptsContractTest
{
    @Test
    fun `clean schema persists contact proof attempt limits on bootstrap links`()
    {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration").load().migrate()
            postgres.createConnection("").use { connection ->
                connection.metaData.getColumns(null, null, "share_link", "contact_otp_failed_attempts").use {
                    assertTrue(it.next())
                }
                connection.metaData.getColumns(null, null, "share_link", "contact_otp_locked_until").use {
                    assertTrue(it.next())
                }
                connection.metaData.getColumns(null, null, "share_link", "contact_otp_challenge_count").use {
                    assertTrue(it.next())
                }
            }
        }
    }

    @Test
    fun `attempt counter defaults to zero and rejects negative values`()
    {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            postgres.createConnection("").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE share_link (id UUID PRIMARY KEY)")
                    statement.execute(javaClass.getResource("/db/migration/V118__share_link_contact_proof_attempts.sql")!!.readText())
                    statement.execute(
                        "INSERT INTO share_link (id) VALUES ('00000000-0000-0000-0000-000000000001')",
                    )
                    statement.executeQuery("SELECT contact_otp_failed_attempts FROM share_link").use {
                        it.next()
                        assertEquals(0, it.getInt(1))
                    }
                    assertThrows<SQLException> {
                        statement.execute(
                            """
                            UPDATE share_link
                            SET contact_otp_failed_attempts = -1
                            WHERE id = '00000000-0000-0000-0000-000000000001'
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }
}
