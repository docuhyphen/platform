package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestResponseConcurrentRetryTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Test
    fun `a concurrent retry of the same response save waits for the first and replays it`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val insideFirst = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val history = mock<InformationRequestTransitionHistoryService>()
        doAnswer { invocation ->
            insideFirst.countDown()
            releaseFirst.await(30, TimeUnit.SECONDS)
            transitionHistory.record(invocation.getArgument<InformationRequestTransitionHistoryCommand>(0))
        }.whenever(history).record(any())
        val services = runtime.build(fixture.requestId, history = history)
        val command = PatchInformationRequestResponsesCommand(
            requestId = fixture.requestId,
            access = RequestAccessContext(
                PrincipalRef.user(fixture.contributorUserId),
                AuthorizationContext(sessionRef = "contributor-session")
            ),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew()
                    .call { InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))) },
            ),
            idempotencyKey = "shared-save",
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = fixture.documentRequirementId,
                    narrative = ResponseNarrativePatch.Set("Recorded once")
                ),
            ),
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<InformationRequestResponseDraftResult> {
                withRequestContext { QuarkusTransaction.requiringNew().call { services.responses.patch(command) } }
            }
            assertTrue(insideFirst.await(30, TimeUnit.SECONDS))
            val second = executor.submit<InformationRequestResponseDraftResult> {
                withRequestContext { QuarkusTransaction.requiringNew().call { services.responses.patch(command) } }
            }
            Thread.sleep(750)
            releaseFirst.countDown()

            val firstResult = first.get(60, TimeUnit.SECONDS)
            val secondResult = second.get(60, TimeUnit.SECONDS)

            assertEquals(firstResult.responseETag, secondResult.responseETag)
            assertEquals(
                1,
                dataSource.connection.use { connection ->
                    queryInt(
                        connection,
                        "SELECT count(*) FROM command_receipt WHERE idempotency_key = 'shared-save' AND actor_id = ?",
                        fixture.contributorUserId
                    )
                },
            )
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }
}
