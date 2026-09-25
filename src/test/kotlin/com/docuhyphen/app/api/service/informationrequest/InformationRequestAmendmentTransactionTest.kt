package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.NextSubmissionVersion
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAmendmentTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var currentRepository: InformationRequestRequirementCurrentRepository
    @Inject lateinit var responseRepository: InformationRequestResponseRepository
    @Inject lateinit var noticeRepository: InformationRequestNoticeIntentRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository

    @Test
    fun `a reworded Version re-pins the request, advances each Requirement, keeps its answers, and owes each party a notice`()
    {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val services = runtime.build(fixture.requestId)
        val etag = aggregateETag(fixture)

        val result = QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword")) }

        assertEquals(next.versionId, result.request.templateVersionId)
        assertNotEquals(etag, result.requestETag)
        assertEquals(
            listOf("supporting-record" to InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED),
            result.amendment.changes.map { it.requirementKey to it.changeKind },
        )
        assertEquals(
            setOf(fixture.contributorPartyId, fixture.attestorPartyId),
            result.amendment.notices.map { it.partyId }.toSet(),
        )
        QuarkusTransaction.requiringNew().run {
            val active = requirementRepository.findForRequest(fixture.requestId)
            assertEquals(
                setOf(next.documentBindingId, next.attestationBindingId),
                active.map { it.sourceTemplateBindingId }.toSet(),
            )
            assertEquals(setOf(2), active.map { currentRepository.findForRequirement(it.id)?.currentRevisionNumber }.toSet())
            val response = requireNotNull(responseRepository.findAllForRequest(fixture.requestId).single())
            assertEquals(fixture.documentRevisionId, response.requirementRevisionId)
            assertNull(response.reconfirmationRequiredByAmendmentId)
            assertEquals(
                setOf(InformationRequestNoticeDeliveryState.PENDING),
                noticeRepository.findForRequest(fixture.requestId).map { it.deliveryState }.toSet(),
            )
            assertTrue(InformationRequestMutation.AMEND in transitionRepository.findForRequest(fixture.requestId).map { it.mutation })
        }

        val replayed = QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, etag, "reword")) }
        assertEquals(result.amendment.amendment.id, replayed.amendment.amendment.id)
        val visible = QuarkusTransaction.requiringNew().call { services.amendmentQueries.amendments(fixture.requestId, owner(fixture)) }
        assertEquals(1, visible.single().view.amendment.amendmentNumber)
    }

    @Test
    fun `a change of meaning makes the existing answer need reconfirming until it is saved again`()
    {
        val (fixture, next) = fixture { connection, _, version ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET requiredness = 'OPTIONAL' WHERE id = ?",
                version.documentBindingId,
            )
        }
        val services = runtime.build(fixture.requestId)

        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "tighten"))
        }

        assertEquals(
            listOf(Triple("supporting-record", InformationRequestAmendmentChangeKind.MEANING_CHANGED, true)),
            result.amendment.changes.map { Triple(it.requirementKey, it.changeKind, it.reconfirmationRequired) },
        )
        assertEquals(
            InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED,
            problems(services, fixture)[fixture.documentRequirementId],
        )

        QuarkusTransaction.requiringNew().run {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(fixture)),
                    idempotencyKey = "reconfirm-${UUID.randomUUID()}",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.documentRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }

        assertEquals(null, problems(services, fixture)[fixture.documentRequirementId])
        QuarkusTransaction.requiringNew().run {
            assertNull(responseRepository.findAllForRequest(fixture.requestId).single().reconfirmationRequiredByAmendmentId)
        }
    }

    @Test
    fun `a Version that stops asking for a Requirement retires it and one that newly asks materializes it`()
    {
        val addedRequirement = UUID.randomUUID()
        val (fixture, next) = fixture(keepAttestation = false) { _, fixture, version ->
            val binding = UUID.randomUUID()
            fixture.template.insertRequirement(addedRequirement, "additional-record", "DOCUMENT")
            fixture.template.insertBinding(binding, version.versionId, addedRequirement, version.sectionId, 3)
            fixture.template.insertEvidencePolicy(UUID.randomUUID(), binding, version.versionId)
            fixture.template.insertDisposition(binding, version.versionId, "PROVIDED")
        }
        val services = runtime.build(fixture.requestId)

        val result = QuarkusTransaction.requiringNew().call {
            services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "restructure"))
        }

        assertEquals(
            listOf(
                "additional-record" to InformationRequestAmendmentChangeKind.ADDED,
                "recorded-assertion" to InformationRequestAmendmentChangeKind.REMOVED,
            ),
            result.amendment.changes.map { it.requirementKey to it.changeKind },
        )
        QuarkusTransaction.requiringNew().run {
            val active = requirementRepository.findForRequest(fixture.requestId)
            assertEquals(
                setOf(fixture.documentTemplateRequirementId, addedRequirement),
                active.map { it.sourceTemplateRequirementId }.toSet(),
            )
            assertEquals(3, requirementRepository.findAllForRequest(fixture.requestId).size)
        }
    }

    @Test
    fun `an earlier Version, a stale request, and a change reaching a submitted stage are refused whole`()
    {
        val (fixture, next) = fixture(staged = true)
        val services = runtime.build(fixture.requestId)

        val earlier = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.amendments.amend(amend(fixture, fixture.template.versionId, aggregateETag(fixture), "same-version"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.AMENDMENT_TARGET_INVALID, earlier.reasonCode)

        val stale = assertThrows(CommandPreconditionException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.amendments.amend(amend(fixture, next.versionId, "\"stale\"", "stale")) }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)

        val stageETag = QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf("record-stage", runtime.contentCollector.collect(request, "record-stage").contentHash)
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    stageKey = "record-stage",
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(stageETag),
                    idempotencyKey = "stage-before-amendment",
                ),
            )
        }
        val submitted = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.amendments.amend(amend(fixture, next.versionId, aggregateETag(fixture), "after-submission"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED, submitted.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(fixture.template.versionId, requestRepository.findById(fixture.requestId)?.templateVersionId)
            assertTrue(noticeRepository.findForRequest(fixture.requestId).isEmpty())
        }
    }

    private fun fixture(
        staged: Boolean = false,
        keepAttestation: Boolean = true,
        adjust: (Connection, SubmissionRuntimeSqlFixture, NextSubmissionVersion) -> Unit = { _, _, _ -> },
    ): Pair<SubmissionRuntimeSqlFixture, NextSubmissionVersion> =
        dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection, staged = staged)
            fixture to fixture.publishNextVersion(keepAttestation = keepAttestation) { version -> adjust(connection, fixture, version) }
        }

    private fun problems(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture) =
        QuarkusTransaction.requiringNew().call {
            services.submissionQueries.preview(fixture.requestId, null, contributor(fixture)).readiness.problems
                .associate { it.requirementId to it.code }
        }

    private fun aggregateETag(fixture: SubmissionRuntimeSqlFixture): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId)))
        }

    private fun responseETag(fixture: SubmissionRuntimeSqlFixture): String =
        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(fixture.requestId)))

    private fun amend(fixture: SubmissionRuntimeSqlFixture, versionId: UUID, etag: String, key: String) =
        AmendInformationRequestCommand(
            requestId = fixture.requestId,
            targetTemplateVersionId = versionId,
            reasonCode = "revised-collection",
            access = owner(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
        )

    private fun owner(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner-session"))

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))
}
