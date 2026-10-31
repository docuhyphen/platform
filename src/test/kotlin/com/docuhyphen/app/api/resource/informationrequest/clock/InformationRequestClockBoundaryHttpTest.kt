package com.docuhyphen.app.api.resource.informationrequest.clock

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import com.docuhyphen.app.api.service.informationrequest.execution.typedAnswerGrantFeatures
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestClockBoundaryHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `unknown and foreign clock policies have the same HTTP refusal`() {
        val prepared = dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val foreign = SubmissionRuntimeSqlFixture(connection)
            val policy = ClockSqlFixture(connection, foreign)
            runtime.template.userId to policy.policyId
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.first)
        val hidden = caller.authorized().get("/information-request-clock-policies/${prepared.second}")
        val unknown = caller.authorized().get("/information-request-clock-policies/${UUID.randomUUID()}")
        assertEquals(403, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(hidden.jsonPath().getString("reasonCode"), unknown.jsonPath().getString("reasonCode"))
    }

    @Test
    fun `an overlong clock key is a malformed command over HTTP`() {
        val prepared = dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val policy = ClockSqlFixture(connection, runtime)
            execute(
                connection,
                "INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at) VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())",
                UUID.randomUUID(),
                runtime.requestId,
                runtime.template.userId
            )
            execute(
                connection,
                """INSERT INTO request_execution_grant (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode, acting_party_cap, granted_features, continuation_actions, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, ?, ?, now(), now())""",
                UUID.randomUUID(),
                runtime.requestId,
                runtime.template.organizationId,
                connection.createArrayOf("varchar", typedAnswerGrantFeatures.toTypedArray()),
                connection.createArrayOf("varchar", everyContinuationAction.toTypedArray())
            )
            runtime to policy.versionId
        }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.first.template.userId)
        val result = caller.command("long-clock-key")
            .body(mapOf("clockKey" to "a".repeat(65), "policyVersionId" to prepared.second.toString()))
            .post("/information-requests/${prepared.first.requestId}/clocks")
        assertEquals(400, result.statusCode, result.asString())
        assertEquals("INFORMATION_REQUEST_CLOCK_INPUT_INVALID", result.jsonPath().getString("reasonCode"))
    }
}