package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.execution.RequestExecutionGrantRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRevokedGrantRecoveryTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var grantRepository: RequestExecutionGrantRepository

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    @Test
    fun `an owner cancels a request whose grant was revoked and so frees its open place and committed allowance`() {
        val fixture = dataSource.connection.use { SubmissionRuntimeSqlFixture(it) }
        val organizationId = fixture.template.organizationId
        insertRevokedGrant(fixture, organizationId)
        val services = runtime.build(fixture.requestId, executionGrants = grants)
        val owner =
            RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner"))
        assertEquals(1L, openRequests(organizationId))
        assertEquals(ALLOWANCE, committedEvidence(organizationId))

        val followUp = assertThrows<InformationRequestLifecycleException> {
            QuarkusTransaction.requiringNew().call {
                services.successors.create(
                    CreateInformationRequestSuccessorCommand(
                        sourceRequestId = fixture.requestId,
                        kind = InformationRequestLineageKind.SUPPLEMENT,
                        reasonCode = "more-records",
                        access = owner,
                        precondition = CommandPrecondition.ExpectedRevision(etagOf(fixture.requestId)),
                        idempotencyKey = "follow-up-under-revoked-grant",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, followUp.reasonCode)

        val cancelled = QuarkusTransaction.requiringNew().call {
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = fixture.requestId,
                    reasonCode = "grant-revoked",
                    access = owner,
                    precondition = CommandPrecondition.ExpectedRevision(etagOf(fixture.requestId)),
                    idempotencyKey = "cancel-under-revoked-grant",
                ),
            )
        }

        assertEquals(InformationRequestState.CANCELLED, cancelled.request.state)
        assertEquals(0L, openRequests(organizationId))
        assertEquals(STORED_BYTES, committedEvidence(organizationId))
    }

    private fun etagOf(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun openRequests(organizationId: UUID): Long =
        QuarkusTransaction.requiringNew().call {
            requestRepository.countOpenForOwner(InformationRequestOwnerType.ORGANIZATION, organizationId)
        }

    private fun committedEvidence(organizationId: UUID): Long =
        QuarkusTransaction.requiringNew().call {
            grantRepository.committedEvidenceBytes(SubscriptionOwnerType.ORGANIZATION, organizationId)
        }

    private fun insertRevokedGrant(fixture: SubmissionRuntimeSqlFixture, organizationId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status, enforcement_mode,
                     evidence_byte_allowance, revoked_at, revoked_reason, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', ?, now(), 'operator.revoked', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                organizationId,
                ALLOWANCE,
            )
        }
    }

    private companion object {
        const val ALLOWANCE = 1_000L
        const val STORED_BYTES = 3L
    }
}
