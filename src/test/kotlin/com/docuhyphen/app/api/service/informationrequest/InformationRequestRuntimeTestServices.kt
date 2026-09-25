package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionSupportingLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestRuntimeTestServices
{
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
    @Inject lateinit var partyRepository: InformationRequestPartyRepository
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
    @Inject lateinit var amendmentTargetResolver: InformationRequestAmendmentTargetResolver
    @Inject lateinit var projectionLoader: InformationRequestTemplateProjectionLoader
    @Inject lateinit var capabilityGate: InformationRequestTemplateCapabilityGate
    @Inject lateinit var amendmentGuard: InformationRequestAmendmentGuard
    @Inject lateinit var materializer: InformationRequestTemplateMaterializer
    @Inject lateinit var amendmentRecorder: InformationRequestAmendmentRecorder
    @Inject lateinit var amendmentReader: InformationRequestAmendmentReader
    @Inject lateinit var partyService: InformationRequestPartyService
    @Inject lateinit var lineageRepository: InformationRequestLineageRepository
    @Inject lateinit var carryForwardRepository: InformationRequestCarryForwardRepository
    @Inject lateinit var recurrenceRepository: InformationRequestRecurrenceRepository
    @Inject lateinit var refreshRuleRepository: InformationRequestRefreshRuleRepository
    @Inject lateinit var executionUsageReservationService: InformationRequestExecutionUsageReservationService

    fun build(
        requestId: UUID,
        history: InformationRequestTransitionHistoryService = transitionHistory,
        hiddenRequirementId: UUID? = null,
    ): InformationRequestRuntimeServices
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
        whenever(grants.findForRequest(requestId)).thenReturn(
            RequestExecutionGrant().apply {
                this.requestId = requestId
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            },
        )
        whenever(grants.issueGrant(any(), any())).thenAnswer { invocation ->
            RequestExecutionGrant().apply {
                this.requestId = invocation.getArgument<InformationRequest>(0).id
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            }
        }
        val gate = InformationRequestMutationGate(requestRepository, exchangeRepository, authorization, mock(), grants)
        val readiness = InformationRequestSubmissionReadinessEvaluator(completenessProgressService, attestationEvaluationService, gate)
        val queries = InformationRequestQueryService(exchangeRepository, requestRepository, authorization, mock(), grants)
        val lifecycle = InformationRequestLifecycleService(
            requestRepository, exchangeRepository, authorization, commandReceiptService, capabilityGate, history, mock(), grants,
            partyRepository, executionUsageReservationService,
        )
        val successors = InformationRequestSuccessorService(
            gate,
            InformationRequestDraftFactory(requestRepository, materializer, mock(), history, clock),
            partyRepository, partyService, packageReader, lockService, requirementRepository, templateRequirementRepository,
            lineageRepository, carryForwardRepository, requestRepository, lifecycle, commandReceiptService, history, clock,
        )
        return InformationRequestRuntimeServices(
            gate = gate,
            readiness = readiness,
            submissions = InformationRequestSubmissionService(
                gate, contentCollector, readiness, lockService, requestRepository, packageRepository, itemRepository,
                evidenceRepository, linkRepository, packageAttestationRepository, withdrawalRepository, packageReader,
                commandReceiptService, history, clock, entityManager,
            ),
            attestations = InformationRequestSubmissionAttestationService(
                gate, requirementRepository, revisionRepository, occurrenceRepository, templateVersionRepository,
                templateRequirementRepository, bindingRepository, attestationRepository, policyLoader,
                attestationEvaluationService, contentCollector, stages, lockService, requirementContext,
                commandReceiptService, history, clock,
            ),
            evidence = InformationRequestEvidenceCollectionService(
                gate = InformationRequestEvidenceGate(
                    requestRepository, exchangeRepository, requirementRepository, occurrenceRepository,
                    templateRequirementRepository, authorization, mock(), grants, lockService,
                ),
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
            submissionQueries = InformationRequestSubmissionQueryService(
                queries, exchangeRepository, packageReader, contentCollector, stages, readiness, lockService,
                requirementContext, fieldValueRevisionQueryService, gate,
            ),
            amendments = InformationRequestAmendmentService(
                gate, requestRepository, amendmentTargetResolver, projectionLoader, capabilityGate, amendmentGuard,
                materializer, amendmentRecorder, amendmentReader, commandReceiptService, history, clock,
            ),
            amendmentQueries = InformationRequestAmendmentQueryService(queries, amendmentReader, requirementRepository, partyRepository, gate),
            lifecycle = lifecycle,
            successors = successors,
            followUps = InformationRequestFollowUpService(
                gate, successors, recurrenceRepository, refreshRuleRepository, lineageRepository, requirementRepository,
                templateRequirementRepository, commandReceiptService, history, clock,
            ),
            lineageQueries = InformationRequestLineageQueryService(
                queries, lineageRepository, carryForwardRepository, itemRepository, fieldValueRevisionQueryService, gate,
            ),
        )
    }
}

data class InformationRequestRuntimeServices(
    val gate: InformationRequestMutationGate,
    val readiness: InformationRequestSubmissionReadinessEvaluator,
    val submissions: InformationRequestSubmissionService,
    val attestations: InformationRequestSubmissionAttestationService,
    val evidence: InformationRequestEvidenceCollectionService,
    val responses: InformationRequestResponseDraftService,
    val submissionQueries: InformationRequestSubmissionQueryService,
    val amendments: InformationRequestAmendmentService,
    val amendmentQueries: InformationRequestAmendmentQueryService,
    val lifecycle: InformationRequestLifecycleService,
    val successors: InformationRequestSuccessorService,
    val followUps: InformationRequestFollowUpService,
    val lineageQueries: InformationRequestLineageQueryService,
)
