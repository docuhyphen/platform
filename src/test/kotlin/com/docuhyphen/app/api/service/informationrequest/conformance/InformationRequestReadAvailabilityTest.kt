package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityService
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeSummaryService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReadAvailabilityTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var workspaces: InformationRequestResponseWorkspaceService
    @Inject lateinit var responses: InformationRequestResponseDraftService
    @Inject lateinit var listings: InformationRequestExchangeSummaryService
    @Inject lateinit var capabilities: InformationRequestCapabilityService

    @Inject
    lateinit var exports: InformationRequestRecordExportService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `an operational suspension pauses changes without hiding the request from its parties`()
    {
        val request = support.fieldRequest()
        ownerStatus(request, "SUSPENDED")

        val workspace = QuarkusTransaction.requiringNew().call { workspaces.load(request.requestId, support.contributor(request)) }
        val listed = QuarkusTransaction.requiringNew().call {
            listings.listForExchange(request.runtime.exchangeId, support.contributor(request))
        }
        val refusal = assertThrows<SubscriptionDenialException> { answer(request, "suspended-answer") }

        assertEquals(request.requestId, workspace.request.id)
        assertEquals(InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED, workspace.executionStanding.kind)
        assertNull(workspace.executionStanding.reason)
        assertEquals(listOf(request.requestId), listed.requests.map { it.request.id })
        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, refusal.denial.reason)
    }

    @Test
    fun `a revoked execution grant stops changes while the request stays readable`()
    {
        val request = support.fieldRequest()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'record review' WHERE request_id = ?",
                request.requestId,
            )
        }

        val workspace = QuarkusTransaction.requiringNew().call { workspaces.load(request.requestId, support.contributor(request)) }
        val refusal = assertThrows<InformationRequestLifecycleException> { answer(request, "revoked-answer") }

        assertEquals(InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED, workspace.executionStanding.kind)
        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
    }

    @Test
    fun `issued work continues after the owner loses the feature, and its parties are not told why`()
    {
        val request = support.fieldRequest()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO subscription_feature_entitlement
                    (id, owner_type, organization_id, feature_code, is_enabled, updated_by_app_user_id)
                VALUES (?, 'ORGANIZATION', ?, 'INFORMATION_REQUESTS', FALSE, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                request.runtime.template.organizationId,
                request.runtime.template.userId,
            )
        }

        answer(request, "continuing-answer")
        val workspace = QuarkusTransaction.requiringNew().call { workspaces.load(request.requestId, support.contributor(request)) }

        assertEquals(InformationRequestExecutionStandingKind.ACTIVE, workspace.executionStanding.kind)
        assertNull(workspace.executionStanding.reason)
    }

    @Test
    fun `a party discovers that it holds assigned work without owning the feature`()
    {
        val request = support.fieldRequest()

        val discovered = QuarkusTransaction.requiringNew().call { capabilities.forCaller(support.contributor(request)) }

        assertEquals(true, discovered.assignedWork)
        assertEquals(false, discovered.scope.newWorkAvailable)
    }

    @Test
    fun `a manager makes, lists, and reads record exports while the owner is suspended, the grant is revoked, or the plan lapsed`() {
        val conditions = listOf<Pair<String, (ConformanceRequest) -> Unit>>(
            "suspended" to { ownerStatus(it, "SUSPENDED") },
            "revoked" to { revokeGrant(it) },
            "lapsed" to { ownerStatus(it, "CANCELED") },
        )
        conditions.forEach { (name, condition) ->
            val request = support.fieldRequest()
            grantManager(request)
            condition(request)
            val manager = support.owner(request)

            val created = QuarkusTransaction.requiringNew().call {
                exports.create(
                    CreateInformationRequestRecordExportCommand(
                        request.requestId,
                        manager,
                        null,
                        "export-$name"
                    )
                )
            }
            val listed = QuarkusTransaction.requiringNew().call { exports.exports(request.requestId, manager) }
            val read =
                QuarkusTransaction.requiringNew().call { exports.read(request.requestId, created.export.id, manager) }

            assertEquals(listOf(created.export.id), listed.map { it.export.id }, name)
            assertTrue(read.verified, name)
            assertEquals(
                request.requestId.toString(),
                Json.parseToJsonElement(requireNotNull(read.content)).jsonObject.getValue("request").jsonObject.getValue(
                    "id"
                ).jsonPrimitive.content
            )
            assertThrows<ForbiddenException>(name) {
                QuarkusTransaction.requiringNew()
                    .call { exports.exports(request.requestId, support.contributor(request)) }
            }
            assertThrows<ForbiddenException>(name) {
                QuarkusTransaction.requiringNew().call {
                    exports.create(
                        CreateInformationRequestRecordExportCommand(
                            request.requestId,
                            support.contributor(request),
                            null,
                            "refused-$name"
                        )
                    )
                }
            }
            assertThrows<ForbiddenException>(name) {
                QuarkusTransaction.requiringNew()
                    .call { exports.read(request.requestId, created.export.id, support.contributor(request)) }
            }
        }
    }

    private fun grantManager(request: ConformanceRequest) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.requestId,
                request.runtime.template.userId,
            )
        }
    }

    private fun revokeGrant(request: ConformanceRequest) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'record review' WHERE request_id = ?",
                request.requestId,
            )
        }
    }

    private fun answer(request: ConformanceRequest, key: String) =
        QuarkusTransaction.requiringNew().call {
            responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = request.requestId,
                    access = support.contributor(request),
                    precondition = CommandPrecondition.ExpectedRevision(support.responseETag(request)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = request.answers.requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(FieldValueEntry(request.answers.fieldContractId, JsonPrimitive("Recorded answer"))),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                ),
            )
        }

    private fun ownerStatus(request: ConformanceRequest, status: String)
    {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                request.runtime.template.organizationId,
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', ?, 5, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                request.runtime.template.organizationId,
                status,
            )
        }
    }
}
