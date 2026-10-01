package com.docuhyphen.app.api.service.informationrequest.conformance

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTrialContinuationTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var exchangeRepository: ExchangeRepository
    @Inject lateinit var grants: InformationRequestExecutionGrantService
    @Inject lateinit var entitlementGuard: InformationRequestEntitlementGuard
    @Inject lateinit var responses: InformationRequestResponseDraftService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a request issued during a trial keeps its frozen allowances and continues after the trial ends`()
    {
        val request = support.fieldRequest()
        val organizationId = request.runtime.template.organizationId
        trial(organizationId, endsAt = Instant.now().plus(Duration.ofDays(10)))
        dataSource.connection.use { execute(it, "DELETE FROM request_execution_grant WHERE request_id = ?", request.requestId) }

        val grant = QuarkusTransaction.requiringNew().call {
            grants.issueGrant(requireNotNull(requestRepository.findById(request.requestId)), exchange(request))
        }
        trial(organizationId, endsAt = Instant.now().minus(Duration.ofDays(1)))

        answer(request, "answered-after-trial")
        val refusal = assertThrows<SubscriptionDenialException> {
            QuarkusTransaction.requiringNew().run { entitlementGuard.requireRequestCreation(exchange(request)) }
        }

        assertEquals("TRIALING", grant.subscriptionStatus)
        assertNotNull(grant.trialExpiresAt)
        assertEquals(100L, grant.actingPartyCap)
        assertEquals(200L, grant.evidenceFileAllowance)
        assertEquals(500L * 1024L * 1024L, grant.evidenceByteAllowance)
        assertEquals(SubscriptionDenialReason.TRIAL_ENDED, refusal.denial.reason)
    }

    private fun exchange(request: ConformanceRequest): Exchange =
        QuarkusTransaction.requiringNew().call { requireNotNull(exchangeRepository.findById(request.runtime.exchangeId)) }

    private fun trial(organizationId: UUID, endsAt: Instant)
    {
        dataSource.connection.use { connection ->
            execute(connection, "DELETE FROM organization_subscription_policy WHERE organization_id = ?", organizationId)
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_start,
                     current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', 'TRIALING', 5, ?, ?, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                organizationId,
                Timestamp.from(endsAt.minus(Duration.ofDays(30))),
                Timestamp.from(endsAt),
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
                                entries = listOf(FieldValueEntry(request.answers.fieldContractId, JsonPrimitive("Answered after the trial"))),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                ),
            )
        }
}
