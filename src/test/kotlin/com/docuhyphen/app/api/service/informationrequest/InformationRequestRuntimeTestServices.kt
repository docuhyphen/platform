package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.*
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.*
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.submission.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.*
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAuthorActionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.amendment.*
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationEvaluationService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateCapabilityGate
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockHold
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockIssueRebaser
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockStopper
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestDraftFactory
import com.docuhyphen.app.api.service.informationrequest.evidence.*
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.externalsource.*
import com.docuhyphen.app.api.service.informationrequest.lifecycle.*
import com.docuhyphen.app.api.service.informationrequest.model.ExternalSourceSetup
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupAuthorizationService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceMaterializer
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceStructure
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestCallerPartyResolver
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyMaterializationService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestTrustedRecipientEligibilityService
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestGeneratedOutputService
import com.docuhyphen.app.api.service.informationrequest.response.*
import com.docuhyphen.app.api.service.informationrequest.review.*
import com.docuhyphen.app.api.service.informationrequest.submission.*
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestRuntimeTestServices
{
    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var revisionRepository: InformationRequestRequirementRevisionRepository

    @Inject
    lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var templateVersionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var bindingRepository: InformationRequestTemplateRequirementBindingRepository

    @Inject
    lateinit var packageRepository: InformationRequestSubmissionPackageRepository

    @Inject
    lateinit var itemRepository: InformationRequestSubmissionItemRepository

    @Inject
    lateinit var evidenceRepository: InformationRequestSubmissionEvidenceRepository

    @Inject
    lateinit var linkRepository: InformationRequestSubmissionSupportingLinkRepository

    @Inject
    lateinit var packageAttestationRepository: InformationRequestSubmissionPackageAttestationRepository

    @Inject
    lateinit var attestationRepository: InformationRequestSubmissionAttestationRepository

    @Inject
    lateinit var withdrawalRepository: InformationRequestSubmissionWithdrawalRepository

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var partyQueries: InformationRequestPartyQueryService

    @Inject
    lateinit var contentCollector: InformationRequestSubmissionContentCollector

    @Inject
    lateinit var completenessProgressService: InformationRequestCompletenessProgressService

    @Inject
    lateinit var attestationEvaluationService: InformationRequestAttestationEvaluationService

    @Inject
    lateinit var groupCardinality: InformationRequestSubmissionGroupCardinalityEvaluator

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    @Inject
    lateinit var packageReader: InformationRequestSubmissionPackageReader

    @Inject
    lateinit var policyLoader: InformationRequestAttestationPolicyLoader

    @Inject
    lateinit var evidencePolicyLoader: InformationRequestEvidencePolicyLoader

    @Inject
    lateinit var stages: InformationRequestSubmissionStages

    @Inject
    lateinit var requirementContext: InformationRequestRequirementAuthorizationContextProvider

    @Inject
    lateinit var titleReader: InformationRequestTitleReader

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var exchangeLifecycleState: ExchangeLifecycleStateService

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var versionRepository: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var documentVersionRecordingService: DocumentVersionRecordingService

    @Inject
    lateinit var dispositionRepository: InformationRequestTemplateBindingDispositionRepository

    @Inject
    lateinit var responseRepository: InformationRequestResponseRepository

    @Inject
    lateinit var schemaAssignmentService: SchemaAssignmentService

    @Inject
    lateinit var fieldsReader: FieldsReadFacade

    @Inject
    lateinit var fieldAudience: InformationRequestFieldAudienceGuard

    @Inject
    lateinit var conditionEvaluationService: InformationRequestConditionEvaluationService

    @Inject
    lateinit var structuredResponseValidationService: InformationRequestStructuredResponseValidationService

    @Inject
    lateinit var validationContextLoader: InformationRequestStructuredResponseValidationContextLoader

    @Inject
    lateinit var hiddenResponseArchiver: InformationRequestHiddenResponseArchiver

    @Inject
    lateinit var conditionEffectRecorder: InformationRequestConditionEffectRecorder

    @Inject
    lateinit var fieldValueRevisionQueryService: FieldValueRevisionQueryService

    @Inject
    lateinit var amendmentTargetResolver: InformationRequestAmendmentTargetResolver

    @Inject
    lateinit var projectionLoader: InformationRequestTemplateProjectionLoader

    @Inject
    lateinit var capabilityGate: InformationRequestTemplateCapabilityGate

    @Inject
    lateinit var amendmentGuard: InformationRequestAmendmentGuard

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var amendmentRecorder: InformationRequestAmendmentRecorder

    @Inject
    lateinit var amendmentReader: InformationRequestAmendmentReader

    @Inject
    lateinit var partyMaterialization: InformationRequestPartyMaterializationService

    @Inject
    lateinit var standingService: InformationRequestExecutionStandingService

    @Inject
    lateinit var trustedRecipients: InformationRequestTrustedRecipientEligibilityService

    @Inject
    lateinit var lineageRepository: InformationRequestLineageRepository

    @Inject
    lateinit var carryForwardRepository: InformationRequestCarryForwardRepository

    @Inject
    lateinit var sourcePackageRepository: InformationRequestLineageSourcePackageRepository

    @Inject
    lateinit var recurrenceRepository: InformationRequestRecurrenceRepository

    @Inject
    lateinit var refreshRuleRepository: InformationRequestRefreshRuleRepository

    @Inject
    lateinit var executionUsageReservationService: InformationRequestExecutionUsageReservationService

    @Inject
    lateinit var reviewOpening: InformationRequestReviewOpeningService

    @Inject
    lateinit var satisfaction: InformationRequestSatisfactionService

    @Inject
    lateinit var reviewLoader: InformationRequestReviewLoader

    @Inject
    lateinit var reviewSeparation: InformationRequestReviewSeparationPolicy

    @Inject
    lateinit var reviewDelegateCandidates: InformationRequestReviewDelegateCandidates

    @Inject
    lateinit var reviewSettlement: InformationRequestReviewSettlement

    @Inject
    lateinit var reviewReopenPolicy: InformationRequestReviewReopenPolicy

    @Inject
    lateinit var evidenceQueries: InformationRequestEvidenceQueryService

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var reviewAssignmentRepository: InformationRequestReviewAssignmentRepository

    @Inject
    lateinit var reviewDraftRepository: InformationRequestReviewDraftItemRepository

    @Inject
    lateinit var reviewDecisionRepository: InformationRequestReviewDecisionRepository

    @Inject
    lateinit var reviewFindingRepository: InformationRequestReviewFindingRepository

    @Inject
    lateinit var reviewCommentRepository: InformationRequestReviewCommentRepository

    @Inject
    lateinit var correctionRepository: InformationRequestCorrectionRepository

    @Inject
    lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository

    @Inject
    lateinit var correctionEvidenceRepository: InformationRequestCorrectionEvidenceRepository

    @Inject
    lateinit var remediationRepository: InformationRequestReviewRemediationRepository

    @Inject
    lateinit var principalGroups: PrincipalGroupService

    @Inject
    lateinit var callerParties: InformationRequestCallerPartyResolver

    @Inject
    lateinit var factRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository

    @Inject
    lateinit var factRevocationRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRevocationRepository

    @Inject
    lateinit var factStanding: InformationRequestAcceptedFactStanding

    @Inject
    lateinit var factEvidenceService: InformationRequestAcceptedFactEvidenceService

    @Inject
    lateinit var subjectRestrictions: InformationRequestSubjectRestrictionService

    @Inject
    lateinit var recertificationRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestFactRecertificationRepository

    @Inject
    lateinit var recertificationEvidenceRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestFactRecertificationEvidenceRepository

    @Inject
    lateinit var businessDecisionRepository: com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestBusinessDecisionRepository

    @Inject
    lateinit var connectorExchangeRepository: InformationRequestConnectorExchangeRepository

    @Inject
    lateinit var importedValueRepository: InformationRequestImportedValueRepository

    @Inject
    lateinit var importedValueDecisionRepository: InformationRequestImportedValueDecisionRepository

    @Inject
    lateinit var discrepancyRepository: InformationRequestImportedValueDiscrepancyRepository

    @Inject
    lateinit var discrepancyResolutionRepository: InformationRequestImportedValueDiscrepancyResolutionRepository

    @Inject
    lateinit var reconciliationRunRepository: InformationRequestImportedValueReconciliationRunRepository

    @Inject
    lateinit var reconciliationRunItemRepository: InformationRequestImportedValueReconciliationRunItemRepository

    @Inject
    lateinit var generatedOutputRepository: InformationRequestGeneratedOutputRepository

    @Inject
    lateinit var importedValueCanonicalizer: InformationRequestImportedValueCanonicalizer

    @Inject
    lateinit var clockStopper: InformationRequestClockStopper

    @Inject
    lateinit var clockIssueRebaser: InformationRequestClockIssueRebaser

    @Inject
    lateinit var clockHold: InformationRequestClockHold

    @Inject
    lateinit var factValueFit: InformationRequestFactValueFit

    @Inject
    lateinit var evidenceIntake: InformationRequestEvidenceIntake

    @Inject
    lateinit var groupOccurrenceMaterializer: InformationRequestGroupOccurrenceMaterializer

    @Inject
    lateinit var groupOccurrenceStructure: InformationRequestGroupOccurrenceStructure

    @Inject
    lateinit var groupAuthorization: InformationRequestGroupAuthorizationService

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
        executionGrants: InformationRequestExecutionGrantService? = null,
    ): InformationRequestRuntimeServices
    {
        val authorization = centralAuthorization ?: stubbedAuthorization(hiddenRequirementId, denies)
        return services(
            requestId, history, authorization, executionGrants ?: stubbedGrants(requestId), responseValidation,
            ExternalSourceSetup(connectors, externalClock),
        )
    }

    private fun stubbedGrants(requestId: UUID): InformationRequestExecutionGrantService {
        val grants = mock<InformationRequestExecutionGrantService>()
        whenever(grants.findForRequest(requestId)).thenReturn(
            issuedExecutionGrant().apply {
                this.requestId = requestId
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            },
        )
        whenever(grants.issueGrant(any(), any())).thenAnswer { invocation ->
            issuedExecutionGrant().apply {
                this.requestId = invocation.getArgument<InformationRequest>(0).id
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            }
        }
        return grants
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
        val gate = InformationRequestMutationGate(
            requestRepository,
            exchangeLookup,
            authorization,
            mock(),
            InformationRequestContinuationGuard(grants, mock()),
        )
        val disclosure = InformationRequestRequirementDisclosure(gate, requirementRepository)
        val readiness = InformationRequestSubmissionReadinessEvaluator(
            completenessProgressService,
            attestationEvaluationService,
            gate,
            responseValidation,
            validationContextLoader,
            groupCardinality,
        )
        val queries = InformationRequestQueryService(exchangeLookup, requestRepository, authorization)
        val lifecycle = InformationRequestLifecycleService(
            requestRepository,
            exchangeLookup,
            authorization,
            commandReceiptService,
            capabilityGate,
            history,
            InformationRequestContinuationGuard(grants, mock()),
            grants,
            partyRepository,
            executionUsageReservationService,
            clockStopper,
            clockIssueRebaser,
            trustedRecipients,
            mock(),
            fieldAudience,
        )
        val successors = InformationRequestSuccessorService(
            gate,
            InformationRequestDraftFactory(requestRepository, materializer, mock(), history, clock),
            partyRepository,
            partyMaterialization,
            packageReader,
            lockService,
            requirementRepository,
            templateRequirementRepository,
            templateVersionRepository,
            lineageRepository,
            carryForwardRepository,
            sourcePackageRepository,
            requestRepository,
            lifecycle,
            commandReceiptService,
            history,
            clock,
        )
        val acceptedFactQueries = InformationRequestAcceptedFactQueryService(
            queries, gate, factStanding, factRepository, partyRepository, requirementRepository, bindingRepository,
            templateRequirementRepository, templateVersionRepository, subjectRestrictions, disclosure,
            dispositionRepository, factValueFit,
        )
        val conditionVisibility = InformationRequestConditionVisibility(conditionEvaluationService, bindingRepository)
        val evidenceGate = InformationRequestEvidenceGate(
            requestRepository, exchangeLookup, requirementRepository, occurrenceRepository,
            templateRequirementRepository, authorization, InformationRequestContinuationGuard(grants, mock()),
            lockService, requirementContext, conditionVisibility,
        )
        val evidenceViews = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService)
        val evidenceCollection = InformationRequestEvidenceCollectionService(
            gate = evidenceGate,
            artifactRepository = artifactRepository,
            commandReceiptService = commandReceiptService,
            transitionHistory = history,
            viewLoader = evidenceViews,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )
        val evidenceUploads = InformationRequestEvidenceUploadService(
            evidenceGate, artifactRepository, versionRepository, documentVersionRecordingService, commandReceiptService,
            history, evidenceViews, evidenceIntake, InformationRequestNoAuthAccessDoubles.current(),
        )
        val responseMutationGuard = InformationRequestResponseMutationGuard(
            requestRepository, exchangeLookup, InformationRequestContinuationGuard(grants, mock()),
        )
        val groupOccurrences = InformationRequestGroupOccurrenceService(
            requestRepository,
            occurrenceRepository,
            requirementRepository,
            revisionRepository,
            groupOccurrenceMaterializer,
            groupOccurrenceStructure,
            responseMutationGuard,
            authorization,
            commandReceiptService,
            history,
            groupAuthorization,
            lockService,
            responseValidation,
            validationContextLoader,
            InformationRequestNoAuthAccessDoubles.current(),
        )
        val activeResponses = InformationRequestActiveResponseReader(
            requirementRepository,
            occurrenceRepository,
            bindingRepository,
            responseRepository,
            conditionEvaluationService,
        )
        val hiddenResponsePolicies = InformationRequestHiddenResponsePolicyEnforcer(
            conditionEvaluationService,
            bindingRepository,
            revisionRepository,
            responseRepository,
            schemaAssignmentService,
            authorization,
            hiddenResponseArchiver,
            fieldsReader,
            InformationRequestHiddenResponseFiles(artifactRepository, versionRepository, evidenceGate),
            evidenceCollection,
            lockService,
            conditionVisibility,
        )
        val responses = InformationRequestResponseDraftService(
            requestRepository = requestRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            revisionRepository = revisionRepository,
            responseStore = responseRepository,
            mutationGuard = responseMutationGuard,
            patchValidator = InformationRequestResponsePatchValidator(dispositionRepository, responseRepository),
            fieldWriter = InformationRequestResponseFieldWriter(
                schemaAssignmentService,
                fieldsReader,
                bindingRepository,
                activeResponses
            ),
            activeResponses = activeResponses,
            authorizationService = authorization,
            commandReceiptService = commandReceiptService,
            transitionHistory = history,
            structuredResponseValidationService = responseValidation,
            validationContextLoader = validationContextLoader,
            lockService = lockService,
            hiddenResponsePolicies = hiddenResponsePolicies,
            conditionEffectRecorder = conditionEffectRecorder,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )
        val reviewAccess =
            InformationRequestReviewAccess(
                gate,
                partyQueries,
                reviewAssignmentRepository,
                reviewRepository,
                requirementContext,
                disclosure
            )
        val connectorRegistry = InformationRequestConnectorRegistry(external.connectors)
        val importedValueRecords = InformationRequestImportedValueRecords(
            gate, queries, importedValueRepository, importedValueDecisionRepository, discrepancyRepository,
            discrepancyResolutionRepository, requirementRepository, commandReceiptService, history, disclosure,
        )
        val importedValueIntake = InformationRequestImportedValueIntakeService(
            gate, importedValueRecords, importedValueRepository, revisionRepository, importedValueCanonicalizer,
            external.clock,
        )
        return InformationRequestRuntimeServices(
            reviewAssignments = InformationRequestReviewAssignmentService(
                gate, reviewAccess, reviewLoader, reviewSeparation, reviewSettlement, reviewRepository,
                reviewAssignmentRepository, commandReceiptService, history, clock, entityManager,
            ),
            reviewDecisions = InformationRequestReviewDecisionService(
                gate, reviewAccess, reviewLoader, reviewSeparation, reviewSettlement, reviewRepository,
                reviewAssignmentRepository, reviewDraftRepository, reviewDecisionRepository, dispositionRepository,
                commandReceiptService, history, clock, entityManager, disclosure,
            ),
            reviewFindings = InformationRequestReviewFindingService(
                gate,
                reviewAccess,
                reviewLoader,
                reviewRepository,
                reviewFindingRepository,
                commandReceiptService,
                history,
                clock,
                disclosure,
            ),
            reviewComments = InformationRequestReviewCommentService(
                gate,
                reviewAccess,
                reviewLoader,
                reviewCommentRepository,
                commandReceiptService,
                history,
                clock,
                disclosure,
                InformationRequestNoAuthAccessDoubles.current(),
            ),
            reviewQueries = InformationRequestReviewQueryService(
                queries,
                gate,
                reviewAccess,
                reviewLoader,
                reviewReopenPolicy,
                reviewRepository,
                reviewAssignmentRepository,
                reviewDraftRepository,
                reviewFindingRepository,
                reviewCommentRepository,
                correctionRepository,
                correctionItemRepository,
                correctionEvidenceRepository,
                remediationRepository,
                requestRepository,
                callerParties,
                standingService,
                fieldValueRevisionQueryService,
                evidenceQueries,
                reviewDelegateCandidates,
                dispositionRepository,
                titleReader,
                exchangeLookup,
            ),
            acceptedFacts = InformationRequestAcceptedFactService(
                gate,
                reviewLoader,
                lockService,
                packageReader,
                factRepository,
                factRevocationRepository,
                partyRepository,
                bindingRepository,
                fieldValueRevisionQueryService,
                factStanding,
                factEvidenceService,
                subjectRestrictions,
                commandReceiptService,
                history,
                clock,
                disclosure,
            ),
            acceptedFactQueries = acceptedFactQueries,
            factRecertifications = InformationRequestFactRecertificationService(
                gate, factRepository, acceptedFactQueries, responses, requirementRepository, fieldsReader,
                schemaAssignmentService, recertificationRepository,
                recertificationEvidenceRepository, commandReceiptService, history, clock,
                InformationRequestNoAuthAccessDoubles.current(),
            ),
            businessDecisions = InformationRequestBusinessDecisionService(
                gate, queries, businessDecisionRepository, commandReceiptService, history, clock,
            ),
            reviewCycles = InformationRequestReviewCycleService(
                gate,
                reviewLoader,
                reviewOpening,
                reviewReopenPolicy,
                reviewRepository,
                correctionRepository,
                requestRepository,
                commandReceiptService,
                clock,
                entityManager,
                InformationRequestNoAuthAccessDoubles.current(),
                clockHold,
                contentCollector,
            ),
            gate = gate,
            readiness = readiness,
            submissions = InformationRequestSubmissionService(
                gate, contentCollector, readiness, lockService, requestRepository, packageRepository, itemRepository,
                evidenceRepository, linkRepository, packageAttestationRepository, withdrawalRepository, packageReader,
                commandReceiptService, history, reviewOpening, satisfaction, clock, entityManager,
                InformationRequestNoAuthAccessDoubles.current(), clockHold,
            ),
            attestations = InformationRequestSubmissionAttestationService(
                gate, requirementRepository, revisionRepository, occurrenceRepository, templateVersionRepository,
                templateRequirementRepository, bindingRepository, attestationRepository, policyLoader,
                attestationEvaluationService, contentCollector, stages, lockService, requirementContext,
                commandReceiptService, history, clock,
                InformationRequestNoAuthAccessDoubles.current(), conditionVisibility,
            ),
            evidence = evidenceCollection,
            evidenceUploads = evidenceUploads,
            responses = responses,
            groupOccurrences = groupOccurrences,
            submissionQueries = InformationRequestSubmissionQueryService(
                queries, exchangeLookup, packageReader, contentCollector, stages, readiness, lockService,
                requirementContext, fieldValueRevisionQueryService, gate, evidenceQueries, reviewOpening,
            ),
            amendments = InformationRequestAmendmentService(
                gate, requestRepository, amendmentTargetResolver, projectionLoader, capabilityGate, amendmentGuard,
                materializer, amendmentRecorder, amendmentReader, commandReceiptService, history, clock,
                requirementRepository, hiddenResponsePolicies, fieldAudience,
            ),
            amendmentQueries = InformationRequestAmendmentQueryService(
                queries,
                amendmentReader,
                requirementRepository,
                partyRepository,
                gate,
                requirementContext
            ),
            lifecycle = lifecycle,
            successors = successors,
            followUps = InformationRequestFollowUpService(
                gate,
                successors,
                recurrenceRepository,
                refreshRuleRepository,
                lineageRepository,
                requirementRepository,
                templateRequirementRepository,
                commandReceiptService,
                history,
                evidencePolicyLoader,
                clock,
                requestRepository,
            ),
            lineageQueries = InformationRequestLineageQueryService(
                queries,
                lineageRepository,
                recurrenceRepository,
                carryForwardRepository,
                itemRepository,
                fieldValueRevisionQueryService,
                gate,
                refreshRuleRepository,
                requestRepository,
                InformationRequestAuthorActionService(authorization, exchangeLifecycleState),
                clock,
            ),
            connectorExchanges = InformationRequestConnectorService(
                gate,
                queries,
                connectorRegistry,
                connectorExchangeRepository,
                requirementRepository,
                commandReceiptService,
                history,
                external.clock,
                disclosure,
            ),
            connectorWorker = InformationRequestConnectorWorker(
                connectorRegistry,
                connectorExchangeRepository,
                gate,
                importedValueIntake,
                InformationRequestConnectorExchangeRecorder(history),
                entityManager,
                external.clock,
            ),
            importedValueIntake = importedValueIntake,
            importedValueDecisions = InformationRequestImportedValueDecisionService(
                gate, importedValueRecords, importedValueDecisionRepository, external.clock,
            ),
            importedValueReconciliation = InformationRequestImportedValueReconciliationService(
                gate,
                importedValueRecords,
                importedValueRepository,
                importedValueDecisionRepository,
                discrepancyRepository,
                discrepancyResolutionRepository,
                reconciliationRunRepository,
                reconciliationRunItemRepository,
                requirementRepository,
                importedValueCanonicalizer,
                responseRepository,
                commandReceiptService,
                external.clock,
            ),
            importedValueRecords = importedValueRecords,
            generatedOutputs = InformationRequestGeneratedOutputService(
                gate,
                queries,
                generatedOutputRepository,
                packageRepository,
                commandReceiptService,
                history,
                external.clock,
            ),
        )
    }
}
