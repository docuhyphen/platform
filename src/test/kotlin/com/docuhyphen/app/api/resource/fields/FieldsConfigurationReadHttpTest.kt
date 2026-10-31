package com.docuhyphen.app.api.resource.fields

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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class FieldsConfigurationReadHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `a personal author reads the platform Schema Version it pinned after a later version was published`() {
        val schema = platformSchema()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, insertUser("personal-author"))

        val pinned = caller.authorized().get("/schemas/versions/${schema.firstVersionId}")
        val definition = caller.authorized().get("/schemas/definitions/${schema.definitionId}")

        assertAll(
            { assertEquals(200, pinned.statusCode, pinned.asString()) },
            { assertEquals(1, pinned.jsonPath().getInt("versionNumber"), pinned.asString()) },
            { assertEquals("PUBLISHED", pinned.jsonPath().getString("status"), pinned.asString()) },
            { assertEquals(200, definition.statusCode, definition.asString()) },
            {
                assertEquals(
                    2,
                    definition.jsonPath().getInt("latestPublishedVersion.versionNumber"),
                    definition.asString()
                )
            },
        )
    }

    @Test
    fun `a platform Schema Version still being drafted stays closed to a personal author`() {
        val schema = platformSchema()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, insertUser("personal-reader"))

        val draft = caller.authorized().get("/schemas/versions/${schema.draftVersionId}")

        assertEquals(403, draft.statusCode, draft.asString())
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["/schemas/definitions", "/fields/definitions"])
    fun `an unknown scope kind is refused as a malformed request that names no internal type`(path: String) {
        val caller = InformationRequestSignedInCaller.signIn(appUsers, insertUser("scope-reader"))

        val response = caller.authorized().queryParam("scopeKind", "team").get(path)

        assertAll(
            { assertEquals(400, response.statusCode, response.asString()) },
            { assertFalse(messageOf(response).orEmpty().contains("com.docuhyphen"), response.asString()) },
            { assertFalse(messageOf(response).orEmpty().contains("enum constant"), response.asString()) },
        )
    }

    private fun messageOf(response: Response): String? =
        runCatching { response.jsonPath().getString("errorMessage") }.getOrNull()

    private fun platformSchema(): PlatformSchema {
        val authorId = insertUser("platform-author")
        val schema = PlatformSchema(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO schema_definition
                    (id, scope_kind, namespace, schema_key, display_name, target_resource_type,
                     status, created_by_app_user_id, created_at, updated_at)
                VALUES (?, 'PLATFORM', 'process', ?, 'Process record data', 'INFORMATION_REQUEST',
                        'PUBLISHED', ?, now(), now())
                """.trimIndent(),
                schema.definitionId,
                "process-record-${schema.definitionId.toString().take(8)}",
                authorId,
            )
            insertVersion(connection, schema.firstVersionId, schema.definitionId, 1, "PUBLISHED", authorId, "2 days")
            insertVersion(connection, schema.secondVersionId, schema.definitionId, 2, "PUBLISHED", authorId, "1 day")
            insertVersion(connection, schema.draftVersionId, schema.definitionId, 3, "DRAFT", authorId, null)
        }
        return schema
    }

    private fun insertVersion(
        connection: java.sql.Connection,
        versionId: UUID,
        definitionId: UUID,
        versionNumber: Int,
        status: String,
        authorId: UUID,
        publishedAgo: String?,
    ) {
        val publishedAt = publishedAgo?.let { "now() - interval '$it'" } ?: "NULL"
        val publishedBy = if (publishedAgo == null) "NULL" else "?"
        val values = listOfNotNull(versionId, definitionId, versionNumber, status, publishedAgo?.let { authorId })
        execute(
            connection,
            """
            INSERT INTO schema_version
                (id, schema_definition_id, version_number, status, compatibility,
                 schema_rules_json, published_at, published_by_app_user_id, created_at)
            VALUES (?, ?, ?, ?, 'ADDITIVE', '[]', $publishedAt, $publishedBy, now())
            """.trimIndent(),
            *values.toTypedArray(),
        )
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

    private class PlatformSchema(
        val definitionId: UUID,
        val firstVersionId: UUID,
        val secondVersionId: UUID,
        val draftVersionId: UUID,
    )
}
