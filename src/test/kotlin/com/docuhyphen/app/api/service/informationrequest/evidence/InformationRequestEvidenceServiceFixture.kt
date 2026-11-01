package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceStoredUsage
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAccess
import org.mockito.kotlin.*
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

internal class InformationRequestEvidenceServiceFixture
{
    val exchange = Exchange().apply {
        id = UUID.randomUUID()
        status = ExchangeStatus.ACCEPTED_STARTED
    }

    val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = exchange.id
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.ISSUED
    }

    val requirements = mutableListOf<InformationRequestRequirement>()

    val documentTemplateRequirement =
        templateRequirement("supporting-record", InformationRequestRequirementType.DOCUMENT)
    val fieldTemplateRequirement = templateRequirement("recorded-summary", InformationRequestRequirementType.FIELD)

    val documentRequirement = requirement(documentTemplateRequirement, "root")
    val fieldRequirement = requirement(fieldTemplateRequirement, "root")
    val groupRequirement = requirement(documentTemplateRequirement, "reported-item[1]")

    val grant = issuedExecutionGrant().apply {
        requestId = request.id
        ownerType = "ORGANIZATION"
        planCode = "BUSINESS"
        subscriptionStatus = "ACTIVE"
        enforcementMode = "ENFORCE"
        issuedAt = Timestamp.from(Instant.now())
    }

    val respondent = PrincipalRef.participant(UUID.randomUUID())
    val access = RequestAccessContext(respondent, AuthorizationContext(sessionRef = "verified-session"))

    val requestRepository: InformationRequestRepository = mock()
    val exchangeLookup: ExchangeLookupService = mock()
    val requirementRepository: InformationRequestRequirementRepository = mock()
    val occurrenceRepository: InformationRequestGroupOccurrenceRepository = mock()
    val templateRequirementRepository: InformationRequestTemplateRequirementRepository = mock()
    val authorizationService: AuthorizationService = mock()
    val requirementContext: InformationRequestRequirementAuthorizationContextProvider = mock()
    val entitlementGuard: InformationRequestEntitlementGuard = mock()
    val executionGrantService: InformationRequestExecutionGrantService = mock()
    val artifactRepository: InformationRequestEvidenceArtifactRepository = mock()
    val versionRepository: InformationRequestEvidenceVersionRepository = mock()
    val documentVersionRecordingService: DocumentVersionRecordingService = mock()
    val transitionHistory: InformationRequestTransitionHistoryService = mock()
    val assessmentRepository: InformationRequestEvidenceAssessmentRepository = mock()
    val receipts = InMemoryEvidenceReceiptStore()
    val commandReceiptService = CommandReceiptService(receipts, CommandReceiptScope())

    val artifacts = mutableListOf<InformationRequestEvidenceArtifact>()
    val versions = mutableListOf<InformationRequestEvidenceVersion>()
    val documentVersions = mutableMapOf<UUID, DocumentVersion>()
    val recordedUploads = mutableListOf<DocumentVersionUpload>()
    val assessments = mutableListOf<InformationRequestEvidenceAssessment>()

    var malwareScanRequired = true
    val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy = mock()
    val policyLoader: InformationRequestEvidencePolicyLoader = mock()
    val uploadLimits = InformationRequestEvidenceUploadLimits(
        maximumFileBytes = 1_048_576,
        maximumNoAuthFileBytes = 524_288,
        maximumRequestFiles = 50,
        maximumRequestBytes = 10_485_760,
        maximumPartyFiles = 25,
        maximumPartyBytes = 5_242_880,
    )

    val viewLoader = InformationRequestEvidenceViewLoader(versionRepository, documentVersionRecordingService)

    val gate = InformationRequestEvidenceGate(
        requestRepository = requestRepository,
        exchangeLookup = exchangeLookup,
        requirementRepository = requirementRepository,
        occurrenceRepository = occurrenceRepository,
        templateRequirementRepository = templateRequirementRepository,
        authorizationService = authorizationService,
        continuationGuard = InformationRequestContinuationGuard(executionGrantService, entitlementGuard),
        lockService = mock(),
        requirementContext = requirementContext,
        conditionVisibility = InformationRequestConditionVisibility(mock(), mock()),
    )

    init
    {
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(exchangeLookup.lockExchangeForUpdate(exchange.id)).thenReturn(exchange)
        listOf(documentRequirement, fieldRequirement, groupRequirement).forEach { requirement ->
            whenever(requirementRepository.findById(requirement.id)).thenReturn(requirement)
        }
        whenever(requirementRepository.findForRequest(request.id)).thenAnswer { requirements.toList() }
        whenever(documentVersionRecordingService.open(any())).thenAnswer { invocation ->
            val version = invocation.arguments[0] as DocumentVersion
            val upload = recordedUploads.first { it.expectedDigest.value == version.contentHash }
            DocumentVersionContent(DocumentVersionReadHandle.storedFile(upload.file), upload.fileName)
        }
        listOf(documentTemplateRequirement, fieldTemplateRequirement).forEach { templateRequirement ->
            whenever(templateRequirementRepository.findById(templateRequirement.id)).thenReturn(templateRequirement)
        }
        whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(emptyList())
        whenever(executionGrantService.findForRequest(request.id)).thenReturn(grant)
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        whenever(artifactRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as InformationRequestEvidenceArtifact).also { artifacts += it }
        }
        whenever(artifactRepository.update(any())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(artifactRepository.findForRequirement(any())).thenAnswer { invocation ->
            artifacts.filter { it.informationRequestRequirementId == invocation.arguments[0] }
        }
        whenever(artifactRepository.findForRequest(any())).thenAnswer { invocation ->
            artifacts.filter { it.informationRequestId == invocation.arguments[0] }
        }
        whenever(artifactRepository.findById(any())).thenAnswer { invocation ->
            artifacts.firstOrNull { it.id == invocation.arguments[0] }
        }
        whenever(artifactRepository.findByIdForUpdate(any())).thenAnswer { invocation ->
            artifacts.firstOrNull { it.id == invocation.arguments[0] }
        }
        whenever(versionRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as InformationRequestEvidenceVersion).also { versions += it }
        }
        whenever(versionRepository.findForArtifact(any())).thenAnswer { invocation ->
            versions.filter { it.evidenceArtifactId == invocation.arguments[0] }.sortedBy { it.versionNumber }
        }
        whenever(versionRepository.findLatest(any())).thenAnswer { invocation ->
            versions.filter { it.evidenceArtifactId == invocation.arguments[0] }.maxByOrNull { it.versionNumber }
        }
        whenever(versionRepository.findById(any())).thenAnswer { invocation ->
            versions.firstOrNull { it.id == invocation.arguments[0] }
        }
        whenever(documentVersionRecordingService.recordStandaloneDocument(any())).thenAnswer { invocation ->
            recorded(Document().apply { title = "stored" }, invocation.arguments[0] as DocumentVersionUpload, 1)
        }
        whenever(documentVersionRecordingService.recordNextVersion(any(), any())).thenAnswer { invocation ->
            val document = invocation.arguments[0] as Document
            recorded(
                document,
                invocation.arguments[1] as DocumentVersionUpload,
                documentVersions.values.count { requireNotNull(it.document).id == document.id } + 1,
            )
        }
        whenever(documentVersionRecordingService.findVersion(any())).thenAnswer { invocation ->
            documentVersions[invocation.arguments[0]]
        }
        whenever(assessmentRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as InformationRequestEvidenceAssessment).also { assessments += it }
        }
        whenever(assessmentRepository.findForVersions(any())).thenAnswer { invocation ->
            val versionIds = invocation.arguments[0] as Collection<*>
            assessments.filter { it.evidenceVersionId in versionIds }.sortedBy { it.assessedAt }
        }
        whenever(deploymentPolicy.malwareScanRequired()).thenAnswer { malwareScanRequired }
        whenever(versionRepository.storedUsageForRequest(any())).thenAnswer { invocation ->
            storedUsage(versions.filter { it.informationRequestId == invocation.arguments[0] })
        }
        whenever(versionRepository.storedUsageForUploaders(any(), any())).thenAnswer { invocation ->
            val uploaders = invocation.arguments[1] as Set<*>
            storedUsage(
                versions.filter {
                    it.informationRequestId == invocation.arguments[0] &&
                            PrincipalRef(it.createdByPrincipalKind, it.createdByPrincipalId) in uploaders
                },
            )
        }
        whenever(requirementContext.linkedIdentitiesOf(any())).thenAnswer { invocation ->
            setOf(invocation.arguments[0] as PrincipalRef)
        }
    }

    val inspector: InformationRequestEvidenceContentInspector = spy(InformationRequestEvidenceContentInspector())

    val intake = InformationRequestEvidenceIntake(
        deploymentPolicy = deploymentPolicy,
        limits = uploadLimits,
        executionGrantService = executionGrantService,
        requirementContext = requirementContext,
        policyLoader = policyLoader,
        inspector = inspector,
        artifactRepository = artifactRepository,
        versionRepository = versionRepository,
        assessmentRepository = assessmentRepository,
        documentVersionRecordingService = documentVersionRecordingService,
        clock = Clock.systemUTC(),
    )

    val contentRelease =
        InformationRequestEvidenceContentRelease(assessmentRepository, deploymentPolicy, requirementContext)
    val scanAuditRecorder: AuditRecorder = mock()
    val actorDisplays = mock<PrincipalDisplayService>()
    val scanAudit = InformationRequestEvidenceScanAudit(
        scanAuditRecorder,
        InformationRequestAuditActorLabelResolver(actorDisplays),
        requestRepository
    )
    val substituteRepository: InformationRequestTemplateBindingSubstituteRepository = mock()
    val responseStore: InformationRequestResponseStore = mock()

    val evaluationService = InformationRequestEvidenceEvaluationService(
        policyLoader = policyLoader,
        requirementRepository = requirementRepository,
        substituteRepository = substituteRepository,
        artifactRepository = artifactRepository,
        versionRepository = versionRepository,
        assessmentRepository = assessmentRepository,
        documentVersionRecordingService = documentVersionRecordingService,
        deploymentPolicy = deploymentPolicy,
        bindingRepository = mock(),
        conditionEvaluationService = mock(),
        clock = Clock.systemUTC(),
        factsReader = InformationRequestEvidenceFactsReader(
            artifactRepository,
            versionRepository,
            assessmentRepository,
            documentVersionRecordingService,
        ),
    )

    fun uploadService() = InformationRequestEvidenceUploadService(
        gate = gate,
        artifactRepository = artifactRepository,
        versionRepository = versionRepository,
        documentVersionRecordingService = documentVersionRecordingService,
        commandReceiptService = commandReceiptService,
        transitionHistory = transitionHistory,
        viewLoader = viewLoader,
        intake = intake,
        noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
    )

    fun collectionService() = InformationRequestEvidenceCollectionService(
        gate = gate,
        artifactRepository = artifactRepository,
        commandReceiptService = commandReceiptService,
        transitionHistory = transitionHistory,
        viewLoader = viewLoader,
        noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
    )

    fun queryService(
        requestQueries: InformationRequestQueryService,
        auditRecorder: AuditRecorder,
        noAuthAccess: InformationRequestNoAuthReadAccessService = InformationRequestNoAuthAccessDoubles.current(),
    ) =
        InformationRequestEvidenceQueryService(
            queryService = requestQueries,
            gate = gate,
            artifactRepository = artifactRepository,
            versionRepository = versionRepository,
            viewLoader = viewLoader,
            contentRelease = contentRelease,
            evaluationService = evaluationService,
            responseStore = responseStore,
            contentOpener = contentOpener(auditRecorder, noAuthAccess),
        )

    fun retainedQueryService(
        requestQueries: InformationRequestQueryService,
        auditRecorder: AuditRecorder,
        noAuthAccess: InformationRequestNoAuthReadAccessService = InformationRequestNoAuthAccessDoubles.current(),
        reviewAccess: InformationRequestReviewAccess = mock(),
    ) =
        InformationRequestRetainedEvidenceQueryService(
            queryService = requestQueries,
            gate = gate,
            artifactRepository = artifactRepository,
            versionRepository = versionRepository,
            documentVersionRecordingService = documentVersionRecordingService,
            contentRelease = contentRelease,
            contentOpener = contentOpener(auditRecorder, noAuthAccess),
            reviewAccess = reviewAccess,
        )

    private fun contentOpener(auditRecorder: AuditRecorder, noAuthAccess: InformationRequestNoAuthReadAccessService) =
        InformationRequestEvidenceContentOpener(
            documentVersionRecordingService = documentVersionRecordingService,
            accessAudit = InformationRequestEvidenceAccessAudit(
                auditRecorder,
                InformationRequestAuditActorLabelResolver(actorDisplays)
            ),
            noAuthAccess = noAuthAccess,
        )

    fun evidenceFile(
        content: String = "collected record content",
        fileName: String = "collected-record.pdf",
        mediaType: String? = "application/pdf",
        encryptionMode: DocumentEncryptionMode = DocumentEncryptionMode.INTERNAL,
    ): InformationRequestEvidenceFile
    {
        val file = Files.createTempFile("evidence-upload", ".pdf").toFile().apply {
            deleteOnExit()
            writeText(content)
        }
        return InformationRequestEvidenceFile(file, fileName, mediaType, encryptionMode)
    }

    fun additionalDocumentRequirement(path: String = "root"): InformationRequestRequirement
    {
        val requirement = requirement(documentTemplateRequirement, path)
        whenever(requirementRepository.findById(requirement.id)).thenReturn(requirement)
        return requirement
    }

    fun expectedEvidenceETag(requirement: InformationRequestRequirement = documentRequirement): CommandPrecondition =
        CommandPrecondition.ExpectedRevision(
            InformationRequestETag.evidenceOf(
                requirement.id,
                artifacts.filter { it.informationRequestRequirementId == requirement.id },
            ),
        )

    fun expectedArtifactETag(artifact: InformationRequestEvidenceArtifact): CommandPrecondition =
        CommandPrecondition.ExpectedRevision(InformationRequestETag.artifactOf(artifact))

    fun recordedTransitions(): List<InformationRequestTransitionHistoryCommand>
    {
        val captor = argumentCaptor<InformationRequestTransitionHistoryCommand>()
        return runCatching {
            verify(transitionHistory, atLeast(1)).record(captor.capture())
            captor.allValues
        }.getOrDefault(emptyList())
    }

    private fun storedUsage(stored: List<InformationRequestEvidenceVersion>): InformationRequestEvidenceStoredUsage
    {
        val contents = stored.mapNotNull { version -> documentVersions[version.documentVersionId] }
        return InformationRequestEvidenceStoredUsage(contents.size.toLong(), contents.sumOf { it.contentLength })
    }

    private fun recorded(document: Document, upload: DocumentVersionUpload, number: Int): DocumentVersion
    {
        recordedUploads += upload
        val version = DocumentVersion().apply {
            this.document = document
            fileName = upload.fileName
            version = "$number"
            createdDate = Timestamp.from(Instant.now())
        }
        DocumentVersionCreatorMapper.recordOn(version, upload.creator)
        DocumentVersionContentIdentityMapper.recordOn(
            version,
            StoredDocumentVersionContent(
                ObjectStoreDocumentVersionLocator("document-versions/${document.id}/${version.id}/record.pdf"),
                upload.expectedDigest,
            ),
            DocumentVersionContentVerification.forEncryptionMode(upload.encryptionMode),
        )
        documentVersions[version.id] = version
        return version
    }

    private fun templateRequirement(key: String, type: InformationRequestRequirementType) =
        InformationRequestTemplateRequirement().apply {
            templateDefinitionId = UUID.randomUUID()
            requirementKey = key
            requirementType = type
        }

    private fun requirement(templateRequirement: InformationRequestTemplateRequirement, path: String) =
        InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = templateRequirement.id
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = path
        }.also { requirements += it }

    companion object
    {
        val NO_ATTRIBUTES = InformationRequestEvidenceAttributes.NONE
    }
}

internal class InMemoryEvidenceReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
