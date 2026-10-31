package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestExternalTargetHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `recertification accepts explicit hidden answer clear confirmations over HTTP`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, fixture.template.userId)
        val route =
            "/information-requests/${UUID.randomUUID()}/accepted-fact-offers/${UUID.randomUUID()}/recertifications"
        val requirementId = UUID.randomUUID()
        val baseline = caller.command("recertification-without-clears").header("If-Match", "\"responses:1\"")
            .body("""{"requirementId":"$requirementId","assented":true}""").post(route)
        assertEquals(403, baseline.statusCode, baseline.asString())
        val confirmed = caller.command("recertification-with-clears").header("If-Match", "\"responses:1\"")
            .body("""{"requirementId":"$requirementId","assented":true,"confirmedHiddenResponseClearRequirementIds":["${UUID.randomUUID()}"]}""")
            .post(route)
        assertEquals(403, confirmed.statusCode, confirmed.asString())
    }

    @Test
    fun `business decision reference limits are reported as bad requests before storage`() {
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { prepared ->
                execute(
                    connection, """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(), UUID.randomUUID(), prepared.exchangeId, prepared.template.userId
                )
                execute(
                    connection, """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(), UUID.randomUUID(), prepared.requestId, prepared.template.userId
                )
            }
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, fixture.template.userId)
        val base = "/information-requests/${fixture.requestId}/business-decisions"
        val known = caller.command("valid-business-reference")
            .body("""{"owningProcessKey":"process.review","outcomeCode":"recorded","reasonReference":"${"r".repeat(512)}","decidedAt":1790856000000}""")
            .post(base)
        assertEquals(201, known.statusCode, known.asString())
        assertAll(listOf("reasonReference", "externalReference").map { field ->
            org.junit.jupiter.api.function.Executable {
                val response = caller.command("oversized-$field")
                    .body("""{"owningProcessKey":"process.other","outcomeCode":"recorded","$field":"${"r".repeat(513)}","decidedAt":1790856000000}""")
                    .post(base)
                assertEquals(400, response.statusCode, response.asString())
            }
        })
    }

    @Test
    fun `an authorized external source caller receives a uniform refusal for unknown child targets`() {
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { prepared ->
                execute(
                    connection, """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(), UUID.randomUUID(), prepared.exchangeId, prepared.template.userId
                )
                execute(
                    connection, """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'REVIEWER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(), UUID.randomUUID(), prepared.requestId, prepared.template.userId
                )
            }
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, fixture.template.userId)
        val base = "/information-requests/${fixture.requestId}"
        fun proposal(requirementId: UUID) =
            """{"requirementId":"$requirementId","resultKey":"external-note","valueType":"SHORT_TEXT","value":"Recorded note","sourceReference":"registry-entry","provenanceReference":"extract-1"}"""

        val known = caller.command("known-external-target").body(proposal(fixture.documentRequirementId))
            .post("$base/imported-values")
        assertEquals(201, known.statusCode, known.asString())
        assertAll(
            {
                val response = caller.command("unknown-external-requirement").body(proposal(UUID.randomUUID()))
                    .post("$base/imported-values")
                assertEquals(403, response.statusCode, response.asString())
            },
            {
                val response = caller.command("unknown-external-value")
                    .body("""{"decision":"REJECTED","reasonCode":"does.not.match"}""")
                    .post("$base/imported-values/${UUID.randomUUID()}/decisions")
                assertEquals(403, response.statusCode, response.asString())
            },
            {
                val response = caller.command("unknown-accepted-fact").body("""{"reasonCode":"record.corrected"}""")
                    .post("$base/accepted-facts/${UUID.randomUUID()}/revocation")
                assertEquals(403, response.statusCode, response.asString())
            },
        )
    }
}
