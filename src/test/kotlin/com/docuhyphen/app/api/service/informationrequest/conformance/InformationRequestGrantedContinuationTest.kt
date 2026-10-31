package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.everyContinuationAction
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.subscription.PlanFeature
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestGrantedContinuationTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a stored grant that leaves out submission refuses the submission and still takes the answer and the attestation`() {
        val request =
            support.fieldRequest(continuationActions = everyContinuationAction - InformationRequestContinuationAction.SUBMIT.name)
        val services = runtime.build(request.requestId, executionGrants = grants)

        support.answerField(services, request, "Answered under a narrowed grant", "answer-under-narrowed-grant")
        support.assent(services, request, "assent-under-narrowed-grant")
        val stateBeforeSubmission = stateOf(request)
        val refusal = assertThrows<InformationRequestLifecycleException> {
            support.submit(services, request, "submit-under-narrowed-grant")
        }

        assertEquals(InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED, refusal.reasonCode)
        assertEquals(stateBeforeSubmission, stateOf(request))
    }

    @Test
    fun `a stored grant without the typed Field feature leaves a Field answer to the owner's live plan`() {
        val request = support.fieldRequest(grantedFeatures = listOf(PlanFeature.INFORMATION_REQUESTS.name))
        val services = runtime.build(request.requestId, executionGrants = grants)
        val organizationId = request.runtime.template.organizationId

        livePlan(organizationId, "ACTIVE", Instant.now().plus(Duration.ofDays(30)))
        support.answerField(services, request, "Typed answer under the live plan", "answer-under-live-plan")
        livePlan(organizationId, "CANCELED", Instant.now().minus(Duration.ofDays(1)))

        assertThrows<SubscriptionDenialException> {
            support.answerField(services, request, "Typed answer after the plan ended", "answer-after-plan-ended")
        }
    }

    private fun livePlan(organizationId: UUID, status: String, periodEnd: Instant) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                organizationId
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_start,
                     current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', ?, 5, ?, ?, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                organizationId,
                status,
                Timestamp.from(periodEnd.minus(Duration.ofDays(30))),
                Timestamp.from(periodEnd),
            )
        }
    }

    private fun stateOf(request: ConformanceRequest): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(request.requestId)).state }
}
