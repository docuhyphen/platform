package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class CommandIdempotencyKeyLengthHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = [129, 256, 300])
    fun `an Idempotency-Key longer than 128 characters is refused as a malformed command on every route`(length: Int) {
        val caller = signedIn()
        val key = "k".repeat(length)

        assertAll(
            routes().map { route ->
                Executable {
                    val response = route.send(caller, key)
                    assertEquals(400, response.statusCode, "${route.name}: ${response.asString()}")
                    assertEquals(KEY_INVALID, reasonCodeOf(response), "${route.name}: ${response.asString()}")
                }
            },
        )
    }

    @Test
    fun `an Idempotency-Key of exactly 128 characters passes the length check on every route`() {
        val caller = signedIn()
        val key = "k".repeat(128)

        assertAll(
            routes().map { route ->
                Executable {
                    val response = route.send(caller, key)
                    assertNotEquals(KEY_INVALID, reasonCodeOf(response), "${route.name}: ${response.asString()}")
                }
            },
        )
    }

    private fun routes(): List<KeyedRoute> {
        val request = UUID.randomUUID()
        val item = UUID.randomUUID()
        val requestPath = "/information-requests/$request"
        val evidence = "$requestPath/requirements/$item/evidence-artifacts"
        return listOf(
            KeyedRoute("response patch") { caller, key ->
                caller.command(key).body("""{"patches":[]}""").patch("$requestPath/responses")
            },
            KeyedRoute("group occurrence addition") { caller, key ->
                caller.command(key).body("""{"groupKey":"records"}""").post("$requestPath/group-occurrences")
            },
            KeyedRoute("group occurrence removal") { caller, key ->
                caller.command(key).delete("$requestPath/group-occurrences/$item")
            },
            KeyedRoute("group occurrence order") { caller, key ->
                caller.command(key).body("""{"groupKey":"records","occurrenceIds":[]}""")
                    .patch("$requestPath/group-occurrences/order")
            },
            KeyedRoute("access link issue") { caller, key ->
                caller.command(key).body("""{"partyId":"$item"}""").post("$requestPath/access-links")
            },
            KeyedRoute("access link rotation") { caller, key ->
                caller.command(key).post("$requestPath/access-links/$item/rotation")
            },
            KeyedRoute("access link replacement") { caller, key ->
                caller.command(key).body("{}").post("$requestPath/access-links/$item/replacement")
            },
            KeyedRoute("access link revocation") { caller, key ->
                caller.command(key).post("$requestPath/access-links/$item/revocation")
            },
            KeyedRoute("participant account link") { caller, key ->
                caller.command(key).body("""{"sessionId":"$item"}""").post("$requestPath/participant-account-links")
            },
            KeyedRoute("delegated authority grant") { caller, key ->
                caller.command(key)
                    .body("""{"assignedPartyId":"$item","delegatePrincipalKind":"USER","delegatePrincipalId":"$item"}""")
                    .post("$requestPath/delegated-authorities")
            },
            KeyedRoute("delegated authority revocation") { caller, key ->
                caller.command(key).body("{}").post("$requestPath/delegated-authorities/$item/revocations")
            },
            KeyedRoute("evidence upload") { caller, key ->
                caller.authorized().header(KEY_HEADER, key)
                    .multiPart("file", recordFile(), "application/pdf")
                    .post(evidence)
            },
            KeyedRoute("evidence replacement") { caller, key ->
                caller.authorized().header(KEY_HEADER, key)
                    .multiPart("file", recordFile(), "application/pdf")
                    .post("$evidence/$item/versions")
            },
            KeyedRoute("evidence withdrawal") { caller, key ->
                caller.command(key).body("{}").post("$evidence/$item/withdrawals")
            },
            KeyedRoute("evidence removal") { caller, key ->
                caller.command(key).body("{}").post("$evidence/$item/removals")
            },
            KeyedRoute("record preservation hold") { caller, key ->
                caller.command(key)
                    .body("""{"resourceType":"INFORMATION_REQUEST","resourceId":"$request","reason":"Keep the process record"}""")
                    .post("/record-preservation-holds")
            },
            KeyedRoute("record preservation scope") { caller, key ->
                caller.command(key).body("""{"scope":"RESOURCE","reason":"Keep only the record"}""")
                    .patch("/record-preservation-holds/$item/scope")
            },
            KeyedRoute("record preservation release") { caller, key ->
                caller.command(key).body("""{"reason":"The process closed"}""")
                    .post("/record-preservation-holds/$item/release")
            },
            KeyedRoute("record retention schedule") { caller, key ->
                caller.command(key).body("""{"minimumRetentionDays":30}""")
                    .put("/record-retention-schedules/INFORMATION_REQUEST")
            },
        )
    }

    private fun recordFile(): File =
        File.createTempFile("process-record", ".pdf").apply {
            deleteOnExit()
            writeText("%PDF-1.4 process record")
        }

    private fun reasonCodeOf(response: Response): String? =
        runCatching { response.jsonPath().getString("reasonCode") }.getOrNull()

    private fun signedIn(): InformationRequestSignedInCaller {
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
                "command-key-${userId.toString().take(8)}@process.test",
            )
        }
        return InformationRequestSignedInCaller.signIn(appUsers, userId)
    }

    private class KeyedRoute(
        val name: String,
        val send: (InformationRequestSignedInCaller, String) -> Response,
    )

    private companion object {
        const val KEY_HEADER = "Idempotency-Key"
        const val KEY_INVALID = "COMMAND_IDEMPOTENCY_KEY_INVALID"
    }
}
