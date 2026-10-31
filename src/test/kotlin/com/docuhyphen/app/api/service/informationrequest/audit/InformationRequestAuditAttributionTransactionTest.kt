package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandActorRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.audit.model.KeyedAuditTransition
import com.docuhyphen.app.api.service.informationrequest.audit.model.RecordedAuditAttribution
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.evidence.*
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAuditAttributionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var versionRepository: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var contentCollector: InformationRequestSubmissionContentCollector

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Inject
    lateinit var history: InformationRequestTransitionHistoryService

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Inject
    lateinit var occurrenceRepository: InformationRequestGroupOccurrenceRepository

    @Inject
    lateinit var templateRequirementRepository: InformationRequestTemplateRequirementRepository

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var evidenceVersionRepository: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var documentVersionRecordingService: DocumentVersionRecordingService

    @Inject
    lateinit var intake: InformationRequestEvidenceIntake

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    @Inject
    lateinit var accessContexts: InformationRequestAccessContextFactory

    private val published by lazy {
        PublishedRequestSupport(
            dataSource,
            requestRepository,
            requirementRepository,
            definitionRepository,
            versionRepository,
            capabilityRepository,
            configurationWriter,
            materializer,
        )
    }

    @Test
    fun `a respondent's save and submission through an access link are audited as the public link, its participant, and its session`() {
        val request = issue("link-respondent-save-request")
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val respondent = request.principal(CONTRIBUTOR)
        val (access, accessSession) = linkAccess(respondent)

        val save = saveCommand(request, access, "respondent-save")
        QuarkusTransaction.requiringNew().call { services.responses.patch(save) }
        QuarkusTransaction.requiringNew().call { services.responses.patch(save) }
        assertEquals(2, transitionCount(request.requestId))
        submit(services, request, access, "respondent-submit")

        assertEquals(
            listOf(
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_START.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_SUBMIT.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_CLOSE.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
            ),
            recorded(request.requestId),
        )
        val saveReceipt = receiptId(respondent.id, "respondent-save")
        val submitReceipt = receiptId(respondent.id, "respondent-submit")
        assertEquals(
            listOf(
                KeyedAuditTransition(InformationRequestMutation.START_RESPONSE.name, "respondent-save", saveReceipt),
                KeyedAuditTransition(InformationRequestMutation.SAVE_RESPONSE.name, "respondent-save", saveReceipt),
                KeyedAuditTransition(InformationRequestMutation.SUBMIT.name, "respondent-submit", submitReceipt),
                KeyedAuditTransition(InformationRequestMutation.CLOSE.name, "respondent-submit", submitReceipt),
            ),
            keyed(request.requestId),
        )
    }

    @Test
    fun `a respondent's evidence upload through an access link is audited as the public link, its participant, and its session`() {
        val fixture = InformationRequestEvidencePostgresFixture().insertInto(dataSource)
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val (access, accessSession) = linkAccess(respondent)
        val uploads = uploads(fixture)

        QuarkusTransaction.requiringNew().call {
            uploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.requirementId,
                    access = access,
                    surface = InformationRequestEvidenceSurface.NO_AUTH,
                    precondition = CommandPrecondition.ExpectedRevision("\"${fixture.requirementId}:0\""),
                    idempotencyKey = "respondent-upload",
                    file = evidenceFile("collected content"),
                    attributes = InformationRequestEvidenceAttributes.NONE,
                ),
            )
        }

        assertEquals(
            listOf(
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_START.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_EVIDENCE_ADMINISTER.key,
                    "PUBLIC_LINK",
                    "participant:${respondent.id}",
                    accessSession,
                    respondent.id
                ),
            ),
            recorded(fixture.requestId),
        )
        val uploadReceipt = receiptId(respondent.id, "respondent-upload")
        assertEquals(
            listOf(
                KeyedAuditTransition(
                    InformationRequestMutation.START_RESPONSE.name,
                    "respondent-upload",
                    uploadReceipt
                ),
                KeyedAuditTransition(
                    InformationRequestMutation.ADMINISTER_EVIDENCE.name,
                    "respondent-upload",
                    uploadReceipt
                ),
            ),
            keyed(fixture.requestId),
        )
    }

    @Test
    fun `an owner's mutation is audited under the owner's account label and the user session it arrived on`() {
        val request = issue("owner-cancel-request")
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService)
        val userSession = UUID.randomUUID().toString()
        val owner =
            RequestAccessContext(PrincipalRef.user(request.ownerId), AuthorizationContext(sessionRef = userSession))
        val aggregate = QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(request.requestId)))
        }

        QuarkusTransaction.requiringNew().call {
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = request.requestId,
                    reasonCode = "no_longer_needed",
                    access = owner,
                    precondition = CommandPrecondition.ExpectedRevision(aggregate),
                    idempotencyKey = "owner-cancel",
                ),
            )
        }

        val ownerEmail =
            dataSource.connection.use { queryString(it, "SELECT email FROM app_user WHERE id = ?", request.ownerId) }
        assertEquals(
            listOf(
                RecordedAuditAttribution(
                    AuditEventType.INFORMATION_REQUEST_CANCEL.key,
                    "HUMAN",
                    ownerEmail,
                    userSession,
                    request.ownerId
                )
            ),
            recorded(request.requestId),
        )
        assertEquals(
            listOf(
                KeyedAuditTransition(
                    InformationRequestMutation.CANCEL.name,
                    "owner-cancel",
                    receiptId(request.ownerId, "owner-cancel")
                )
            ),
            keyed(request.requestId),
        )
    }

    @Test
    fun `a mutation that fails inside its command leaves neither a receipt nor a transition`() {
        val request = issue("refused-command-request")
        val respondent = request.principal(CONTRIBUTOR)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(request.requestId),
            operation = "refused-information-request-save",
            actor = CommandActorRef.principal(respondent),
            idempotencyKey = "refused-save",
            requestFingerprint = CommandRequestFingerprint.sha256Hex("refused-save"),
        )

        val refusal = assertThrows(Throwable::class.java) {
            QuarkusTransaction.requiringNew().run {
                commandReceiptService.runOnce<Unit>(receipt) {
                    val stored = requireNotNull(requestRepository.findById(request.requestId))
                    history.record(
                        InformationRequestTransitionHistoryCommand(
                            request = stored,
                            fromState = stored.state,
                            toState = stored.state,
                            mutation = InformationRequestMutation.SAVE_RESPONSE,
                            actor = InformationRequestAuditActor.of(
                                RequestAccessContext(
                                    respondent,
                                    AuthorizationContext(sessionRef = UUID.randomUUID().toString())
                                ),
                            ),
                            auditIdempotencyKey = "information_request.refused_save|${request.requestId}",
                        ),
                    )
                    error(REFUSAL)
                }
            }
        }

        assertTrue(generateSequence(refusal) { it.cause }.any { it is IllegalStateException && it.message == REFUSAL })
        dataSource.connection.use { connection ->
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM command_receipt WHERE resource_id = ? AND idempotency_key = 'refused-save'",
                    request.requestId
                )
            )
            assertEquals(0, transitionCount(request.requestId))
            assertEquals(
                0,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                    request.requestId.toString(),
                    AuditEventType.INFORMATION_REQUEST_REQUIREMENT_RESPOND.key,
                ),
            )
        }
    }

    private fun linkAccess(respondent: PrincipalRef): Pair<RequestAccessContext, String> {
        val link = ShareLink()
        val session = RequestAccessSession().apply {
            shareLinkId = link.id
            participantPrincipalKind = respondent.kind
            participantPrincipalId = respondent.id
            credentialHash = "stored-credential-digest"
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        return accessContexts.fromBootstrapSession(link, session, respondent) to session.id.toString()
    }

    private fun issue(templateKey: String): PublishedRequest =
        published.issue(
            templateKey,
            listOf(ANSWER),
            listOf(CONTRIBUTOR),
            setOf(CONTRIBUTOR)
        ) { schemaVersionId, fields ->
            configuration(schemaVersionId, fields.getValue(ANSWER).fieldDefinitionId)
        }

    private fun saveCommand(request: PublishedRequest, access: RequestAccessContext, key: String) =
        PatchInformationRequestResponsesCommand(
            requestId = request.requestId,
            access = access,
            precondition = CommandPrecondition.ExpectedRevision(published.responseETag(request)),
            idempotencyKey = key,
            patches = listOf(
                InformationRequestResponsePatch(
                    requirementId = published.requirement(request, ANSWER).id,
                    disposition = InformationRequestResponseDisposition.PROVIDED,
                    fieldValues = ResponseFieldValuesPatch(
                        listOf(
                            FieldValueEntry(
                                request.fields.getValue(ANSWER).fieldContractId,
                                JsonPrimitive("collected answer")
                            )
                        ),
                        FieldsPrecondition.Unconditioned,
                    ),
                ),
            ),
        )

    private fun submit(
        services: InformationRequestRuntimeServices,
        request: PublishedRequest,
        access: RequestAccessContext,
        key: String
    ) =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(request.requestId))
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = request.requestId,
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(null, contentCollector.collect(stored, null).contentHash),
                    ),
                    idempotencyKey = key,
                ),
            )
        }

    private fun uploads(fixture: InformationRequestEvidencePostgresFixture): InformationRequestEvidenceUploadService {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val grants = mock<InformationRequestExecutionGrantService>()
        whenever(grants.findForRequest(fixture.requestId)).thenReturn(
            issuedExecutionGrant().apply {
                requestId = fixture.requestId
                ownerType = "ORGANIZATION"
                planCode = "BUSINESS"
                subscriptionStatus = "ACTIVE"
                enforcementMode = "ENFORCE"
                issuedAt = Timestamp.from(Instant.now())
            },
        )
        return InformationRequestEvidenceUploadService(
            gate = InformationRequestEvidenceGate(
                requestRepository = requestRepository,
                exchangeLookup = exchangeLookup,
                requirementRepository = requirementRepository,
                occurrenceRepository = occurrenceRepository,
                templateRequirementRepository = templateRequirementRepository,
                authorizationService = authorization,
                continuationGuard = InformationRequestContinuationGuard(grants, mock()),
                lockService = lockService,
                requirementContext = mock(),
                conditionVisibility = InformationRequestConditionVisibility(mock(), mock()),
            ),
            artifactRepository = artifactRepository,
            versionRepository = evidenceVersionRepository,
            documentVersionRecordingService = documentVersionRecordingService,
            commandReceiptService = commandReceiptService,
            transitionHistory = history,
            viewLoader = InformationRequestEvidenceViewLoader(
                evidenceVersionRepository,
                documentVersionRecordingService
            ),
            intake = intake,
            noAuthAccess = InformationRequestNoAuthAccessDoubles.current(),
        )
    }

    private fun evidenceFile(content: String): InformationRequestEvidenceFile =
        InformationRequestEvidenceFile(
            File.createTempFile("attributed-upload", ".pdf").apply {
                deleteOnExit()
                writeText(content)
            },
            "collected-record.pdf",
            "application/pdf",
            DocumentEncryptionMode.INTERNAL,
        )

    private fun recorded(requestId: UUID): List<RecordedAuditAttribution> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT a.event_type_key, a.actor_kind, a.actor_label, a.session_id, a.actor_id
                FROM audit_outbox a
                         JOIN information_request_transition t ON t.id::text = a.business_transaction_id
                WHERE t.information_request_id = ?
                ORDER BY t.sequence_number
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) RecordedAuditAttribution(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getString(3),
                            rows.getString(4),
                            rows.getObject(5, UUID::class.java),
                        )
                        else null
                    }.toList()
                }
            }
        }

    private fun keyed(requestId: UUID): List<KeyedAuditTransition> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT mutation, idempotency_key, command_receipt_id FROM information_request_transition WHERE information_request_id = ? ORDER BY sequence_number",
            ).use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) KeyedAuditTransition(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getObject(3, UUID::class.java)
                        )
                        else null
                    }.toList()
                }
            }
        }

    private fun receiptId(actorId: UUID, idempotencyKey: String): UUID =
        dataSource.connection.use { connection ->
            UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        "SELECT id::text FROM command_receipt WHERE actor_id = ? AND idempotency_key = ?",
                        actorId,
                        idempotencyKey,
                    ),
                ),
            )
        }

    private fun transitionCount(requestId: UUID): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_transition WHERE information_request_id = ?",
                requestId
            )
        }

    private fun configuration(schemaVersionId: UUID, fieldId: UUID) = InformationRequestTemplateConfigurationRequest(
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "answers",
                title = "Answers",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = ANSWER,
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "Provide the collected answer",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        collectedFieldDefinitionId = fieldId,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            ),
        ),
    )

    private companion object {
        const val CONTRIBUTOR = "CONTRIBUTOR"
        const val ANSWER = "collected-answer"
        const val REFUSAL = "The save is refused after its history was written"
    }
}
