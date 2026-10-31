package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeHiddenIdentifierHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `rescinding an Exchange the caller cannot see is refused exactly as rescinding one that does not exist`() {
        val hidden = exchange()
        val outsider = InformationRequestSignedInCaller.signIn(appUsers, insertUser("rescind-outsider"))

        val hiddenRefusal = outsider.authorized().contentType(ContentType.JSON).post("/exchanges/$hidden/rescind")
        val unknownRefusal =
            outsider.authorized().contentType(ContentType.JSON).post("/exchanges/${UUID.randomUUID()}/rescind")

        assertSameRefusal(hiddenRefusal, unknownRefusal)
        assertEquals("INITIATED", statusOf(hidden))
    }

    @Test
    fun `rescinding an Exchange the caller cannot see takes no lock on it`() {
        val hidden = exchange()
        val outsider = InformationRequestSignedInCaller.signIn(appUsers, insertUser("rescind-waiter"))

        val refusal = answeredWhileExchangeLocked(hidden) {
            outsider.authorized().contentType(ContentType.JSON).post("/exchanges/$hidden/rescind")
        }

        assertNotNull(refusal, "The refusal waited for a lock held on the hidden Exchange")
        assertEquals(404, refusal!!.statusCode, refusal.asString())
    }

    @Test
    fun `a no-auth status change with a wrong access token is refused without waiting for the Exchange lock`() {
        val exchangeId = exchange()

        val refusal = answeredWhileExchangeLocked(exchangeId) {
            given()
                .header("x-no-auth-access-token", "not-the-access-token")
                .contentType(ContentType.JSON)
                .body("""{"status":"REJECTED","otp":"000000"}""")
                .put("/no-auth/exchanges/$exchangeId")
        }

        assertNotNull(refusal, "The refusal waited for a lock held on the Exchange")
        assertEquals(404, refusal!!.statusCode, refusal.asString())
    }

    @Test
    fun `document versions of an Exchange the caller cannot see are refused exactly as those of an unknown Exchange`() {
        val hidden = exchange()
        val document = UUID.randomUUID()
        val outsider = InformationRequestSignedInCaller.signIn(appUsers, insertUser("version-outsider"))

        assertSameRefusal(
            outsider.authorized().get("/exchanges/$hidden/documents/$document/versions"),
            outsider.authorized().get("/exchanges/${UUID.randomUUID()}/documents/$document/versions"),
        )
    }

    private fun assertSameRefusal(hidden: Response, unknown: Response) {
        assertAll(
            { assertEquals(404, unknown.statusCode, unknown.asString()) },
            { assertEquals(unknown.statusCode, hidden.statusCode, hidden.asString()) },
            { assertEquals(messageOf(unknown), messageOf(hidden), hidden.asString()) },
        )
    }

    private fun messageOf(response: Response): String? =
        runCatching { response.jsonPath().getString("errorMessage") }.getOrNull()

    private fun answeredWhileExchangeLocked(exchangeId: UUID, send: () -> Response): Response? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                queryString(connection, "SELECT CAST(id AS text) FROM exchange WHERE id = ? FOR UPDATE", exchangeId)
                val pending = CompletableFuture.supplyAsync(send)
                return runCatching { pending.get(LOCK_WAIT_SECONDS, TimeUnit.SECONDS) }.getOrNull()
            } finally {
                connection.rollback()
            }
        }
    }

    private fun statusOf(exchangeId: UUID): String? = dataSource.connection.use { connection ->
        queryString(connection, "SELECT status FROM exchange WHERE id = ?", exchangeId)
    }

    private fun exchange(): UUID {
        val organizationId = UUID.randomUUID()
        val ownerId = insertUser("exchange-owner")
        val exchangeId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process owner organization', ?, TRUE, TRUE, now())
                """.trimIndent(),
                organizationId,
                "REG-${organizationId.toString().take(8)}",
            )
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_organization_id, initiator_id, is_deleted, require_recipient_sign_in,
                     created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process records', 'Please respond',
                        'Process collection', 'INITIATED')
                """.trimIndent(),
                exchangeId,
                organizationId,
                ownerId,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                exchangeId,
                ownerId,
            )
        }
        return exchangeId
    }

    private fun insertUser(label: String): UUID {
        val userId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                userId,
                "$label-${userId.toString().take(8)}@process.test",
            )
        }
        return userId
    }

    private companion object {
        const val LOCK_WAIT_SECONDS = 5L
    }
}
