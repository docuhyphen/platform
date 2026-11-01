package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.model.AuthorizationOrderSubmission
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestCommandAuthorizationOrderTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var clockEventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var clockPolicies: InformationRequestClockPolicyService

    @Inject
    lateinit var clockRecorder: InformationRequestClockRecorder

    @ParameterizedTest(name = "{0} on a {1}")
    @MethodSource("cases")
    fun `an outsider is refused as forbidden before the request's state, grant, or locks are disclosed`(
        command: String,
        condition: String,
    ) {
        val prepared = prepare(condition)
        val requestId = prepared.fixture.requestId
        val services = runtime.build(
            requestId,
            denies = { principal, _ -> principal == OUTSIDER.principal },
            executionGrants = if (condition == LAPSED_GRANT) revokedGrants(requestId) else null,
        )

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { send(command, services, prepared) }
        }
    }

    private fun send(
        command: String,
        services: InformationRequestRuntimeServices,
        prepared: AuthorizationOrderSubmission
    ): Any =
        submissionCommand(command, services, prepared)
            ?: answerCommand(command, services, prepared)
            ?: externalSourceCommand(command, services, prepared)
            ?: followUpAndReviewCommand(command, services, prepared)
            ?: error("No case for $command")

    private fun submissionCommand(
        command: String,
        services: InformationRequestRuntimeServices,
        prepared: AuthorizationOrderSubmission,
    ): Any? {
        val requestId = prepared.fixture.requestId
        return when (command) {
            SUBMIT -> services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = requestId,
                    stageKey = null,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-submit",
                ),
            )

            WITHDRAW -> services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = requestId,
                    packageId = prepared.packageId,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-withdraw",
                ),
            )

            ATTEST -> services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = requestId,
                    requirementId = prepared.fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-attest",
                ),
            )

            DECIDE -> services.reviewDecisions.recordDecisions(
                RecordInformationRequestReviewDecisionsCommand(
                    requestId = requestId,
                    reviewId = UUID.randomUUID(),
                    assignmentId = UUID.randomUUID(),
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-decide",
                ),
            )

            FIND -> services.reviewFindings.record(
                RecordInformationRequestReviewFindingCommand(
                    requestId = requestId,
                    reviewId = UUID.randomUUID(),
                    submissionItemId = UUID.randomUUID(),
                    reasonCode = "outsider_finding",
                    narrative = "A finding from outside the request",
                    severity = InformationRequestFindingSeverity.MINOR,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    correctionScope = InformationRequestFindingCorrectionScope.NONE,
                    access = OUTSIDER,
                    idempotencyKey = "outsider-find",
                ),
            )

            ASSIGN -> services.reviewAssignments.assign(
                AssignInformationRequestReviewerCommand(
                    requestId = requestId,
                    reviewId = UUID.randomUUID(),
                    stageKey = "review",
                    reviewerPartyId = UUID.randomUUID(),
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-assign",
                ),
            )

            PROMOTE -> services.acceptedFacts.promote(
                PromoteInformationRequestAcceptedFactCommand(
                    requestId = requestId,
                    packageId = prepared.packageId,
                    submissionItemId = UUID.randomUUID(),
                    purposeKey = "outsider-purpose",
                    visibility = InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
                    access = OUTSIDER,
                    idempotencyKey = "outsider-promote",
                ),
            )

            AMEND -> services.amendments.amend(
                AmendInformationRequestCommand(
                    requestId = requestId,
                    targetTemplateVersionId = UUID.randomUUID(),
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-amend",
                ),
            )

            else -> null
        }
    }

    private fun answerCommand(
        command: String,
        services: InformationRequestRuntimeServices,
        prepared: AuthorizationOrderSubmission,
    ): Any? {
        val requestId = prepared.fixture.requestId
        val documentRequirementId = prepared.fixture.documentRequirementId
        return when (command) {
            UPLOAD -> services.evidenceUploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = requestId,
                    requirementId = documentRequirementId,
                    access = OUTSIDER,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = STALE,
                    idempotencyKey = "outsider-upload",
                    file = evidenceFile(),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )

            REPLACE -> services.evidenceUploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = requestId,
                    requirementId = documentRequirementId,
                    artifactId = prepared.fixture.artifactId,
                    access = OUTSIDER,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = STALE,
                    idempotencyKey = "outsider-replace",
                    file = evidenceFile(),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )

            WITHDRAW_EVIDENCE -> services.evidence.withdraw(evidenceStateChange(prepared, "outsider-withdraw-evidence"))
            REMOVE_EVIDENCE -> services.evidence.remove(evidenceStateChange(prepared, "outsider-remove-evidence"))
            PATCH -> services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = requestId,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-patch",
                    patches = listOf(InformationRequestResponsePatch(requirementId = documentRequirementId)),
                ),
            )

            ADD_ENTRY -> services.groupOccurrences.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId, OUTSIDER, STALE, "outsider-add-entry", "entries",
                ),
            )

            REMOVE_ENTRY -> services.groupOccurrences.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId, OUTSIDER, STALE, "outsider-remove-entry", UUID.randomUUID(),
                ),
            )

            REORDER_ENTRIES -> services.groupOccurrences.reorder(
                ReorderInformationRequestGroupOccurrencesCommand(
                    requestId = requestId,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-reorder-entries",
                    groupKey = "entries",
                    orderedOccurrenceIds = listOf(UUID.randomUUID()),
                ),
            )

            START_CLOCK -> clocks(services).start(
                StartInformationRequestClockCommand(
                    requestId = requestId,
                    clockKey = "response",
                    policyVersionId = UUID.randomUUID(),
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = null,
                    access = OUTSIDER,
                    idempotencyKey = "outsider-start-clock",
                ),
            )

            PAUSE_CLOCK -> clocks(services).change(
                ChangeInformationRequestClockCommand(
                    requestId = requestId,
                    clockId = UUID.randomUUID(),
                    change = InformationRequestClockChange.PAUSE,
                    extensionMinutes = null,
                    reasonCode = "outsider-pause",
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-pause-clock",
                ),
            )

            else -> null
        }
    }

    private fun externalSourceCommand(
        command: String,
        services: InformationRequestRuntimeServices,
        prepared: AuthorizationOrderSubmission,
    ): Any? {
        val requestId = prepared.fixture.requestId
        return when (command) {
            CONNECTOR -> services.connectorExchanges.request(
                RequestInformationRequestConnectorExchangeCommand(
                    requestId = requestId,
                    requirementId = prepared.fixture.documentRequirementId,
                    connectorKey = "records",
                    lookupReference = null,
                    access = OUTSIDER,
                    idempotencyKey = "outsider-connector",
                ),
            )

            PROPOSE_VALUE -> services.importedValueIntake.propose(
                ProposeInformationRequestImportedValueCommand(
                    requestId = requestId,
                    requirementId = prepared.fixture.documentRequirementId,
                    resultKey = "count",
                    valueType = FieldValueType.SHORT_TEXT,
                    value = JsonPrimitive("12"),
                    sourceReference = "records",
                    confidence = InformationRequestSourceConfidence.ASSERTED,
                    verifiedAt = null,
                    expiresAt = null,
                    provenanceReference = "outsider-provenance",
                    access = OUTSIDER,
                    idempotencyKey = "outsider-propose-value",
                ),
            )

            DECIDE_VALUE -> services.importedValueDecisions.decide(
                DecideInformationRequestImportedValueCommand(
                    requestId = requestId,
                    importedValueId = UUID.randomUUID(),
                    decision = InformationRequestImportedValueDecisionKind.ACCEPTED,
                    reasonCode = "outsider_decision",
                    access = OUTSIDER,
                    idempotencyKey = "outsider-decide-value",
                ),
            )

            RECONCILE -> services.importedValueReconciliation.reconcile(
                ReconcileInformationRequestImportedValuesCommand(requestId, OUTSIDER, "outsider-reconcile"),
            )

            RESOLVE -> services.importedValueReconciliation.resolve(
                ResolveInformationRequestDiscrepancyCommand(
                    requestId = requestId,
                    discrepancyId = UUID.randomUUID(),
                    resolution = InformationRequestDiscrepancyResolution.RESPONSE_STANDS,
                    reasonCode = "outsider_resolution",
                    access = OUTSIDER,
                    idempotencyKey = "outsider-resolve",
                ),
            )

            OUTPUT -> services.generatedOutputs.record(
                RecordInformationRequestGeneratedOutputCommand(
                    requestId = requestId,
                    packageId = null,
                    outputKey = "summary",
                    externalReference = "outsider-output",
                    contentHashSha256 = null,
                    mediaType = null,
                    producedBySource = "records",
                    producedAt = Instant.now(),
                    access = OUTSIDER,
                    idempotencyKey = "outsider-output",
                ),
            )

            else -> null
        }
    }

    private fun followUpAndReviewCommand(
        command: String,
        services: InformationRequestRuntimeServices,
        prepared: AuthorizationOrderSubmission,
    ): Any? {
        val requestId = prepared.fixture.requestId
        return when (command) {
            RECUR -> services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().plusSeconds(86_400),
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-recur",
                ),
            )

            REFRESH_RULE -> services.followUps.defineRefreshRule(
                DefineInformationRequestRefreshRuleCommand(
                    requestId = requestId,
                    requirementKey = "supporting-record",
                    leadDays = 30,
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-refresh-rule",
                ),
            )

            RECERTIFY -> services.factRecertifications.recertify(
                RecertifyInformationRequestAcceptedFactCommand(
                    requestId = requestId,
                    factId = UUID.randomUUID(),
                    requirementId = prepared.fixture.documentRequirementId,
                    assented = true,
                    precondition = STALE,
                    access = OUTSIDER,
                    idempotencyKey = "outsider-recertify",
                ),
            )

            COMMENT -> services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = requestId,
                    reviewId = UUID.randomUUID(),
                    submissionItemId = UUID.randomUUID(),
                    body = "A comment from outside the request",
                    access = OUTSIDER,
                    idempotencyKey = "outsider-comment",
                ),
            )

            RECONSIDER, APPEAL -> services.reviewCycles.reopen(
                ReopenInformationRequestReviewCommand(
                    requestId = requestId,
                    reviewId = UUID.randomUUID(),
                    kind = if (command == APPEAL) InformationRequestReviewKind.APPEAL
                    else InformationRequestReviewKind.RECONSIDERATION,
                    reason = "outsider_reason",
                    access = OUTSIDER,
                    precondition = STALE,
                    idempotencyKey = "outsider-${command.length}",
                ),
            )

            BUSINESS_DECISION -> services.businessDecisions.record(
                RecordInformationRequestBusinessDecisionCommand(
                    requestId = requestId,
                    owningProcessKey = "records",
                    outcomeCode = "accepted",
                    kind = InformationRequestBusinessDecisionKind.ORIGINAL,
                    decidedAt = Instant.now(),
                    access = OUTSIDER,
                    idempotencyKey = "outsider-business-decision",
                ),
            )

            else -> null
        }
    }

    private fun evidenceStateChange(prepared: AuthorizationOrderSubmission, key: String) =
        ChangeInformationRequestEvidenceStateCommand(
            requestId = prepared.fixture.requestId,
            requirementId = prepared.fixture.documentRequirementId,
            artifactId = prepared.fixture.artifactId,
            access = OUTSIDER,
            precondition = STALE,
            idempotencyKey = key,
            reason = null,
        )

    private fun evidenceFile(): InformationRequestEvidenceFile {
        val file = File.createTempFile("outsider-evidence", ".pdf").apply {
            writeBytes("%PDF-1.4".toByteArray())
            deleteOnExit()
        }
        return InformationRequestEvidenceFile(file, "record.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun clocks(services: InformationRequestRuntimeServices) = InformationRequestClockService(
        services.gate, clockRepository, clockEventRepository, clockPolicies, clockRecorder,
        runtime.commandReceiptService, Clock.systemUTC(),
    )

    private fun prepare(condition: String): AuthorizationOrderSubmission = dataSource.connection.use { connection ->
        val fixture = SubmissionRuntimeSqlFixture(connection)
        val packageId = UUID.randomUUID()
        when (condition) {
            CLOSED -> execute(
                connection,
                "UPDATE information_request SET state = 'CLOSED', closed_at = now() WHERE id = ?",
                fixture.requestId,
            )

            CANCELLED -> execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                fixture.requestId,
            )

            LAPSED_GRANT -> Unit

            else -> {
                fixture.insertPackage(packageId, 1)
                fixture.insertItem(
                    UUID.randomUUID(),
                    packageId,
                    fixture.attestationRequirementId,
                    fixture.attestationRevisionId,
                    fixture.attestationBindingId,
                    "RESPONSE_ATTESTATION",
                )
                fixture.insertItem(
                    UUID.randomUUID(),
                    packageId,
                    fixture.documentRequirementId,
                    fixture.documentRevisionId,
                    fixture.documentBindingId,
                    "DOCUMENT",
                )
            }
        }
        AuthorizationOrderSubmission(fixture, packageId)
    }

    private fun revokedGrants(requestId: UUID): InformationRequestExecutionGrantService =
        mock<InformationRequestExecutionGrantService>().also { grants ->
            whenever(grants.findForRequest(requestId)).thenReturn(
                issuedExecutionGrant().apply {
                    this.requestId = requestId
                    ownerType = "ORGANIZATION"
                    planCode = "BUSINESS"
                    subscriptionStatus = "ACTIVE"
                    enforcementMode = "ENFORCE"
                    issuedAt = Timestamp.from(Instant.now())
                    revokedAt = Timestamp.from(Instant.now())
                    revokedReason = "record-review"
                },
            )
        }

    companion object {
        private const val SUBMIT = "submitting"
        private const val WITHDRAW = "withdrawing"
        private const val ATTEST = "confirming"
        private const val DECIDE = "recording review decisions"
        private const val FIND = "recording a finding"
        private const val ASSIGN = "assigning a reviewer"
        private const val PROMOTE = "promoting an accepted fact"
        private const val AMEND = "amending"
        private const val UPLOAD = "uploading a file"
        private const val REPLACE = "replacing a file"
        private const val WITHDRAW_EVIDENCE = "withdrawing a file"
        private const val REMOVE_EVIDENCE = "removing a file"
        private const val PATCH = "saving answers"
        private const val ADD_ENTRY = "adding a group entry"
        private const val REMOVE_ENTRY = "removing a group entry"
        private const val REORDER_ENTRIES = "reordering group entries"
        private const val START_CLOCK = "starting a clock"
        private const val PAUSE_CLOCK = "pausing a clock"
        private const val CONNECTOR = "asking a connector"
        private const val PROPOSE_VALUE = "proposing an imported value"
        private const val DECIDE_VALUE = "deciding an imported value"
        private const val RECONCILE = "reconciling imported values"
        private const val RESOLVE = "resolving a discrepancy"
        private const val OUTPUT = "recording a generated output"
        private const val RECUR = "scheduling a recurrence"
        private const val REFRESH_RULE = "adding a refresh rule"
        private const val RECERTIFY = "recertifying a fact"
        private const val COMMENT = "commenting on a review"
        private const val RECONSIDER = "reopening a review"
        private const val APPEAL = "appealing a review"
        private const val BUSINESS_DECISION = "recording a business decision"
        private const val CLOSED = "closed request"
        private const val CANCELLED = "cancelled request"
        private const val LAPSED_GRANT = "lapsed grant"
        private const val LOCKED = "locked stage"
        private val STALE = CommandPrecondition.ExpectedRevision(setOf("\"stale\""))
        private val OUTSIDER = RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(sessionRef = "outsider-session", mfaSatisfied = true),
        )

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                SUBMIT, WITHDRAW, ATTEST, DECIDE, FIND, ASSIGN, PROMOTE, AMEND, UPLOAD, REPLACE, WITHDRAW_EVIDENCE,
                REMOVE_EVIDENCE, PATCH, ADD_ENTRY, REMOVE_ENTRY, REORDER_ENTRIES, START_CLOCK, PAUSE_CLOCK, CONNECTOR,
                PROPOSE_VALUE, DECIDE_VALUE, RECONCILE, RESOLVE, OUTPUT, RECUR, REFRESH_RULE, RECERTIFY, COMMENT,
                RECONSIDER, APPEAL, BUSINESS_DECISION,
            ).flatMap { command ->
                listOf(CLOSED, CANCELLED, LAPSED_GRANT, LOCKED).map { condition -> Arguments.of(command, condition) }
            }
    }
}
