package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExportKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestSubjectRestrictionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.privacy.model.SubjectExportScenario
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubjectExportAccessTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var recordExports: InformationRequestRecordExportService

    @Inject
    lateinit var privacyRepository: InformationRequestPrivacyRequestRepository

    @Inject
    lateinit var restrictionRepository: InformationRequestSubjectRestrictionRepository

    @Inject
    lateinit var restrictions: InformationRequestSubjectRestrictionService

    @Inject
    lateinit var corrections: InformationRequestItemCorrectionService

    @Inject
    lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Test
    fun `a decision maker of one source request neither lists nor reads the subject export through that request`() {
        val scenario = scenario()
        val manager = user(scenario.siblingManagerId, verified = true)
        assertTrue(
            requestDecision(
                manager,
                Action.INFORMATION_REQUEST_EXPORT,
                scenario.sourceRequestId
            ) is Decision.Deny
        )
        assertTrue(
            requestDecision(
                manager,
                Action.INFORMATION_REQUEST_EXPORT,
                scenario.siblingRequestId
            ) is Decision.Allow
        )

        val listed =
            QuarkusTransaction.requiringNew().call { recordExports.exports(scenario.siblingRequestId, manager) }
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { recordExports.read(scenario.siblingRequestId, scenario.exportId, manager) }
        }

        assertTrue(listed.none { it.export.id == scenario.exportId }, "${listed.map { it.export.exportKind }}")
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    @Test
    fun `a caller allowed to export every source request lists and reads the subject export through either source`() {
        val scenario = scenario()
        val owner = user(scenario.ownerUserId, verified = true)

        listOf(scenario.sourceRequestId, scenario.siblingRequestId).forEach { requestId ->
            val listed = QuarkusTransaction.requiringNew().call { recordExports.exports(requestId, owner) }
            val read =
                QuarkusTransaction.requiringNew().call { recordExports.read(requestId, scenario.exportId, owner) }

            assertTrue(listed.any { it.export.id == scenario.exportId })
            assertTrue(read.content.orEmpty().contains(SOURCE_NARRATIVE))
        }
    }

    @Test
    fun `the owner's privacy administrator reads a subject export from its privacy request and each source records the read`() {
        val scenario = scenario()

        val view = QuarkusTransaction.requiringNew()
            .call { privacy(scenario, verified = false).subjectExport(scenario.privacyRequestId) }

        assertEquals(scenario.exportId, view.export.id)
        assertEquals(InformationRequestRecordExportKind.SUBJECT_RECORD, view.export.exportKind)
        assertTrue(view.verified)
        assertFalse(view.containsConfidentialContent)
        assertTrue(view.content.orEmpty().contains(SOURCE_NARRATIVE))
        assertEquals(setOf(scenario.sourceRequestId, scenario.siblingRequestId), view.sourceRequestIds.toSet())
        assertEquals(
            setOf(scenario.sourceRequestId.toString(), scenario.siblingRequestId.toString()),
            readTargets(scenario.exportId)
        )
    }

    @Test
    fun `a subject export holding protected answers asks its privacy administrator to verify again`() {
        val scenario = scenario(protectedAnswer = true)

        assertThrows(StepUpRequiredException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { privacy(scenario, verified = false).subjectExport(scenario.privacyRequestId) }
        }
        val verified = QuarkusTransaction.requiringNew()
            .call { privacy(scenario, verified = true).subjectExport(scenario.privacyRequestId) }

        assertTrue(verified.containsConfidentialContent)
        assertTrue(verified.content.orEmpty().contains(RECORDED_VALUE))
        assertEquals(
            setOf(scenario.sourceRequestId.toString(), scenario.siblingRequestId.toString()),
            readTargets(scenario.exportId)
        )
    }

    @Test
    fun `a privacy request without a record, or of another owner, has no readable subject export`() {
        val scenario = scenario()
        val restriction = QuarkusTransaction.requiringNew().call {
            privacy(scenario, verified = true).submit(
                command(
                    scenario.subjectId,
                    InformationRequestPrivacyRequestKind.RESTRICTION
                )
            )
        }

        val withoutRecord = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { privacy(scenario, verified = true).subjectExport(restriction.request.id) }
        }
        val otherOwner = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                privacy(scenario, verified = true, ownerId = UUID.randomUUID()).subjectExport(scenario.privacyRequestId)
            }
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, withoutRecord.reasonCode)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, otherOwner.reasonCode)
        assertTrue(readTargets(scenario.exportId).isEmpty())
    }

    private fun scenario(protectedAnswer: Boolean = false): SubjectExportScenario {
        lateinit var answers: FieldAnswerSqlFixture
        val subjectId = UUID.randomUUID()
        val siblingRequestId = UUID.randomUUID()
        val siblingManagerId = UUID.randomUUID()
        val source = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template)
                if (protectedAnswer) {
                    execute(
                        connection,
                        "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-record' WHERE id = ?",
                        answers.bindingId,
                    )
                }
            }).also { fixture ->
                answers.materialize(fixture)
                execute(
                    connection,
                    "UPDATE information_request_response SET narrative = ? WHERE id = ?",
                    SOURCE_NARRATIVE,
                    answers.responseId
                )
                grantRespondentShares(connection, fixture)
                fixture.insertSubject(subjectId, UUID.randomUUID())
                fixture.insertSiblingRequest(siblingRequestId, subjectId)
                fixture.insertUser(siblingManagerId)
                decisionMaker(connection, siblingRequestId, siblingManagerId)
                decisionMaker(connection, fixture.requestId, fixture.template.userId)
                decisionMaker(connection, siblingRequestId, fixture.template.userId)
            }
        }
        submitWhole(runtime.build(source.requestId, centralAuthorization = authorizationService), source)
        val recorded = QuarkusTransaction.requiringNew().call {
            privacy(source, verified = true).submit(command(subjectId, InformationRequestPrivacyRequestKind.ACCESS))
        }
        return SubjectExportScenario(
            runtime = source,
            subjectId = subjectId,
            siblingRequestId = siblingRequestId,
            siblingManagerId = siblingManagerId,
            privacyRequestId = recorded.request.id,
            exportId = requireNotNull(recorded.request.recordExportId),
        )
    }

    private fun privacy(
        scenario: SubjectExportScenario,
        verified: Boolean,
        ownerId: UUID? = null
    ): InformationRequestPrivacyService =
        privacy(scenario.runtime, verified, ownerId)

    private fun privacy(
        fixture: SubmissionRuntimeSqlFixture,
        verified: Boolean,
        ownerId: UUID? = null
    ): InformationRequestPrivacyService {
        val ownerScope = mock<InformationRequestOwnerScopeAccess>()
        val owner = InformationRequestOwnerRef(
            InformationRequestOwnerType.ORGANIZATION,
            ownerId ?: fixture.template.organizationId
        )
        whenever(ownerScope.currentOwner()).thenReturn(owner)
        whenever(ownerScope.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY))
            .thenReturn(
                RequestAccessContext(
                    PrincipalRef.user(fixture.template.userId),
                    AuthorizationContext(sessionRef = "privacy-session", mfaSatisfied = verified)
                )
            )
        return InformationRequestPrivacyService(
            ownerScope, privacyRepository, recordExports, restrictions, corrections, disposals,
            runtime.commandReceiptService, auditRecorder, actorLabels, Clock.systemUTC(),
        )
    }

    private fun command(subjectId: UUID, kind: InformationRequestPrivacyRequestKind) =
        RecordInformationRequestPrivacyRequestCommand(
            subjectId, kind, "subject.request", "policy.basis",
            idempotencyKey = "subject-export-${UUID.randomUUID()}",
            authorization = AuthorizationContext(sessionRef = "privacy-session", mfaSatisfied = true),
        )

    private fun submitWhole(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture) {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = user(fixture.attestorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture.requestId)),
                    idempotencyKey = "subject-export-assent-${UUID.randomUUID()}",
                ),
            )
        }
        QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = user(fixture.contributorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "subject-export-submit-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun grantRespondentShares(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
        listOf(
            Triple(fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR"),
            Triple(fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR"),
        ).forEach { (partyId, userId, role) ->
            execute(
                connection,
                "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                share(connection, fixture.requestId, userId, role),
                partyId
            )
        }
    }

    private fun decisionMaker(connection: Connection, requestId: UUID, userId: UUID) {
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'DECISION_MAKER', 'USER', ?, ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            userId,
            share(connection, requestId, userId, "DECISION_MAKER"),
        )
    }

    private fun share(connection: Connection, requestId: UUID, userId: UUID, role: String): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            userId,
            role,
        )
        return shareId
    }

    private fun readTargets(exportId: UUID): Set<String> =
        dataSource.connection.use {
            queryStrings(
                it,
                "SELECT target_id FROM audit_outbox WHERE event_type_key = ? AND business_transaction_id = ?",
                AuditEventType.INFORMATION_REQUEST_EXPORT_READ.key,
                exportId.toString(),
            )
        }

    private fun requestDecision(access: RequestAccessContext, action: Action, requestId: UUID): Decision =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                access.principal,
                action,
                ResourceRef.informationRequest(requestId),
                access.authorization
            )
        }

    private fun user(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "subject-export-session", mfaSatisfied = verified)
        )

    private companion object {
        const val SOURCE_NARRATIVE = "Narrative recorded on the source request"
        const val RECORDED_VALUE = "Recorded answer"
    }
}
