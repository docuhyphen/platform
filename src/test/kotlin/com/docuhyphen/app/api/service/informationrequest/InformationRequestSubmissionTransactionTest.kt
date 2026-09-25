package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionResult
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionSupportingLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSubmissionTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var exchangeRepository: ExchangeRepository
    @Inject lateinit var requirementRepository: InformationRequestRequirementRepository
    @Inject lateinit var revisionRepository: InformationRequestRequirementRevisionRepository
    @Inject lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository
    @Inject lateinit var templateVersionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository
    @Inject lateinit var bindingRepository: InformationRequestTemplateRequirementBindingRepository
    @Inject lateinit var packageRepository: InformationRequestSubmissionPackageRepository
    @Inject lateinit var itemRepository: InformationRequestSubmissionItemRepository
    @Inject lateinit var evidenceRepository: InformationRequestSubmissionEvidenceRepository
    @Inject lateinit var linkRepository: InformationRequestSubmissionSupportingLinkRepository
    @Inject lateinit var packageAttestationRepository: InformationRequestSubmissionPackageAttestationRepository
    @Inject lateinit var attestationRepository: InformationRequestSubmissionAttestationRepository
    @Inject lateinit var withdrawalRepository: InformationRequestSubmissionWithdrawalRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var contentCollector: InformationRequestSubmissionContentCollector
    @Inject lateinit var completenessProgressService: InformationRequestCompletenessProgressService
    @Inject lateinit var attestationEvaluationService: InformationRequestAttestationEvaluationService
    @Inject lateinit var lockService: InformationRequestSubmissionLockService
    @Inject lateinit var packageReader: InformationRequestSubmissionPackageReader
    @Inject lateinit var policyLoader: InformationRequestAttestationPolicyLoader
    @Inject lateinit var stages: InformationRequestSubmissionStages
    @Inject lateinit var requirementContext: InformationRequestRequirementAuthorizationContextProvider
    @Inject lateinit var commandReceiptService: CommandReceiptService
    @Inject lateinit var transitionHistory: InformationRequestTransitionHistoryService
    @Inject lateinit var clock: Clock
    @Inject lateinit var entityManager: EntityManager
    @Inject lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository
    @Inject lateinit var versionRepository: InformationRequestEvidenceVersionRepository
    @Inject lateinit var documentVersionRecordingService: DocumentVersionRecordingService
    @Inject lateinit var dispositionRepository: InformationRequestTemplateBindingDispositionRepository
    @Inject lateinit var responseRepository: InformationRequestResponseRepository
    @Inject lateinit var schemaAssignmentService: SchemaAssignmentService
    @Inject lateinit var schemaAssignmentRepository: SchemaAssignmentRepository
    @Inject lateinit var fieldValueSetRepository: FieldValueSetRepository
    @Inject lateinit var fieldContractRepository: FieldContractRepository
    @Inject lateinit var conditionEvaluationService: InformationRequestConditionEvaluationService
    @Inject lateinit var structuredResponseValidationService: InformationRequestStructuredResponseValidationService
    @Inject lateinit var fieldValueRevisionQueryService: FieldValueRevisionQueryService

    @Test
    fun `a ready whole-package submission freezes the scope and closes a request that needs no review`()
    {
        val fixture = fixture()
        val services = services(fixture)
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(assent(fixture, submissionETag(fixture, null)))
        }
        assertEquals(submissionETag(fixture, null), attested.submissionETag)

        val submitted = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, null, attested.submissionETag, "submit-key"))
        }

        assertEquals(1, submitted.submission.submissionPackage.packageNumber)
        assertEquals(InformationRequestState.CLOSED, submitted.request.state)
        assertEquals(2, submitted.submission.items.size)
        assertEquals(1, submitted.submission.evidence.size)
        assertEquals(listOf(attested.attestation.id), submitted.submission.attestations.map { it.id })
        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(InformationRequestState.CLOSED, request.state)
            assertEquals(submitted.submission.submissionPackage.id, request.satisfiedByPackageId)
            val mutations = transitionRepository.findForRequest(fixture.requestId).map { it.mutation }
            assertTrue(mutations.containsAll(listOf(InformationRequestMutation.SUBMIT, InformationRequestMutation.CLOSE)))
            assertEquals(
                fixture.evidenceVersionId,
                evidenceRepository.findForPackages(listOf(submitted.submission.submissionPackage.id)).single().evidenceVersionId,
            )
        }

        val replayed = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, null, attested.submissionETag, "submit-key"))
        }
        assertEquals(submitted.submission.submissionPackage.id, replayed.submission.submissionPackage.id)
        QuarkusTransaction.requiringNew().run {
            assertEquals(1, packageRepository.findForRequest(fixture.requestId).size)
        }
    }

    @Test
    fun `a scope that is not ready is refused whole and names the item that blocks it`()
    {
        val fixture = fixture()
        val services = services(fixture)

        val refusal = assertThrows(InformationRequestSubmissionIncompleteException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.submissions.submit(submit(fixture, null, submissionETag(fixture, null), "early-key"))
            }
        }

        assertEquals(
            listOf(fixture.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            refusal.readiness.problems.map { it.requirementId to it.code },
        )
        QuarkusTransaction.requiringNew().run {
            assertTrue(packageRepository.findForRequest(fixture.requestId).isEmpty())
            assertEquals(InformationRequestState.ISSUED, requestRepository.findById(fixture.requestId)?.state)
        }
    }

    @Test
    fun `a submission or attestation against content the caller did not review is refused as stale`()
    {
        val fixture = fixture()
        val services = services(fixture)
        val stale = "\"submission:whole:${"0".repeat(64)}\""

        val attestation = assertThrows(CommandPreconditionException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.attestations.record(assent(fixture, stale)) }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, attestation.kind)
        assertEquals(submissionETag(fixture, null), attestation.currentETag)

        val submission = assertThrows(CommandPreconditionException::class.java)
        {
            QuarkusTransaction.requiringNew().call { services.submissions.submit(submit(fixture, null, stale, "stale-key")) }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, submission.kind)
    }

    @Test
    fun `a submitted stage stays locked while another stage remains editable and the request stays open`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture)

        val first = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "stage-one"))
        }
        assertEquals("record-stage", first.submission.submissionPackage.stageKey)
        assertEquals(InformationRequestState.ISSUED, first.request.state)
        QuarkusTransaction.requiringNew().run {
            assertEquals(setOf(fixture.documentRequirementId), lockService.lockedRequirementIds(fixture.requestId))
        }

        val duplicate = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "stage-one-again"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_ALREADY_SUBMITTED, duplicate.reasonCode)

        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(assent(fixture, submissionETag(fixture, "confirmation-stage")))
        }
        val second = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "confirmation-stage", attested.submissionETag, "stage-two"))
        }
        assertEquals(2, second.submission.submissionPackage.packageNumber)
        assertTrue(second.submission.submissionPackage.completesRequest)
        assertEquals(InformationRequestState.CLOSED, second.request.state)
    }

    @Test
    fun `withdrawing a stage before review reopens it and a resubmission follows the withdrawn package`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture)
        val first = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "first-stage"))
        }

        val withdrawn = QuarkusTransaction.requiringNew().call {
            services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    packageId = first.submission.submissionPackage.id,
                    reasonCode = "records-to-add",
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(first.responseETag),
                    idempotencyKey = "withdraw-first",
                ),
            )
        }
        assertEquals("records-to-add", withdrawn.submission.withdrawal?.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertTrue(lockService.lockedRequirementIds(fixture.requestId).isEmpty())
        }

        val again = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.submissions.withdraw(
                    WithdrawInformationRequestPackageCommand(
                        requestId = fixture.requestId,
                        packageId = first.submission.submissionPackage.id,
                        access = contributor(fixture),
                        precondition = CommandPrecondition.ExpectedRevision(withdrawn.responseETag),
                        idempotencyKey = "withdraw-again",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_NOT_WITHDRAWABLE, again.reasonCode)
        QuarkusTransaction.requiringNew().run {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = fixture.requestId,
                    access = contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(withdrawn.responseETag),
                    idempotencyKey = "revise-after-withdrawal",
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = fixture.documentRequirementId,
                            narrative = ResponseNarrativePatch.Set("A revised note"),
                        ),
                    ),
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            val frozen = packageReader.view(fixture.requestId, first.submission.submissionPackage.id).items
                .single { it.informationRequestRequirementId == fixture.documentRequirementId }
            assertNull(frozen.narrative)
            assertEquals("A revised note", responseRepository.findAllForRequest(fixture.requestId).single().narrative)
        }

        val resubmitted = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "resubmit-stage"))
        }
        assertEquals(first.submission.submissionPackage.id, resubmitted.submission.submissionPackage.previousPackageId)
        assertNull(resubmitted.submission.withdrawal)
    }

    @Test
    fun `evidence a package submitted cannot be withdrawn until the package itself is withdrawn`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture)
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "locking-stage"))
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.evidence.withdraw(
                    ChangeInformationRequestEvidenceStateCommand(
                        requestId = fixture.requestId,
                        requirementId = fixture.documentRequirementId,
                        artifactId = fixture.artifactId,
                        access = contributor(fixture),
                        precondition = CommandPrecondition.ExpectedRevision(RevisionETag.of(fixture.artifactId, 1)),
                        idempotencyKey = "withdraw-submitted-evidence",
                        reason = "Replacing the record",
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestEvidenceCollectionState.ACTIVE,
                artifactRepository.findById(fixture.artifactId)?.collectionState,
            )
        }
    }

    @Test
    fun `a response a package submitted cannot change until the package is withdrawn`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture)
        val submitted = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", submissionETag(fixture, "record-stage"), "lock-responses"))
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.responses.patch(
                    PatchInformationRequestResponsesCommand(
                        requestId = fixture.requestId,
                        access = contributor(fixture),
                        precondition = CommandPrecondition.ExpectedRevision(submitted.responseETag),
                        idempotencyKey = "patch-submitted-response",
                        patches = listOf(
                            InformationRequestResponsePatch(
                                requirementId = fixture.documentRequirementId,
                                narrative = ResponseNarrativePatch.Set("A later note"),
                            ),
                        ),
                    ),
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
    }

    @Test
    fun `stages submitted in order refuse a later stage before an earlier one`()
    {
        val fixture = fixture(staged = true, sequential = true)
        val services = services(fixture)
        QuarkusTransaction.requiringNew().run { services.attestations.record(assent(fixture, submissionETag(fixture, "confirmation-stage"))) }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java)
        {
            QuarkusTransaction.requiringNew().call {
                services.submissions.submit(submit(fixture, "confirmation-stage", submissionETag(fixture, "confirmation-stage"), "out-of-order"))
            }
        }
        assertEquals(InformationRequestErrorCatalog.SUBMISSION_STAGE_ORDER, refusal.reasonCode)
    }

    @Test
    fun `parallel duplicates of one submission record exactly one package`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture, history = mock())
        val etag = submissionETag(fixture, "record-stage")
        val executor = Executors.newFixedThreadPool(2)
        try
        {
            val calls = List(2) {
                Callable<InformationRequestSubmissionResult> {
                    QuarkusTransaction.requiringNew().call {
                        services.submissions.submit(submit(fixture, "record-stage", etag, "shared-submission"))
                    }
                }
            }
            val results = executor.invokeAll(calls, 60, TimeUnit.SECONDS).map { it.get() }
            assertEquals(1, results.map { it.submission.submissionPackage.id }.distinct().size)
            QuarkusTransaction.requiringNew().run {
                assertEquals(1, packageRepository.findForRequest(fixture.requestId).size)
            }
        }
        finally
        {
            executor.shutdownNow()
        }
    }

    @Test
    fun `review-before-submit names the next open stage and each caller's standing without disclosing hidden items`()
    {
        val fixture = fixture(staged = true)
        val services = services(fixture)
        val query = submissionQuery(fixture)

        val opening = QuarkusTransaction.requiringNew().call { query.preview(fixture.requestId, null, contributor(fixture)) }
        assertEquals("record-stage", opening.stageKey)
        assertTrue(opening.readiness.ready)
        assertTrue(opening.canSubmit)
        assertEquals(submissionETag(fixture, "record-stage"), opening.submissionETag)

        val first = QuarkusTransaction.requiringNew().call {
            services.submissions.submit(submit(fixture, "record-stage", opening.submissionETag, "preview-stage-one"))
        }

        val next = QuarkusTransaction.requiringNew().call { query.preview(fixture.requestId, null, attestor(fixture)) }
        assertEquals("confirmation-stage", next.stageKey)
        assertEquals(
            listOf(fixture.attestationRequirementId to InformationRequestSubmissionProblemCode.ATTESTATION_MISSING),
            next.readiness.problems.map { it.requirementId to it.code },
        )
        assertEquals(false, next.canSubmit)
        assertTrue(next.attestations.single().callerCanAttest)
        assertEquals(
            listOf("record-stage" to first.submission.submissionPackage.id, "confirmation-stage" to null),
            next.stages.map { it.stageKey to it.submittedPackage?.id },
        )
        val submittedStage = QuarkusTransaction.requiringNew().call { query.preview(fixture.requestId, "record-stage", attestor(fixture)) }
        assertEquals(false, submittedStage.canSubmit)
        assertEquals(false, submittedStage.attestations.any { it.callerCanAttest })

        val hidden = submissionQuery(fixture, hiddenRequirementId = fixture.documentRequirementId)
        val packages = QuarkusTransaction.requiringNew().call { hidden.packages(fixture.requestId, contributor(fixture)) }
        assertEquals(setOf(fixture.documentRequirementId), packages.single().view.items.map { it.informationRequestRequirementId }.toSet())
        assertTrue(packages.single().visibleRequirementIds.isEmpty())
    }

    private fun fixture(staged: Boolean = false, sequential: Boolean = false): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, staged = staged, sequential = sequential)
        }

    private fun submissionETag(fixture: SubmissionRuntimeSqlFixture, stageKey: String?): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            InformationRequestETag.submissionOf(stageKey, contentCollector.collect(request, stageKey).contentHash)
        }

    private fun contributor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.contributorUserId), AuthorizationContext(sessionRef = "contributor-session"))

    private fun attestor(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.attestorUserId), AuthorizationContext(sessionRef = "attestor-session"))

    private fun assent(fixture: SubmissionRuntimeSqlFixture, etag: String) =
        RecordInformationRequestSubmissionAttestationCommand(
            requestId = fixture.requestId,
            requirementId = fixture.attestationRequirementId,
            decision = InformationRequestAttestationDecision.ASSENTED,
            access = attestor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = "assent-${UUID.randomUUID()}",
        )

    private fun submit(fixture: SubmissionRuntimeSqlFixture, stageKey: String?, etag: String, key: String) =
        SubmitInformationRequestPackageCommand(
            requestId = fixture.requestId,
            stageKey = stageKey,
            access = contributor(fixture),
            precondition = CommandPrecondition.ExpectedRevision(etag),
            idempotencyKey = key,
        )

    private fun services(
        fixture: SubmissionRuntimeSqlFixture,
        history: InformationRequestTransitionHistoryService = transitionHistory,
    ): SubmissionServices
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val grants = mock<InformationRequestExecutionGrantService>()
        whenever(grants.findForRequest(fixture.requestId)).thenReturn(
            RequestExecutionGrant().apply {
                requestId = fixture.requestId
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            },
        )
        val gate = InformationRequestMutationGate(requestRepository, exchangeRepository, authorization, mock(), grants)
        val evidenceGate = InformationRequestEvidenceGate(
            requestRepository = requestRepository,
            exchangeRepository = exchangeRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            templateRequirementRepository = templateRequirementRepository,
            authorizationService = authorization,
            entitlementGuard = mock(),
            executionGrantService = grants,
            lockService = lockService,
        )
        val readiness = InformationRequestSubmissionReadinessEvaluator(completenessProgressService, attestationEvaluationService, gate)
        return SubmissionServices(
            submissions = InformationRequestSubmissionService(
                gate = gate,
                contentCollector = contentCollector,
                readinessEvaluator = readiness,
                lockService = lockService,
                requestRepository = requestRepository,
                packageRepository = packageRepository,
                itemRepository = itemRepository,
                evidenceRepository = evidenceRepository,
                linkRepository = linkRepository,
                packageAttestationRepository = packageAttestationRepository,
                withdrawalRepository = withdrawalRepository,
                packageReader = packageReader,
                commandReceiptService = commandReceiptService,
                transitionHistory = history,
                clock = clock,
                entityManager = entityManager,
            ),
            attestations = InformationRequestSubmissionAttestationService(
                gate = gate,
                requirementRepository = requirementRepository,
                revisionRepository = revisionRepository,
                occurrenceRepository = occurrenceRepository,
                templateVersionRepository = templateVersionRepository,
                templateRequirementRepository = templateRequirementRepository,
                bindingRepository = bindingRepository,
                attestationRepository = attestationRepository,
                policyLoader = policyLoader,
                evaluationService = attestationEvaluationService,
                contentCollector = contentCollector,
                stages = stages,
                lockService = lockService,
                requirementContext = requirementContext,
                commandReceiptService = commandReceiptService,
                transitionHistory = history,
                clock = clock,
            ),
            evidence = InformationRequestEvidenceCollectionService(
                gate = evidenceGate,
                artifactRepository = artifactRepository,
                commandReceiptService = commandReceiptService,
                transitionHistory = history,
                viewLoader = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService),
            ),
            responses = InformationRequestResponseDraftService(
                requestRepository = requestRepository,
                exchangeRepository = exchangeRepository,
                requirementRepository = requirementRepository,
                occurrenceRepository = occurrenceRepository,
                revisionRepository = revisionRepository,
                dispositionRepository = dispositionRepository,
                bindingRepository = bindingRepository,
                responseStore = responseRepository,
                schemaAssignmentService = schemaAssignmentService,
                schemaAssignmentRepository = schemaAssignmentRepository,
                fieldValueSetRepository = fieldValueSetRepository,
                fieldContractRepository = fieldContractRepository,
                authorizationService = authorization,
                commandReceiptService = commandReceiptService,
                entitlementGuard = mock(),
                executionGrantService = grants,
                transitionHistory = history,
                conditionEvaluationService = conditionEvaluationService,
                structuredResponseValidationService = structuredResponseValidationService,
                lockService = lockService,
            ),
        )
    }

    private fun submissionQuery(
        fixture: SubmissionRuntimeSqlFixture,
        hiddenRequirementId: UUID? = null,
    ): InformationRequestSubmissionQueryService
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
            val action = invocation.getArgument<Action>(1)
            val resource = invocation.getArgument<ResourceRef>(2)
            if (action == Action.INFORMATION_REQUEST_REQUIREMENT_VIEW && resource.id == hiddenRequirementId)
                Decision.Deny("HIDDEN", "Hidden from this caller")
            else
                Decision.Allow()
        }
        val grants = mock<InformationRequestExecutionGrantService>()
        val gate = InformationRequestMutationGate(requestRepository, exchangeRepository, authorization, mock(), grants)
        return InformationRequestSubmissionQueryService(
            queryService = InformationRequestQueryService(exchangeRepository, requestRepository, authorization, mock(), grants),
            exchangeRepository = exchangeRepository,
            packageReader = packageReader,
            contentCollector = contentCollector,
            stages = stages,
            readinessEvaluator = InformationRequestSubmissionReadinessEvaluator(completenessProgressService, attestationEvaluationService, gate),
            lockService = lockService,
            requirementContext = requirementContext,
            fieldValueRevisions = fieldValueRevisionQueryService,
            gate = gate,
        )
    }

    private data class SubmissionServices(
        val submissions: InformationRequestSubmissionService,
        val attestations: InformationRequestSubmissionAttestationService,
        val evidence: InformationRequestEvidenceCollectionService,
        val responses: InformationRequestResponseDraftService,
    )
}
