package com.docuhyphen.app.api.service.informationrequest.model

import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.externalsource.*
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSuccessorService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestGeneratedOutputService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.review.*
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionReadinessEvaluator
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService

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
    val evidenceUploads: InformationRequestEvidenceUploadService,
    val responses: InformationRequestResponseDraftService,
    val groupOccurrences: InformationRequestGroupOccurrenceService,
    val submissionQueries: InformationRequestSubmissionQueryService,
    val amendments: InformationRequestAmendmentService,
    val amendmentQueries: InformationRequestAmendmentQueryService,
    val lifecycle: InformationRequestLifecycleService,
    val successors: InformationRequestSuccessorService,
    val followUps: InformationRequestFollowUpService,
    val lineageQueries: InformationRequestLineageQueryService,
    val connectorExchanges: InformationRequestConnectorService,
    val connectorWorker: InformationRequestConnectorWorker,
    val importedValueIntake: InformationRequestImportedValueIntakeService,
    val importedValueDecisions: InformationRequestImportedValueDecisionService,
    val importedValueReconciliation: InformationRequestImportedValueReconciliationService,
    val importedValueRecords: InformationRequestImportedValueRecords,
    val generatedOutputs: InformationRequestGeneratedOutputService,
)
