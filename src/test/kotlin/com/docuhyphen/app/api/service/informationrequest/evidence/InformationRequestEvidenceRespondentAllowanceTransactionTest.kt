package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestParticipantAccountUpgradeService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationWriter
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.security.SecureRandom
import java.time.Clock
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceRespondentAllowanceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var requirements: InformationRequestRequirementRepository

    @Inject
    lateinit var definitions: InformationRequestTemplateDefinitionRepository

    @Inject
    lateinit var templateVersions: InformationRequestTemplateVersionRepository

    @Inject
    lateinit var capabilities: InformationRequestTemplateVersionCapabilityRepository

    @Inject
    lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter

    @Inject
    lateinit var materializer: InformationRequestTemplateMaterializer

    @Inject
    lateinit var artifacts: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var evidenceVersions: InformationRequestEvidenceVersionRepository

    @Inject
    lateinit var assessments: InformationRequestEvidenceAssessmentRepository

    @Inject
    lateinit var documents: DocumentVersionRecordingService

    @Inject
    lateinit var receipts: CommandReceiptService

    @Inject
    lateinit var history: InformationRequestTransitionHistoryService

    @Inject
    lateinit var gate: InformationRequestEvidenceGate

    @Inject
    lateinit var deployment: InformationRequestEvidenceDeploymentPolicy

    @Inject
    lateinit var policies: InformationRequestEvidencePolicyLoader

    @Inject
    lateinit var inspector: InformationRequestEvidenceContentInspector

    @Inject
    lateinit var grants: InformationRequestExecutionGrantService

    @Inject
    lateinit var requirementContext: InformationRequestRequirementAuthorizationContextProvider

    @Inject
    lateinit var noAuth: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var collection: InformationRequestEvidenceCollectionService

    @Inject
    lateinit var upgrades: InformationRequestParticipantAccountUpgradeService

    @Inject
    lateinit var clock: Clock

    private val support by lazy {
        PublishedRequestSupport(
            dataSource,
            requests,
            requirements,
            definitions,
            templateVersions,
            capabilities,
            configurationWriter,
            materializer,
        )
    }

    @Test
    fun `a respondent cannot store more files than its allowance`() {
        val request = issue()
        val uploads = uploads(respondentFiles = 1)
        upload(uploads, request, request.access(RESPONDENT))

        assertLimitExceeded { upload(uploads, request, request.access(RESPONDENT)) }
    }

    @Test
    fun `linking an account keeps the respondent's file allowance`() {
        val request = issue()
        val uploads = uploads(respondentFiles = 1)
        upload(uploads, request, request.access(RESPONDENT))
        val account = linkAccount(request)

        assertLimitExceeded { upload(uploads, request, account) }
    }

    @Test
    fun `linking an account keeps the respondent's byte allowance`() {
        val request = issue()
        val first = recordFile()
        val uploads = uploads(respondentBytes = first.file.length())
        upload(uploads, request, request.access(RESPONDENT), first)
        val account = linkAccount(request)

        assertLimitExceeded { upload(uploads, request, account) }
    }

    @Test
    fun `a different respondent keeps its own allowance`() {
        val request = issue(OTHER_RESPONDENT)
        val uploads = uploads(respondentFiles = 1)
        upload(uploads, request, request.access(RESPONDENT))

        assertDoesNotThrow { upload(uploads, request, request.access(OTHER_RESPONDENT)) }
    }

    @Test
    fun `linking an account does not lift the request allowance`() {
        val request = issue()
        val uploads = uploads(requestFiles = 1)
        upload(uploads, request, request.access(RESPONDENT))
        val account = linkAccount(request)

        assertLimitExceeded { upload(uploads, request, account) }
    }

    @Test
    fun `a linked account cannot replace the participant's file beyond the respondent's allowance`() {
        val request = issue()
        val uploads = uploads(respondentFiles = 1)
        val provided = upload(uploads, request, request.access(RESPONDENT))
        val account = linkAccount(request)

        assertLimitExceeded { replace(uploads, request, account, provided) }
    }

    @Test
    fun `a linked account's replacement counts toward the same allowance and each version keeps its uploader`() {
        val request = issue()
        val uploads = uploads(respondentFiles = 2)
        val participant = request.access(RESPONDENT)
        val provided = upload(uploads, request, participant)
        val account = linkAccount(request)
        replace(uploads, request, account, provided)

        assertEquals(listOf(participant.principal, account.principal), uploadersOf(provided))
        assertLimitExceeded { upload(uploads, request, account) }
    }

    @Test
    fun `withdrawn files still count toward the respondent's allowance after linking`() {
        val request = issue()
        val uploads = uploads(respondentFiles = 1)
        val participant = request.access(RESPONDENT)
        val provided = upload(uploads, request, participant)
        withdraw(request, participant, provided)
        val account = linkAccount(request)

        assertLimitExceeded { upload(uploads, request, account) }
    }

    @Test
    fun `a participant linked through an earlier request shares one allowance with that account`() {
        val request = issue()
        val participant = request.access(RESPONDENT)
        val accountId = support.outsider()
        val earlierLink = upgrade(earlierRequestTo(request), accountId)
        val uploads = uploads(respondentFiles = 2)
        upload(uploads, request, participant)

        assertEquals(earlierLink.id, upgrade(request, accountId).id)
        val account = accountAccess(accountId)
        upload(uploads, request, account)
        assertLimitExceeded { upload(uploads, request, account) }
        assertThrows(ForbiddenException::class.java) { upload(uploads, request, participant) }
    }

    private fun issue(vararg otherRespondents: String): PublishedRequest =
        support.issue(
            "respondent-allowance",
            emptyList(),
            listOf(RESPONDENT) + otherRespondents,
            participants = setOf(RESPONDENT),
        ) { _, _ ->
            InformationRequestTemplateConfigurationRequest(
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        "records", "Records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                REQUIREMENT_KEY, InformationRequestRequirementType.DOCUMENT, "Provide the record",
                                requiredness = InformationRequestRequiredness.REQUIRED,
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                                evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(maximumFileCount = 10),
                            ),
                        ),
                    ),
                ),
            )
        }

    private fun uploads(
        respondentFiles: Long = 100,
        respondentBytes: Long = 1_000_000,
        requestFiles: Long = 200,
    ): InformationRequestEvidenceUploadService {
        val intake = InformationRequestEvidenceIntake(
            deploymentPolicy = deployment,
            limits = InformationRequestEvidenceUploadLimits(
                maximumFileBytes = 1_000_000,
                maximumNoAuthFileBytes = 1_000_000,
                maximumRequestFiles = requestFiles,
                maximumRequestBytes = 10_000_000,
                maximumPartyFiles = respondentFiles,
                maximumPartyBytes = respondentBytes,
            ),
            executionGrantService = grants,
            requirementContext = requirementContext,
            policyLoader = policies,
            inspector = inspector,
            artifactRepository = artifacts,
            versionRepository = evidenceVersions,
            assessmentRepository = assessments,
            documentVersionRecordingService = documents,
            clock = clock,
        )
        return InformationRequestEvidenceUploadService(
            gate = gate,
            artifactRepository = artifacts,
            versionRepository = evidenceVersions,
            documentVersionRecordingService = documents,
            commandReceiptService = receipts,
            transitionHistory = history,
            viewLoader = InformationRequestEvidenceViewLoader(evidenceVersions, documents),
            intake = intake,
            noAuthAccess = noAuth,
        )
    }

    private fun upload(
        uploads: InformationRequestEvidenceUploadService,
        request: PublishedRequest,
        access: RequestAccessContext,
        file: InformationRequestEvidenceFile = recordFile(),
    ): UUID = QuarkusTransaction.requiringNew().call {
        val requirement = support.requirement(request, REQUIREMENT_KEY)
        uploads.upload(
            UploadInformationRequestEvidenceCommand(
                request.requestId, requirement.id, access, InformationRequestEvidenceSurface.AUTHENTICATED,
                CommandPrecondition.ExpectedRevision(
                    InformationRequestETag.evidenceOf(requirement.id, artifacts.findForRequirement(requirement.id)),
                ),
                UUID.randomUUID().toString(), file, InformationRequestEvidenceAttributes.NONE,
            ),
        ).artifact.artifact.id
    }

    private fun replace(
        uploads: InformationRequestEvidenceUploadService,
        request: PublishedRequest,
        access: RequestAccessContext,
        artifactId: UUID,
    ) = QuarkusTransaction.requiringNew().call {
        uploads.replace(
            ReplaceInformationRequestEvidenceCommand(
                request.requestId, support.requirement(request, REQUIREMENT_KEY).id, artifactId, access,
                InformationRequestEvidenceSurface.AUTHENTICATED, artifactPrecondition(artifactId),
                UUID.randomUUID().toString(), recordFile(), InformationRequestEvidenceAttributes.NONE,
            ),
        )
    }

    private fun withdraw(request: PublishedRequest, access: RequestAccessContext, artifactId: UUID) =
        QuarkusTransaction.requiringNew().call {
            collection.withdraw(
                ChangeInformationRequestEvidenceStateCommand(
                    request.requestId, support.requirement(request, REQUIREMENT_KEY).id, artifactId, access,
                    artifactPrecondition(artifactId), UUID.randomUUID().toString(), "Provided in error",
                ),
            )
        }

    private fun artifactPrecondition(artifactId: UUID): CommandPrecondition =
        CommandPrecondition.ExpectedRevision(
            InformationRequestETag.artifactOf(
                requireNotNull(
                    artifacts.findById(
                        artifactId
                    )
                )
            )
        )

    private fun uploadersOf(artifactId: UUID): List<PrincipalRef> =
        QuarkusTransaction.requiringNew().call {
            evidenceVersions.findForArtifact(artifactId)
                .map { PrincipalRef(it.createdByPrincipalKind, it.createdByPrincipalId) }
        }

    private fun linkAccount(request: PublishedRequest): RequestAccessContext {
        val accountId = support.outsider()
        upgrade(request, accountId)
        return accountAccess(accountId)
    }

    private fun accountAccess(accountId: UUID): RequestAccessContext =
        RequestAccessContext(PrincipalRef.user(accountId), AuthorizationContext(sessionRef = "linked-account-session"))

    private fun upgrade(request: PublishedRequest, accountId: UUID): ParticipantAccountLink {
        val participant = request.principal(RESPONDENT)
        val partyId = request.parties.getValue(RESPONDENT)
        val linkId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val secret =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val partyRevision = dataSource.connection.use { connection ->
            val email = queryString(connection, "SELECT email FROM external_participant WHERE id = ?", participant.id)
            execute(
                connection,
                "UPDATE external_participant SET email_verified_at = now() WHERE id = ?",
                participant.id
            )
            execute(
                connection,
                "UPDATE app_user SET email = ?, email_verification_completed = TRUE WHERE id = ?",
                email,
                accountId
            )
            val shareId = UUID.fromString(
                queryString(
                    connection,
                    "SELECT share_id::text FROM information_request_party WHERE id = ?",
                    partyId
                )
            )
            execute(
                connection,
                """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa,
                     contact_otp_failed_attempts, contact_otp_challenge_count, rotation_count,
                     max_uses, expires_at, created_at, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 0, 0, 5,
                        now() + interval '30 days', now(), 'EMAIL_OTP')
                """.trimIndent(),
                linkId, shareId, CommandRequestFingerprint.sha256Hex("respondent-allowance-$linkId"),
            )
            execute(
                connection,
                """
                INSERT INTO request_access_session
                    (id, share_link_id, participant_principal_kind, participant_principal_id,
                     verification_strength, issued_at, expires_at, credential_hash, created_at)
                VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?, now())
                """.trimIndent(),
                sessionId, linkId, participant.id, CommandRequestFingerprint.sha256Hex(secret),
            )
            queryString(
                connection,
                "SELECT party_revision::text FROM information_request_party WHERE id = ?",
                partyId
            )!!.toLong()
        }
        val upgrade = upgrades.upgrade(
            UpgradeInformationRequestParticipantAccountCommand(
                request.requestId,
                sessionId,
                "$sessionId.$secret",
                accountAccess(accountId),
                CommandPrecondition.ExpectedRevision(RevisionETag.of(partyId, partyRevision)),
                UUID.randomUUID().toString(),
            ),
        )
        assertEquals(participant.id, upgrade.participantAccountLink.participantId)
        assertEquals(accountId, upgrade.participantAccountLink.appUserId)
        return upgrade.participantAccountLink
    }

    private fun earlierRequestTo(request: PublishedRequest): PublishedRequest {
        val earlier = request.copy(
            requestId = UUID.randomUUID(),
            exchangeId = UUID.randomUUID(),
            parties = mapOf(RESPONDENT to UUID.randomUUID()),
        )
        val shareId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange
                    (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in, created_date, last_activity,
                     description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect process information', 'Please respond',
                        'Earlier process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                earlier.exchangeId, request.ownerId, request.ownerId,
            )
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_user_id, state, gates_exchange_closure,
                     aggregate_revision, party_revision, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', ?, 'DRAFT', TRUE, 1, 1, now(), now())
                """.trimIndent(),
                earlier.requestId, earlier.exchangeId, request.templateVersionId, request.ownerId,
            )
            execute(
                connection,
                "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?",
                earlier.requestId
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId, earlier.requestId, request.principal(RESPONDENT).id,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'CONTRIBUTOR', 'PARTICIPANT', ?, ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                earlier.parties.getValue(RESPONDENT), earlier.requestId, request.principal(RESPONDENT).id, shareId,
            )
        }
        return earlier
    }

    private fun recordFile(): InformationRequestEvidenceFile {
        val file = File.createTempFile("respondent-allowance", ".txt").apply {
            deleteOnExit()
            writeText("record-" + UUID.randomUUID().toString())
        }
        assertEquals(RECORD_BYTES, file.length())
        return InformationRequestEvidenceFile(
            file,
            "provided-record.txt",
            "text/plain",
            DocumentEncryptionMode.INTERNAL
        )
    }

    private fun assertLimitExceeded(block: () -> Unit) {
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { block() }
        assertEquals(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED, refusal.reasonCode)
    }

    private companion object {
        const val RESPONDENT = "CONTRIBUTOR"
        const val OTHER_RESPONDENT = "CONTRIBUTOR#other"
        const val REQUIREMENT_KEY = "provided-record"
        const val RECORD_BYTES = 43L
    }
}
