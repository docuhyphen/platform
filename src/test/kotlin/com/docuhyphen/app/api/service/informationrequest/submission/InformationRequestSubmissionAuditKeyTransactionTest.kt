package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
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
class InformationRequestSubmissionAuditKeyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Test
    fun `two people who submit under the same idempotency key each leave their own audit event`() {
        val colleagueId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, reviewed = true).also {
                it.insertActingParty(
                    UUID.randomUUID(),
                    "CONTRIBUTOR",
                    colleagueId
                )
            }
        }
        val services = runtime.build(fixture.requestId)

        val first = submit(services, fixture, fixture.contributorUserId)
        withdraw(services, fixture, first, fixture.contributorUserId)
        submit(services, fixture, colleagueId)

        assertEquals(
            2,
            dataSource.connection.use { connection ->
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.request.submit' AND target_id = ?",
                    fixture.requestId.toString(),
                )
            },
        )
    }

    private fun submit(
        services: InformationRequestRuntimeServices,
        fixture: SubmissionRuntimeSqlFixture,
        userId: UUID
    ): UUID {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(fixture.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val request = requireNotNull(requestRepository.findById(fixture.requestId))
                            InformationRequestETag.submissionOf(
                                null,
                                runtime.contentCollector.collect(request, null).contentHash
                            )
                        },
                    ),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(userId),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = SHARED_KEY,
                ),
            )
        }.submission.submissionPackage.id
    }

    private fun withdraw(
        services: InformationRequestRuntimeServices,
        fixture: SubmissionRuntimeSqlFixture,
        packageId: UUID,
        userId: UUID
    ) {
        QuarkusTransaction.requiringNew().run {
            services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    packageId = packageId,
                    reasonCode = "answer-revisited",
                    access = access(userId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId))),
                    ),
                    idempotencyKey = "withdraw-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun access(userId: UUID) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "session-${userId.toString().take(8)}")
        )

    private companion object {
        const val SHARED_KEY = "shared-submission-key"
    }
}
