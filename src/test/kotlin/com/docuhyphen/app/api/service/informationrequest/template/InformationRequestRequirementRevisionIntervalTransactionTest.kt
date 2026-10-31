package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRequirementRevisionIntervalTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementContexts: InformationRequestRequirementAuthorizationContextProvider

    @Test
    fun `an amendment closes each superseded or removed revision and leaves exactly one open revision per asked Requirement`() {
        val addedRequirement = UUID.randomUUID()
        val (fixture, next) = dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection)
            fixture to fixture.publishNextVersion(keepAttestation = false) { version ->
                val binding = UUID.randomUUID()
                fixture.template.insertRequirement(addedRequirement, "additional-record", "DOCUMENT")
                fixture.template.insertBinding(binding, version.versionId, addedRequirement, version.sectionId, 3)
                fixture.template.insertEvidencePolicy(UUID.randomUUID(), binding, version.versionId)
                fixture.template.insertDisposition(binding, version.versionId, "PROVIDED")
            }
        }
        val services = runtime.build(fixture.requestId)

        QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next)) }

        dataSource.connection.use { connection ->
            assertEquals(
                "true",
                queryString(
                    connection,
                    """
                    SELECT (earlier.effective_to = later.effective_from)::text
                    FROM information_request_requirement_revision earlier
                             JOIN information_request_requirement_revision later
                                  ON later.information_request_requirement_id = earlier.information_request_requirement_id
                                      AND later.revision_number = earlier.revision_number + 1
                    WHERE earlier.id = ?
                    """.trimIndent(),
                    fixture.documentRevisionId,
                ),
            )
            assertEquals(1, openRevisions(connection, fixture.documentRequirementId))
            assertEquals(0, openRevisions(connection, fixture.attestationRequirementId))
            assertNotNull(
                queryString(
                    connection,
                    "SELECT effective_to::text FROM information_request_requirement_revision WHERE id = ?",
                    fixture.attestationRevisionId,
                ),
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    """
                    SELECT COUNT(*) FROM information_request_requirement_revision revision
                             JOIN information_request_requirement requirement
                                  ON requirement.id = revision.information_request_requirement_id
                    WHERE requirement.information_request_id = ? AND requirement.source_template_requirement_id = ?
                      AND revision.effective_to IS NULL
                    """.trimIndent(),
                    fixture.requestId,
                    addedRequirement,
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            val removed = requireNotNull(requirementContexts.resolve(fixture.attestationRequirementId))
            assertEquals(
                fixture.attestationRevisionId,
                (removed.policyFacts as InformationRequestRequirementPolicyFacts).currentRevisionId,
            )
        }
    }

    private fun openRevisions(connection: Connection, requirementId: UUID): Int =
        queryInt(
            connection,
            """
            SELECT COUNT(*) FROM information_request_requirement_revision
            WHERE information_request_requirement_id = ? AND effective_to IS NULL
            """.trimIndent(),
            requirementId,
        )

    private fun amend(fixture: SubmissionRuntimeSqlFixture, next: NextSubmissionVersion) =
        AmendInformationRequestCommand(
            requestId = fixture.requestId,
            targetTemplateVersionId = next.versionId,
            reasonCode = "revised-collection",
            access = RequestAccessContext(
                PrincipalRef.user(fixture.template.userId),
                AuthorizationContext(sessionRef = "owner-session"),
            ),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId)))
                },
            ),
            idempotencyKey = "interval-amendment",
        )
}
