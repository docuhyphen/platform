package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.occurrence.AddInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.RemoveInformationRequestGroupOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.occurrence.ReorderInformationRequestGroupOccurrencesCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.model.informationrequest.review.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import io.quarkus.arc.ClientProxy
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestNoAuthCommandSessionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var authorization: AuthorizationService

    @Inject
    lateinit var responses: InformationRequestResponseDraftService

    @Inject
    lateinit var occurrences: InformationRequestGroupOccurrenceService

    @Inject
    lateinit var recertifications: InformationRequestFactRecertificationService

    @Inject
    lateinit var attestations: InformationRequestSubmissionAttestationService

    @Inject
    lateinit var uploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var collection: InformationRequestEvidenceCollectionService

    @Inject
    lateinit var comments: InformationRequestReviewCommentService

    @Inject
    lateinit var cycles: InformationRequestReviewCycleService

    @Inject
    lateinit var submissions: InformationRequestSubmissionService

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandNames")
    fun `a command sent through an access link is refused once its session ended after the session was resolved`(
        command: String,
    ) {
        val refusal = attempt(command, endSession = true)

        assertEquals(
            InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            (refusal as? InformationRequestLifecycleException)?.reasonCode,
            "$command: $refusal",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandNames")
    fun `a command sent through an access link reaches its own checks while its session is current`(command: String) {
        val refusal = attempt(command, endSession = false)

        assertFalse(
            refusal is InformationRequestLifecycleException && refusal.reasonCode in SESSION_REFUSALS,
            "$command: $refusal",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandNames")
    fun `a command sent through an access link authorizes with the verification its session holds when it runs`(
        command: String,
    ) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        val access = readAccess.resolve(scenario.requestId, scenario.rawToken, issued.sessionToken)
        assertTrue(access.authorization.mfaSatisfied)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_access_session SET issued_at = now() - interval '1 hour' WHERE id = ?",
                issued.session.id,
            )
        }
        val authorizedWith = recordAuthorizationContexts()

        runCatching { commands().getValue(command)(scenario, access) }

        assertTrue(authorizedWith.none { it.mfaSatisfied }, "$command authorized with $authorizedWith")
    }

    private fun attempt(command: String, endSession: Boolean): Throwable? {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        val access = readAccess.resolve(scenario.requestId, scenario.rawToken, issued.sessionToken)
        if (endSession) dataSource.revokeAccessSession(issued.session.id)
        return runCatching { commands().getValue(command)(scenario, access) }.exceptionOrNull()
    }

    private fun recordAuthorizationContexts(): List<AuthorizationContext> {
        val delegate = ClientProxy.unwrap(authorization)
        val seen = Collections.synchronizedList(mutableListOf<AuthorizationContext>())
        val recording = Mockito.mock(DefaultAuthorizationService::class.java, Answer { invocation ->
            seen += invocation.arguments.filterIsInstance<AuthorizationContext>()
            try {
                invocation.method.invoke(delegate, *invocation.arguments)
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
        })
        QuarkusMock.installMockForType(recording, DefaultAuthorizationService::class.java)
        return seen
    }

    private fun commands(): Map<String, (NoAuthSessionSqlScenario, RequestAccessContext) -> Any> = mapOf(
        SAVE_RESPONSES to { scenario, access ->
            responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    patches = listOf(
                        InformationRequestResponsePatch(
                            scenario.runtime.documentRequirementId,
                            narrative = ResponseNarrativePatch.Set("A response sent through an access link"),
                        ),
                    ),
                ),
            )
        },
        ADD_GROUP_ENTRY to { scenario, access ->
            occurrences.add(
                AddInformationRequestGroupOccurrenceCommand(
                    requestId = scenario.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    groupKey = "unlisted-group",
                ),
            )
        },
        REMOVE_GROUP_ENTRY to { scenario, access ->
            occurrences.remove(
                RemoveInformationRequestGroupOccurrenceCommand(
                    requestId = scenario.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    occurrenceId = UUID.randomUUID(),
                ),
            )
        },
        REORDER_GROUP_ENTRIES to { scenario, access ->
            occurrences.reorder(
                ReorderInformationRequestGroupOccurrencesCommand(
                    requestId = scenario.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    groupKey = "unlisted-group",
                    orderedOccurrenceIds = emptyList(),
                ),
            )
        },
        RECERTIFY_FACT to { scenario, access ->
            recertifications.recertify(
                RecertifyInformationRequestAcceptedFactCommand(
                    requestId = scenario.requestId,
                    factId = UUID.randomUUID(),
                    requirementId = scenario.runtime.documentRequirementId,
                    assented = true,
                    precondition = CommandPrecondition.Unconditioned,
                    access = access,
                    idempotencyKey = key(),
                ),
            )
        },
        RECORD_ATTESTATION to { scenario, access ->
            attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = scenario.requestId,
                    requirementId = scenario.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                ),
            )
        },
        UPLOAD_EVIDENCE to { scenario, access ->
            uploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = scenario.requestId,
                    requirementId = scenario.runtime.documentRequirementId,
                    access = access,
                    surface = InformationRequestEvidenceSurface.NO_AUTH,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    file = evidenceFile(),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )
        },
        REPLACE_EVIDENCE to { scenario, access ->
            uploads.replace(
                ReplaceInformationRequestEvidenceCommand(
                    requestId = scenario.requestId,
                    requirementId = scenario.runtime.documentRequirementId,
                    artifactId = scenario.runtime.artifactId,
                    access = access,
                    surface = InformationRequestEvidenceSurface.NO_AUTH,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                    file = evidenceFile(),
                    attributes = InformationRequestEvidenceAttributes(),
                ),
            )
        },
        WITHDRAW_EVIDENCE to { scenario, access ->
            collection.withdraw(evidenceStateChange(scenario, access, "The file is no longer current"))
        },
        REMOVE_EVIDENCE to { scenario, access ->
            collection.remove(evidenceStateChange(scenario, access, null))
        },
        COMMENT_ON_REVIEW to { scenario, access ->
            comments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = scenario.requestId,
                    reviewId = UUID.randomUUID(),
                    submissionItemId = UUID.randomUUID(),
                    body = "A question about the review",
                    access = access,
                    idempotencyKey = key(),
                ),
            )
        },
        APPEAL_REVIEW to { scenario, access ->
            cycles.reopen(
                ReopenInformationRequestReviewCommand(
                    requestId = scenario.requestId,
                    reviewId = UUID.randomUUID(),
                    kind = InformationRequestReviewKind.APPEAL,
                    reason = "The decision overlooked a record",
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                ),
            )
        },
        SUBMIT_PACKAGE to { scenario, access ->
            submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.requestId,
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                ),
            )
        },
        WITHDRAW_PACKAGE to { scenario, access ->
            submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = scenario.requestId,
                    packageId = UUID.randomUUID(),
                    access = access,
                    precondition = CommandPrecondition.Unconditioned,
                    idempotencyKey = key(),
                ),
            )
        },
    )

    private fun evidenceStateChange(scenario: NoAuthSessionSqlScenario, access: RequestAccessContext, reason: String?) =
        ChangeInformationRequestEvidenceStateCommand(
            requestId = scenario.requestId,
            requirementId = scenario.runtime.documentRequirementId,
            artifactId = scenario.runtime.artifactId,
            access = access,
            precondition = CommandPrecondition.Unconditioned,
            idempotencyKey = key(),
            reason = reason,
        )

    private fun evidenceFile(): InformationRequestEvidenceFile {
        val file = Files.createTempFile("session-boundary", ".pdf").toFile()
        file.writeBytes("%PDF-1.4 session boundary record".toByteArray())
        file.deleteOnExit()
        return InformationRequestEvidenceFile(file, "record.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun key() = "session-command-${UUID.randomUUID()}"

    companion object {
        private const val SAVE_RESPONSES = "save responses"
        private const val ADD_GROUP_ENTRY = "add a group entry"
        private const val REMOVE_GROUP_ENTRY = "remove a group entry"
        private const val REORDER_GROUP_ENTRIES = "reorder group entries"
        private const val RECERTIFY_FACT = "recertify an accepted fact"
        private const val RECORD_ATTESTATION = "record an attestation"
        private const val UPLOAD_EVIDENCE = "upload evidence"
        private const val REPLACE_EVIDENCE = "replace evidence"
        private const val WITHDRAW_EVIDENCE = "withdraw evidence"
        private const val REMOVE_EVIDENCE = "remove evidence"
        private const val COMMENT_ON_REVIEW = "comment on a review"
        private const val APPEAL_REVIEW = "appeal a review"
        private const val SUBMIT_PACKAGE = "submit a package"
        private const val WITHDRAW_PACKAGE = "withdraw a package"

        private val SESSION_REFUSALS = setOf(
            InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
            InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
            InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED,
            InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
        )

        @JvmStatic
        fun commandNames(): List<String> = listOf(
            SAVE_RESPONSES,
            ADD_GROUP_ENTRY,
            REMOVE_GROUP_ENTRY,
            REORDER_GROUP_ENTRIES,
            RECERTIFY_FACT,
            RECORD_ATTESTATION,
            UPLOAD_EVIDENCE,
            REPLACE_EVIDENCE,
            WITHDRAW_EVIDENCE,
            REMOVE_EVIDENCE,
            COMMENT_ON_REVIEW,
            APPEAL_REVIEW,
            SUBMIT_PACKAGE,
            WITHDRAW_PACKAGE,
        )
    }
}
