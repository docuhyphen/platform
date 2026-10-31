package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRecordAuditHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var entityManager: jakarta.persistence.EntityManager

    @Test
    fun `organization legacy hold routes remain authorized at the owning organization`() {
        val user = dataSource.connection.use { SubmissionRuntimeSqlFixture(it).template.userId }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, user)
        val response = caller.authorized().get("/organizations/${UUID.randomUUID()}/audit-legal-holds")
        assertEquals(403, response.statusCode, response.asString())
    }

    @Test
    fun `legacy hold HTTP responses keep their user and organization aliases`() {
        val administrator = dataSource.connection.use { connection ->
            val user = SubmissionRuntimeSqlFixture(connection).template.userId
            execute(
                connection,
                "INSERT INTO app_role_assignment (id, app_user_id, role_name, granted_at, is_active) VALUES (?, ?, 'APP_ADMIN', now(), true)",
                UUID.randomUUID(),
                user
            )
            user
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, administrator)
        val placed = caller.command("legacy-place-${UUID.randomUUID()}")
            .body(
                mapOf(
                    "resourceType" to "EXCHANGE",
                    "resourceId" to UUID.randomUUID().toString(),
                    "reason" to "Preserve the recorded process"
                )
            )
            .post("/platform/audit-legal-holds")
        assertEquals(200, placed.statusCode, placed.asString())
        val released = caller.authorized().contentType(io.restassured.http.ContentType.JSON).body("{}")
            .post("/platform/audit-legal-holds/${placed.jsonPath().getString("holdId")}/release")
        assertEquals(200, released.statusCode, released.asString())
        assertAll(
            { assertEquals(administrator.toString(), placed.jsonPath().getString("placedByUserId")) },
            { assertTrue(placed.asString().contains("\"organizationId\":null"), placed.asString()) },
            { assertEquals(administrator.toString(), released.jsonPath().getString("releasedByUserId")) },
        )
    }

    @Test
    fun `platform export routes hide personally owned exports from a platform administrator`() {
        val owner = dataSource.connection.use { SubmissionRuntimeSqlFixture(it).template.userId }
        val administrator = dataSource.connection.use { connection ->
            val user = SubmissionRuntimeSqlFixture(connection).template.userId
            execute(
                connection,
                "INSERT INTO app_role_assignment (id, app_user_id, role_name, granted_at, is_active) VALUES (?, ?, 'APP_ADMIN', now(), true)",
                UUID.randomUUID(),
                user
            )
            user
        }
        val personal = com.docuhyphen.app.api.model.entity.AuditExport().apply {
            ownerType = "USER"
            ownerId = owner
            requestedByUserId = owner
            categoriesCsv = "EXCHANGE"
            purpose = "Personal record copy"
            occurredAfter = java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z"))
            occurredBefore = java.sql.Timestamp.from(java.time.Instant.parse("2026-01-02T00:00:00Z"))
        }
        val platform = com.docuhyphen.app.api.model.entity.AuditExport().apply {
            requestedByUserId = administrator
            categoriesCsv = "EXCHANGE"
            purpose = "Platform record copy"
            occurredAfter = personal.occurredAfter
            occurredBefore = personal.occurredBefore
        }
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run {
            entityManager.persist(personal)
            entityManager.persist(platform)
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, administrator)
        val allowed = caller.authorized().get("/platform/audit-exports/${platform.id}")
        val denied = caller.authorized().get("/platform/audit-exports/${personal.id}")
        assertEquals(200, allowed.statusCode, allowed.asString())
        assertEquals(404, denied.statusCode, denied.asString())
    }

    @Test
    fun `reading a subject export through one request audits every source and still needs access to every source`() {
        val exportId = UUID.randomUUID()
        val prepared = dataSource.connection.use { connection ->
            val first = SubmissionRuntimeSqlFixture(connection)
            val second = SubmissionRuntimeSqlFixture(connection, sharedOrganizationId = first.template.organizationId)
            val subject = UUID.randomUUID()
            first.insertSubject(subject, UUID.randomUUID())
            val content =
                """{"schemaVersion":4,"requests":[{"request":{"id":"${first.requestId}"}},{"request":{"id":"${second.requestId}"}}]}"""
            val bytes = content.toByteArray(Charsets.UTF_8)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            execute(
                connection,
                """INSERT INTO information_request_record_export
                (id, export_kind, subject_identity_ref_id, owner_kind, owner_id, schema_version, content_json,
                 content_hash_algorithm, content_hash, content_length, storage_location, transfer_decision,
                 requested_by_principal_kind, requested_by_principal_id, requested_at)
                VALUES (?, 'SUBJECT_RECORD', ?, 'ORGANIZATION', ?, 4, ?, 'SHA_256', ?, ?, 'primary', 'NOT_REQUESTED', 'USER', ?, now())""",
                exportId,
                subject,
                first.template.organizationId,
                content,
                hash,
                bytes.size.toLong(),
                first.template.userId
            )
            listOf(first.requestId, second.requestId).forEach { id ->
                execute(
                    connection,
                    "INSERT INTO information_request_record_export_source (export_id, information_request_id) VALUES (?, ?)",
                    exportId,
                    id
                )
                execute(
                    connection,
                    "INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at) VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())",
                    UUID.randomUUID(),
                    id,
                    first.template.userId
                )
            }
            Triple(first.template.userId, first.requestId, second.requestId)
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.first)
        val path = "/information-requests/${prepared.second}/record-exports/$exportId"
        val read = caller.authorized().get(path)
        dataSource.connection.use {
            execute(
                it,
                "UPDATE share SET status = 'REVOKED' WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND principal_id = ?",
                prepared.third,
                prepared.first
            )
        }
        val refused = caller.authorized().get(path)
        assertAll(
            { assertEquals(200, read.statusCode, read.asString()) },
            { assertTrue(read.asString().contains(prepared.third.toString()), read.asString()) },
            { assertEquals(404, refused.statusCode, refused.asString()) },
            {
                dataSource.connection.use { connection ->
                    listOf(prepared.second, prepared.third).forEach { id ->
                        assertEquals(
                            1,
                            queryInt(
                                connection,
                                "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = 'information_request.request.export_read'",
                                id.toString()
                            ),
                            id.toString()
                        )
                    }
                }
            },
        )
    }
}
