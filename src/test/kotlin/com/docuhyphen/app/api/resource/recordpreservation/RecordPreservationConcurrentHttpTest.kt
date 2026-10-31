package com.docuhyphen.app.api.resource.recordpreservation

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.resource.recordpreservation.model.PlacedHold
import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.arc.Arc
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RecordPreservationConcurrentHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `two requests racing on one read version of a hold apply once and answer the other 412`() {
        val caller = signedIn()
        val hold = place(caller, "http-place-stale")

        val outcomes = whileHoldLocked(hold.id, waiters = 2) {
            listOf(
                CompletableFuture.supplyAsync {
                    caller.request("http-scope", hold.etag)
                        .body("""{"scope":"DESCENDANTS_AND_REFERENCES","reason":"widened"}""")
                        .patch("/record-preservation-holds/${hold.id}/scope")
                },
                CompletableFuture.supplyAsync {
                    caller.request("http-release", hold.etag)
                        .body("""{"reason":"review closed"}""")
                        .post("/record-preservation-holds/${hold.id}/release")
                },
            )
        }

        assertEquals(listOf(200, 412), outcomes.map { it.statusCode }.sorted(), outcomes.joinToString { it.asString() })
        val refused = outcomes.single { it.statusCode == 412 }
        assertEquals("COMMAND_PRECONDITION_STALE", refused.jsonPath().getString("reasonCode"))
        assertEquals(caller.hold(hold.id).header("ETag"), refused.header("ETag"))
    }

    @Test
    fun `a retried request racing with its original over HTTP replays it`() {
        val caller = signedIn()
        val hold = place(caller, "http-place-retry")
        val retry = {
            caller.request("http-retried-scope", hold.etag)
                .body("""{"scope":"DESCENDANTS_AND_REFERENCES","reason":"widened"}""")
                .patch("/record-preservation-holds/${hold.id}/scope")
        }

        val outcomes = whileHoldLocked(hold.id, waiters = 2) {
            listOf(CompletableFuture.supplyAsync(retry), CompletableFuture.supplyAsync(retry))
        }

        assertEquals(listOf(200, 200), outcomes.map { it.statusCode }, outcomes.joinToString { it.asString() })
        assertEquals(1, outcomes.map { it.header("ETag") }.distinct().size)
        assertEquals(
            1,
            rows(
                "SELECT count(*) FROM audit_legal_hold_event WHERE hold_id = ? AND event_kind = 'SCOPE_CHANGED'",
                hold.id
            ),
        )
    }

    @Test
    fun `two schedule publications racing on one read version publish once and answer the other 412`() {
        val caller = signedIn()
        val read = caller.authorized().get("/record-retention-schedules/INFORMATION_REQUEST")
        assertEquals(200, read.statusCode, read.asString())
        val etag = read.header("ETag")

        val outcomes = whileSchedulesLocked(waiters = 2) {
            listOf(
                CompletableFuture.supplyAsync {
                    caller.request("http-publish-first", etag)
                        .body("""{"minimumRetentionDays":30,"disposalAfterDays":90}""")
                        .put("/record-retention-schedules/INFORMATION_REQUEST")
                },
                CompletableFuture.supplyAsync {
                    caller.request("http-publish-second", etag)
                        .body("""{"minimumRetentionDays":60,"disposalAfterDays":120}""")
                        .put("/record-retention-schedules/INFORMATION_REQUEST")
                },
            )
        }

        assertEquals(listOf(200, 412), outcomes.map { it.statusCode }.sorted(), outcomes.joinToString { it.asString() })
        assertEquals(
            1,
            rows(
                "SELECT count(*) FROM record_retention_schedule WHERE owner_kind = 'USER' AND owner_id = ?",
                caller.userId
            ),
        )
    }

    @Test
    fun `an oversized hold reason is a malformed HTTP request`() {
        val caller = signedIn()
        val hold = place(caller, "place-before-long-reason")
        val response = caller.request("long-hold-reason", hold.etag)
            .body(mapOf("scope" to "DESCENDANTS_AND_REFERENCES", "reason" to "a".repeat(2049)))
            .patch("/record-preservation-holds/${hold.id}/scope")
        assertEquals(400, response.statusCode, response.asString())
    }

    @Test
    fun `a case reference over 256 characters is a malformed HTTP request and places nothing`() {
        val caller = signedIn()
        fun placeWith(caseReference: String, key: String) = caller.authorized()
            .header("Idempotency-Key", key)
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "resourceType" to "APP_USER",
                    "resourceId" to caller.userId.toString(),
                    "scope" to "RESOURCE",
                    "reason" to "records kept for a review",
                    "caseReference" to caseReference,
                )
            )
            .post("/record-preservation-holds")

        val refused = placeWith("c".repeat(257), "long-case-reference")
        val accepted = placeWith("c".repeat(256), "bounded-case-reference")

        assertEquals(400, refused.statusCode, refused.asString())
        assertEquals(201, accepted.statusCode, accepted.asString())
        assertEquals(
            1,
            rows(
                "SELECT count(*) FROM audit_legal_hold WHERE resource_type = 'APP_USER' AND resource_id = CAST(? AS text)",
                caller.userId
            ),
        )
    }

    private fun place(caller: SignedInCaller, key: String): PlacedHold {
        val placed = caller.authorized()
            .header("Idempotency-Key", key)
            .contentType(ContentType.JSON)
            .body("""{"resourceType":"APP_USER","resourceId":"${caller.userId}","scope":"RESOURCE","reason":"records kept for a review"}""")
            .post("/record-preservation-holds")
        assertEquals(201, placed.statusCode, placed.asString())
        return PlacedHold(UUID.fromString(placed.jsonPath().getString("id")), placed.header("ETag"))
    }

    private fun whileHoldLocked(
        holdId: UUID,
        waiters: Int,
        start: () -> List<CompletableFuture<Response>>
    ): List<Response> =
        whileLocked(waiters, start) { connection ->
            queryString(connection, "SELECT CAST(id AS text) FROM audit_legal_hold WHERE id = ? FOR UPDATE", holdId)
        }

    private fun whileSchedulesLocked(waiters: Int, start: () -> List<CompletableFuture<Response>>): List<Response> =
        whileLocked(waiters, start) { connection ->
            execute(connection, "LOCK TABLE record_retention_schedule IN SHARE ROW EXCLUSIVE MODE")
        }

    private fun whileLocked(
        waiters: Int,
        start: () -> List<CompletableFuture<Response>>,
        lock: (Connection) -> Unit,
    ): List<Response> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            lock(connection)
            val pending = start()
            awaitLockWaiters(waiters, pending)
            connection.commit()
            return pending.map { it.get(60, TimeUnit.SECONDS) }
        }
    }

    private fun awaitLockWaiters(expected: Int, pending: List<CompletableFuture<Response>>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (pending.none { it.isDone } && System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting >= expected) return
            Thread.sleep(25)
        }
    }

    private fun rows(sql: String, id: UUID): Int =
        dataSource.connection.use { connection -> queryInt(connection, sql, id) }

    private fun signedIn(): SignedInCaller {
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
                "records-${userId.toString().take(8)}@process.test",
            )
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                userId,
            )
        }
        val context = Arc.container().requestContext()
        context.activate()
        val accessToken = try {
            val user = requireNotNull(appUsers.getById(userId))
            Arc.container().instance(TokenIssuanceService::class.java).get().issueTokenTriple(user).accessToken
        } finally {
            context.terminate()
        }
        return SignedInCaller(userId, accessToken)
    }

    private class SignedInCaller(val userId: UUID, private val accessToken: String) {
        fun authorized() = given().header("Authorization", "Bearer $accessToken")

        fun request(idempotencyKey: String, ifMatch: String) =
            authorized()
                .header("Idempotency-Key", idempotencyKey)
                .header("If-Match", ifMatch)
                .contentType(ContentType.JSON)

        fun hold(holdId: UUID): Response = authorized().get("/record-preservation-holds/$holdId")
    }
}
