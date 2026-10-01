package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestConnectorExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestGeneratedOutputRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueDiscrepancyResolutionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestImportedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestCarryForwardRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewCommentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDraftItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionSupportingLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactEvidenceService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactStanding
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentGuard
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentReader
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentRecorder
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentTargetResolver
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationEvaluationService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateCapabilityGate
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestDraftFactory
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceGate
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceViewLoader
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnector
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorRegistry
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorWorker
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueCanonicalizer
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSatisfactionService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSuccessorService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestGeneratedOutputService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAccess
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewLoader
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewOpeningService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewSeparationPolicy
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewSettlement
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionPackageReader
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionReadinessEvaluator
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionStages
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
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
    @Inject lateinit var reviewOpening: InformationRequestReviewOpeningService
    @Inject lateinit var satisfaction: InformationRequestSatisfactionService
    @Inject lateinit var reviewLoader: InformationRequestReviewLoader
    @Inject lateinit var reviewSeparation: InformationRequestReviewSeparationPolicy
    @Inject lateinit var reviewSettlement: InformationRequestReviewSettlement
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var reviewAssignmentRepository: InformationRequestReviewAssignmentRepository
    @Inject lateinit var reviewDraftRepository: InformationRequestReviewDraftItemRepository
    @Inject lateinit var reviewDecisionRepository: InformationRequestReviewDecisionRepository
    @Inject lateinit var reviewFindingRepository: InformationRequestReviewFindingRepository
    @Inject lateinit var reviewCommentRepository: InformationRequestReviewCommentRepository
    @Inject lateinit var correctionRepository: InformationRequestCorrectionRepository
    @Inject lateinit var correctionItemRepository: com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionItemRepository
    @Inject lateinit var correctionEvidenceRepository: com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionEvidenceRepository
    @Inject lateinit var remediationRepository: com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRemediationRepository
    @Inject lateinit var groupMemberRepository: com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
    @Inject lateinit var factRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
    @Inject lateinit var factRevocationRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRevocationRepository
    @Inject lateinit var factStanding: InformationRequestAcceptedFactStanding
    @Inject lateinit var factEvidenceService: InformationRequestAcceptedFactEvidenceService
    @Inject lateinit var subjectRestrictions: InformationRequestSubjectRestrictionService
    @Inject lateinit var schemaFieldBindingRepository: com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
    @Inject lateinit var recertificationRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestFactRecertificationRepository
    @Inject lateinit var recertificationEvidenceRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestFactRecertificationEvidenceRepository
    @Inject lateinit var businessDecisionRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestBusinessDecisionRepository
    @Inject lateinit var connectorExchangeRepository: InformationRequestConnectorExchangeRepository
    @Inject lateinit var importedValueRepository: InformationRequestImportedValueRepository
    @Inject lateinit var importedValueDecisionRepository: InformationRequestImportedValueDecisionRepository
    @Inject lateinit var discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository
    @Inject lateinit var discrepancyResolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository
    @Inject lateinit var generatedOutputRepository: InformationRequestGeneratedOutputRepository
    @Inject lateinit var importedValueCanonicalizer: InformationRequestImportedValueCanonicalizer

    @Suppress("LongParameterList")
    fun build(
        requestId: UUID,
        history: InformationRequestTransitionHistoryService = transitionHistory,
        hiddenRequirementId: UUID? = null,
        denies: (PrincipalRef, Action) -> Boolean = { _, _ -> false },
        centralAuthorization: AuthorizationService? = null,
        responseValidation: InformationRequestStructuredResponseValidationService = structuredResponseValidationService,
        connectors: List<InformationRequestConnector> = emptyList(),
        externalClock: Clock = clock,
    ): InformationRequestRuntimeServices
    {
        val authorization = centralAuthorization ?: stubbedAuthorization(hiddenRequirementId, denies)
        return services(requestId, history, authorization, mock(), responseValidation, ExternalSourceSetup(connectors, externalClock))
    }

    private fun stubbedAuthorization(
        hiddenRequirementId: UUID?,
        denies: (PrincipalRef, Action) -> Boolean,
    ): AuthorizationService
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
            val principal = invocation.getArgument<PrincipalRef>(0)
            val action = invocation.getArgument<Action>(1)
            val resource = invocation.getArgument<ResourceRef>(2)
            if (action == Action.INFORMATION_REQUEST_REQUIREMENT_VIEW && resource.id == hiddenRequirementId)
                Decision.Deny("HIDDEN", "Hidden from this caller")
            else if (denies(principal, action))
                Decision.Deny("DENIED", "Denied to this caller")
            else
                Decision.Allow()
        }
        return authorization
    }

    private fun services(
        requestId: UUID,
        history: InformationRequestTransitionHistoryService,
        authorization: AuthorizationService,
        grants: InformationRequestExecutionGrantService,
        responseValidation: InformationRequestStructuredResponseValidationService,
        external: ExternalSourceSetup,
    ): InformationRequestRuntimeServices
    {
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
        val queries = InformationRequestQueryService(exchangeRepository, requestRepository, authorization)
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
        val acceptedFactQueries = InformationRequestAcceptedFactQueryService(
            queries, gate, factStanding, factRepository, partyRepository, requirementRepository, bindingRepository,
            templateRequirementRepository, templateVersionRepository, subjectRestrictions,
        )
        val responses = InformationRequestResponseDraftService(
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
            structuredResponseValidationService = responseValidation,
            lockService = lockService,
        )
        val reviewAccess = InformationRequestReviewAccess(gate, partyRepository, reviewAssignmentRepository, requirementContext)
        val connectorRegistry = InformationRequestConnectorRegistry(external.connectors)
        val importedValues = InformationRequestImportedValueService(
            gate, queries, importedValueRepository, importedValueDecisionRepository, discrepancyRepository,
            discrepancyResolutionRepository, requirementRepository, importedValueCanonicalizer, responseRepository,
            commandReceiptService, history, external.clock,
        )
        return InformationRequestRuntimeServices(
            reviewAssignments = InformationRequestReviewAssignmentService(
                gate, reviewAccess, reviewLoader, reviewSeparation, reviewSettlement, reviewRepository,
                reviewAssignmentRepository, requestRepository, commandReceiptService, history, clock, entityManager,
            ),
            reviewDecisions = InformationRequestReviewDecisionService(
                gate, reviewAccess, reviewLoader, reviewSeparation, reviewSettlement, reviewRepository,
                reviewAssignmentRepository, reviewDraftRepository, reviewDecisionRepository, dispositionRepository,
                commandReceiptService, history, clock, entityManager,
            ),
            reviewFindings = InformationRequestReviewFindingService(
                gate, reviewAccess, reviewLoader, reviewRepository, reviewFindingRepository, commandReceiptService, history, clock,
            ),
            reviewComments = InformationRequestReviewCommentService(
                gate, reviewAccess, reviewLoader, reviewCommentRepository, commandReceiptService, history, clock,
            ),
            reviewQueries = InformationRequestReviewQueryService(
                queries, gate, reviewAccess, reviewLoader, lockService, reviewRepository, reviewAssignmentRepository,
                reviewDraftRepository, reviewFindingRepository, reviewCommentRepository, correctionRepository,
                correctionItemRepository, correctionEvidenceRepository, remediationRepository, requestRepository,
                groupMemberRepository, fieldValueRevisionQueryService,
            ),
            acceptedFacts = InformationRequestAcceptedFactService(
                gate, reviewLoader, lockService, packageReader, factRepository, factRevocationRepository, partyRepository,
                bindingRepository, fieldValueRevisionQueryService, factStanding, factEvidenceService, subjectRestrictions, commandReceiptService, history, clock,
            ),
            acceptedFactQueries = acceptedFactQueries,
            factRecertifications = InformationRequestFactRecertificationService(
                gate, factRepository, acceptedFactQueries, responses, requirementRepository, schemaAssignmentRepository,
                schemaFieldBindingRepository, fieldContractRepository, schemaAssignmentService, recertificationRepository,
                recertificationEvidenceRepository, commandReceiptService, history, clock,
            ),
            businessDecisions = InformationRequestBusinessDecisionService(
                gate, queries, businessDecisionRepository, commandReceiptService, history, clock,
            ),
            reviewCycles = InformationRequestReviewCycleService(
                gate, reviewLoader, reviewOpening, lockService, reviewRepository, correctionRepository, requestRepository,
                commandReceiptService, clock, entityManager,
            ),
            gate = gate,
            readiness = readiness,
            submissions = InformationRequestSubmissionService(
                gate, contentCollector, readiness, lockService, requestRepository, packageRepository, itemRepository,
                evidenceRepository, linkRepository, packageAttestationRepository, withdrawalRepository, packageReader,
                commandReceiptService, history, reviewOpening, satisfaction, clock, entityManager,
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
            responses = responses,
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
                queries, lineageRepository, recurrenceRepository, carryForwardRepository, itemRepository, fieldValueRevisionQueryService, gate,
            ),
            connectorExchanges = InformationRequestConnectorService(
                gate, queries, connectorRegistry, connectorExchangeRepository, requirementRepository, commandReceiptService, history, external.clock,
            ),
            connectorWorker = InformationRequestConnectorWorker(
                connectorRegistry, connectorExchangeRepository, gate, importedValues, entityManager, external.clock,
            ),
            importedValues = importedValues,
            generatedOutputs = InformationRequestGeneratedOutputService(
                gate, queries, generatedOutputRepository, packageRepository, commandReceiptService, history, external.clock,
            ),
        )
    }

    data class ExternalSourceSetup(val connectors: List<InformationRequestConnector>, val clock: Clock)
}

data class InformationRequestRuntimeServices(
    val reviewAssignments: InformationRequestReviewAssignmentService,
    val reviewDecisions: InformationRequestReviewDecisionService,
    val reviewFindings: InformationRequestReviewFindingService,
    val reviewComments: InformationRequestReviewCommentService,
    val reviewCycles: InformationRequestReviewCycleService,
    val reviewQueries: InformationRequestReviewQueryService,
    val acceptedFacts: InformationRequestAcceptedFactService,
    val acceptedFactQueries: InformationRequestAcceptedFactQueryService,
    val factRecertifications: InformationRequestFactRecertificationService,
    val businessDecisions: InformationRequestBusinessDecisionService,
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
    val connectorExchanges: InformationRequestConnectorService,
    val connectorWorker: InformationRequestConnectorWorker,
    val importedValues: InformationRequestImportedValueService,
    val generatedOutputs: InformationRequestGeneratedOutputService,
)
