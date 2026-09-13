package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.SQLException

class RequestAccessSessionCredentialContractTest
{
    @Test
    fun `clean schema supports hashed session credentials`()
    {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration").load().migrate()
            postgres.createConnection("").use { connection ->
                connection.metaData.getColumns(null, null, "request_access_session", "credential_hash").use {
                    assertTrue(it.next(), "Session credentials must have a persisted hash")
                }
            }
        }
    }

    @Test
    fun `legacy sessions are revoked and old writers cannot create content capable sessions`()
    {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").use { postgres ->
            postgres.start()
            postgres.createConnection("").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE share_link (id UUID PRIMARY KEY)")
                    statement.execute(javaClass.getResource("/db/migration/V104__request_access_session.sql")!!.readText())
                    statement.execute("INSERT INTO share_link VALUES ('00000000-0000-0000-0000-000000000001')")
                    val insert = """
                        INSERT INTO request_access_session
                            (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength, issued_at)
                        VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
                                'PARTICIPANT', gen_random_uuid(), 'EMAIL_OTP', now())
                    """.trimIndent()
                    statement.execute(insert)
                    javaClass.getResource("/db/migration/V117__request_access_session_credential.sql")?.let {
                        statement.execute(it.readText())
                    }
                    statement.executeQuery("SELECT revoked_at IS NOT NULL AS revoked FROM request_access_session").use {
                        it.next()
                        assertTrue(it.getBoolean("revoked"), "Legacy token-only sessions must be revoked")
                    }
                    statement.execute(insert)
                    statement.executeQuery("SELECT count(*) FROM request_access_session WHERE revoked_at IS NULL").use {
                        it.next()
                        assertEquals(0, it.getInt(1))
                    }
                    assertThrows<SQLException> {
                        statement.execute("UPDATE request_access_session SET credential_hash = repeat('a', 64), revoked_at = NULL")
                    }
                    val credentialInsert = """
                        INSERT INTO request_access_session
                            (id, share_link_id, participant_principal_kind, participant_principal_id,
                             verification_strength, issued_at, expires_at, credential_hash)
                        VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001',
                                'PARTICIPANT', gen_random_uuid(), 'EMAIL_OTP', now(), now() + interval '1 hour', repeat('b', 64))
                    """.trimIndent()
                    statement.execute(credentialInsert)
                    statement.executeQuery("SELECT count(*) FROM request_access_session WHERE revoked_at IS NULL").use {
                        it.next()
                        assertEquals(1, it.getInt(1))
                    }
                    assertThrows<SQLException> { statement.execute(credentialInsert) }
                    assertThrows<SQLException> {
                        statement.execute("UPDATE request_access_session SET expires_at = NULL WHERE credential_hash IS NOT NULL")
                    }
                    assertThrows<SQLException> {
                        statement.execute("UPDATE request_access_session SET participant_principal_id = gen_random_uuid() WHERE credential_hash IS NOT NULL")
                    }
                    statement.execute("UPDATE request_access_session SET use_count = use_count + 1, revoked_at = now() WHERE credential_hash IS NOT NULL")
                }
            }
        }
    }
}
