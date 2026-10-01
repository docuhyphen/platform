package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.migration.NextSubmissionVersion
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestControlledIssuanceTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository

    @Test
    fun `a Version whose every capability is installed issues in a controlled scope`()
    {
        val (fixture, draftId) = draftOn { fixture, _ -> fixture.template.versionId }
        val services = runtime.build(draftId)

        val issued = QuarkusTransaction.requiringNew().call { services.lifecycle.issue(issue(fixture, draftId)) }

        assertEquals(InformationRequestState.ISSUED, issued.request.state)
    }

    @Test
    fun `a Version that routes work to a reviewer issues now that review is installed`()
    {
        val (fixture, draftId) = draftOn { fixture, connection ->
            fixture.publishNextVersion(adjust = { next -> requireReview(connection, next) }).versionId
        }
        val services = runtime.build(draftId)

        val issued = QuarkusTransaction.requiringNew().call { services.lifecycle.issue(issue(fixture, draftId)) }

        assertEquals(InformationRequestState.ISSUED, issued.request.state)
    }

    @Test
    fun `a Version frozen against a review contract no installed executor honours is refused at issuance`()
    {
        val unservedContract = InformationRequestCapability.RESPONSE_REVIEW.contractVersion + 1
        val (fixture, draftId) = draftOn { fixture, connection ->
            fixture.publishNextVersion(
                adjust = { next -> requireReview(connection, next) },
                adjustCapabilities = { next ->
                    execute(
                        connection,
                        """
                        UPDATE information_request_template_version_capability
                        SET required_contract_version = ?
                        WHERE template_version_id = ? AND capability_key = 'RESPONSE_REVIEW'
                        """.trimIndent(),
                        unservedContract,
                        next.versionId,
                    )
                },
            ).versionId
        }
        val services = runtime.build(draftId)

        val refusal = assertThrows(InformationRequestCapabilityNotInstalledException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.lifecycle.issue(issue(fixture, draftId)) }
        }

        assertEquals(
            listOf(InformationRequestCapabilityRequirement(InformationRequestCapability.RESPONSE_REVIEW, unservedContract)),
            refusal.unserved,
        )
        QuarkusTransaction.requiringNew().run {
            assertEquals(InformationRequestState.DRAFT, requestRepository.findById(draftId)?.state)
        }
    }

    private fun requireReview(connection: Connection, next: NextSubmissionVersion)
    {
        execute(
            connection,
            "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
            next.documentBindingId,
        )
    }

    private fun draftOn(
        version: (SubmissionRuntimeSqlFixture, Connection) -> UUID,
    ): Pair<SubmissionRuntimeSqlFixture, UUID> =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            val draftId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'ORGANIZATION', ?, 'DRAFT', TRUE, 1, 1, ?, ?)
                """.trimIndent(),
                draftId,
                fixture.exchangeId,
                version(fixture, connection),
                fixture.template.organizationId,
                fixture.template.now,
                fixture.template.now,
            )
            fixture to draftId
        }

    private fun issue(fixture: SubmissionRuntimeSqlFixture, requestId: UUID) =
        IssueInformationRequestCommand(
            requestId = requestId,
            access = RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner")),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId)))
                },
            ),
            idempotencyKey = "issue-${UUID.randomUUID()}",
        )
}
