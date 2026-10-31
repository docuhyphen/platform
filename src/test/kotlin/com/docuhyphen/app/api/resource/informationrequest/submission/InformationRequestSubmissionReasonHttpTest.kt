package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import com.docuhyphen.app.api.service.informationrequest.execution.typedAnswerGrantFeatures
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubmissionReasonHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: DefaultAuthorizationService

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `withdrawal refuses an overlong reason as a malformed command`() {
        val support = ConformanceRequestSupport(dataSource, runtime, requests)
        val prepared = support.fieldRequest(reviewed = true)
        val services = runtime.build(prepared.requestId, centralAuthorization = authorization, executionGrants = grants)
        support.answerField(services, prepared, "recorded answer", "reason-answer")
        support.assent(services, prepared, "reason-assent")
        val submitted = support.submit(services, prepared, "reason-submit")
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.runtime.contributorUserId)
        val response = caller.command("reason-withdraw-${UUID.randomUUID()}")
            .header("If-Match", support.responseETag(prepared))
            .body("""{"reasonCode":"${"r".repeat(129)}"}""")
            .post("/information-requests/${prepared.requestId}/submissions/${submitted.submission.submissionPackage.id}/withdrawal")
        assertEquals(400, response.statusCode, response.asString())
        assertEquals(InformationRequestErrorCatalog.REASON_CODE_INVALID, response.jsonPath().getString("reasonCode"))
    }

    @Test
    fun `amendment refuses an overlong reason as a malformed command`() {
        val (fixture, next) = dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            execute(
                connection,
                "INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at) VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())",
                UUID.randomUUID(),
                fixture.requestId,
                fixture.template.userId,
                InformationRequestShareRoleKey.DECISION_MAKER.name,
            )
            execute(
                connection,
                """INSERT INTO request_execution_grant (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode, acting_party_cap, granted_features, continuation_actions, issued_at, created_at)
                   VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 5, ?, ?, now(), now())""",
                UUID.randomUUID(), fixture.requestId, fixture.template.organizationId,
                connection.createArrayOf("varchar", typedAnswerGrantFeatures.toTypedArray()),
                connection.createArrayOf("varchar", everyContinuationAction.toTypedArray()),
            )
            fixture to fixture.publishNextVersion { version ->
                execute(
                    connection,
                    "UPDATE information_request_template_requirement_binding SET prompt = 'Updated record prompt' WHERE id = ?",
                    version.documentBindingId
                )
            }
        }
        val etag = QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requests.findById(fixture.requestId))) }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, fixture.template.userId)
        val response = caller.command("reason-amend-${UUID.randomUUID()}")
            .header("If-Match", etag)
            .body("""{"targetTemplateVersionId":"${next.versionId}","reasonCode":"${"r".repeat(129)}"}""")
            .post("/information-requests/${fixture.requestId}/amendments")
        assertEquals(400, response.statusCode, response.asString())
        assertEquals(InformationRequestErrorCatalog.REASON_CODE_INVALID, response.jsonPath().getString("reasonCode"))
    }
}