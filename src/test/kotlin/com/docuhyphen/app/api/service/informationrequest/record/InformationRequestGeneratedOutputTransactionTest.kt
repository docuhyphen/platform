package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.externalsource.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.externalsource.MutableClock
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestGeneratedOutputTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var authorizationService: AuthorizationService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a closed request keeps a reference to an output produced outside the platform, once per key`()
    {
        val request = support.fieldRequest(prepare = { connection, prepared ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                prepared.runtime.exchangeId,
                prepared.runtime.template.userId,
            )
        })
        val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService, externalClock = clock)
        support.answerField(services, request, "Recorded answer", "answer")
        support.assent(services, request, "assent")
        val submitted = support.submit(services, request, "submit")
        assertEquals(InformationRequestState.CLOSED, submitted.request.state)
        val packageId = submitted.submission.submissionPackage.id
        val owner = support.owner(request)
        fun command(key: String, packageRef: UUID? = packageId, hash: String? = "A".repeat(64), producedAt: Instant = clock.instant().minus(Duration.ofHours(1)), reference: String = "external://outputs/summary-1", outputKey: String = "summary-output") =
            RecordInformationRequestGeneratedOutputCommand(
                requestId = request.requestId,
                packageId = packageRef,
                outputKey = outputKey,
                externalReference = reference,
                contentHashSha256 = hash,
                mediaType = "application/pdf",
                producedBySource = "summary-service",
                producedAt = producedAt,
                access = owner,
                idempotencyKey = key,
            )

        val output = QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(command("record")) }
        assertEquals(packageId, output.packageId)
        assertEquals("summary-output", output.outputKey)
        assertEquals("external://outputs/summary-1", output.externalReference)
        assertEquals("a".repeat(64), output.contentHashSha256)
        assertEquals("application/pdf", output.mediaType)
        assertEquals("summary-service", output.producedBySource)
        assertEquals(request.runtime.template.userId, output.recordedByPrincipalId)
        assertEquals(output.id, QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(command("record")) }.id)
        assertThrows(CommandReceiptConflictException::class.java) {
            QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(command("record", reference = "external://outputs/other")) }
        }
        QuarkusTransaction.requiringNew().run {
            val mutations = transitionRepository.findForRequest(request.requestId).map { it.mutation }
            assertTrue(InformationRequestMutation.RECORD_GENERATED_OUTPUT in mutations, mutations.toString())
        }

        listOf(
            command("bad-key", outputKey = "Summary Output"),
            command("blank-reference", reference = " "),
            command("long-reference", reference = "external://" + "r".repeat(600)),
            command("bad-hash", hash = "not-a-hash"),
            command("future", producedAt = clock.instant().plus(Duration.ofDays(1))),
        ).forEach { refused ->
            val thrown = runCatching { QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(refused) } }.exceptionOrNull()
            assertTrue(thrown is InformationRequestCommandRequestException, "${refused.idempotencyKey}: $thrown")
        }
        val foreign = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(command("foreign", packageRef = UUID.randomUUID())) }
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, foreign.reasonCode)

        val contributor = support.contributor(request)
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { services.generatedOutputs.record(command("by-contributor").let {
                RecordInformationRequestGeneratedOutputCommand(it.requestId, it.packageId, it.outputKey, it.externalReference, it.contentHashSha256, it.mediaType, it.producedBySource, it.producedAt, contributor, it.idempotencyKey)
            }) }
        }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { services.generatedOutputs.outputs(request.requestId, contributor) }
        }
        assertEquals(listOf(output.id), QuarkusTransaction.requiringNew().call { services.generatedOutputs.outputs(request.requestId, owner) }.map { it.id })
    }
}
