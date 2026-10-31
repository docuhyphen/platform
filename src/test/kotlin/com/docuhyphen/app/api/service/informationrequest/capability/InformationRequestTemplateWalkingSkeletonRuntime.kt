package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.migration.queryStrings
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionAttestationResult
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionResult
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.capability.model.InformationRequestTemplateWalkingSkeletonFixture
import com.docuhyphen.app.api.service.informationrequest.capability.model.IssuedWalkingSkeleton
import com.docuhyphen.app.api.service.informationrequest.capability.model.WalkingSkeletonField
import com.docuhyphen.app.api.service.informationrequest.conformance.PublishedRequestSupport
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import kotlinx.serialization.json.JsonPrimitive
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.io.File
import java.util.*
import javax.sql.DataSource

internal class InformationRequestTemplateWalkingSkeletonRuntime(
    private val dataSource: DataSource,
    private val published: PublishedRequestSupport,
    private val runtime: InformationRequestRuntimeTestServices,
    private val requestRepository: InformationRequestRepository,
    private val evidenceUploads: InformationRequestEvidenceUploadService,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
) {
    fun issueBasic(partyRoles: List<String>, participants: Set<String> = emptySet()): IssuedWalkingSkeleton =
        issue(listOf(RECORDED_SUMMARY), partyRoles, participants) { schemaVersionId, fields ->
            InformationRequestTemplateWalkingSkeletonFixtures.basicFieldDocumentResponseAttestationRequest(
                schemaVersionId,
                fields.getValue(RECORDED_SUMMARY).fieldDefinitionId,
            )
        }

    fun issueMultiParty(partyRoles: List<String>, participants: Set<String> = emptySet()): IssuedWalkingSkeleton =
        issue(listOf(SUBJECT_STATUS, DELEGATE_NOTE), partyRoles, participants) { schemaVersionId, fields ->
            InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(
                schemaVersionId,
                fields.getValue(SUBJECT_STATUS).fieldDefinitionId,
                fields.getValue(DELEGATE_NOTE).fieldDefinitionId,
            )
        }

    fun requirementId(issued: IssuedWalkingSkeleton, requirementKey: String): UUID =
        published.requirement(issued.request, requirementKey).id

    fun materializedRequirements(issued: IssuedWalkingSkeleton): Set<Pair<String, String>> =
        dataSource.connection.use { connection ->
            queryStrings(
                connection,
                """
                SELECT template_requirement.requirement_key || '@' || requirement.occurrence_path
                FROM information_request_requirement requirement
                JOIN information_request_template_requirement template_requirement
                    ON template_requirement.id = requirement.source_template_requirement_id
                WHERE requirement.information_request_id = ?
                """.trimIndent(),
                issued.requestId,
            )
        }.map { it.substringBefore('@') to it.substringAfter('@') }.toSet()

    fun materializedRequirementCount(issued: IssuedWalkingSkeleton): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_requirement WHERE information_request_id = ?",
                issued.requestId
            )
        }

    @Suppress("LongParameterList")
    fun respond(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        requirementKey: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        disposition: InformationRequestResponseDisposition? = null,
        narrative: ResponseNarrativePatch = ResponseNarrativePatch.Unchanged,
        fieldValue: String? = null,
    ): InformationRequestResponseDraftResult {
        val requirementId = requirementId(issued, requirementKey)
        val precondition = CommandPrecondition.ExpectedRevision(published.responseETag(issued.request))
        return QuarkusTransaction.requiringNew().call {
            services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = issued.requestId,
                    access = access,
                    precondition = precondition,
                    idempotencyKey = idempotencyKey,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = requirementId,
                            disposition = disposition,
                            narrative = narrative,
                            fieldValues = fieldValue?.let { value ->
                                ResponseFieldValuesPatch(
                                    entries = listOf(
                                        FieldValueEntry(
                                            issued.request.fields.getValue(requirementKey).fieldContractId,
                                            JsonPrimitive(value)
                                        )
                                    ),
                                    precondition = FieldsPrecondition.Unconditioned,
                                )
                            },
                        ),
                    ),
                ),
            )
        }
    }

    fun upload(
        issued: IssuedWalkingSkeleton,
        requirementKey: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        attributes: InformationRequestEvidenceAttributes = InformationRequestEvidenceAttributes.NONE,
    ): InformationRequestEvidenceCommandResult {
        val requirementId = requirementId(issued, requirementKey)
        val evidenceETag = QuarkusTransaction.requiringNew().call {
            InformationRequestETag.evidenceOf(requirementId, artifactRepository.findForRequirement(requirementId))
        }
        return QuarkusTransaction.requiringNew().call {
            evidenceUploads.upload(
                UploadInformationRequestEvidenceCommand(
                    requestId = issued.requestId,
                    requirementId = requirementId,
                    access = access,
                    surface = InformationRequestEvidenceSurface.AUTHENTICATED,
                    precondition = CommandPrecondition.ExpectedRevision(evidenceETag),
                    idempotencyKey = idempotencyKey,
                    file = pdf(idempotencyKey),
                    attributes = attributes,
                ),
            )
        }
    }

    @Suppress("LongParameterList")
    fun assent(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        requirementKey: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        stageKey: String? = null,
    ): InformationRequestSubmissionAttestationResult {
        val requirementId = requirementId(issued, requirementKey)
        val precondition = CommandPrecondition.ExpectedRevision(submissionETag(issued, stageKey))
        return QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = issued.requestId,
                    requirementId = requirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access,
                    precondition = precondition,
                    idempotencyKey = idempotencyKey,
                ),
            )
        }
    }

    fun submit(
        services: InformationRequestRuntimeServices,
        issued: IssuedWalkingSkeleton,
        access: RequestAccessContext,
        idempotencyKey: String,
        stageKey: String? = null,
    ): InformationRequestSubmissionResult {
        val precondition = CommandPrecondition.ExpectedRevision(submissionETag(issued, stageKey))
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = issued.requestId,
                    stageKey = stageKey,
                    access = access,
                    precondition = precondition,
                    idempotencyKey = idempotencyKey,
                ),
            )
        }
    }

    fun submissionETag(issued: IssuedWalkingSkeleton, stageKey: String? = null): String =
        QuarkusTransaction.requiringNew().call {
            val stored = requireNotNull(requestRepository.findById(issued.requestId))
            InformationRequestETag.submissionOf(
                stageKey,
                runtime.contentCollector.collect(stored, stageKey).contentHash
            )
        }

    fun aggregateETag(issued: IssuedWalkingSkeleton): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(issued.requestId))) }

    fun partiesETag(issued: IssuedWalkingSkeleton): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(issued.requestId))) }

    fun openAccessLink(issued: IssuedWalkingSkeleton, party: String, rawToken: String, codeHash: String) {
        dataSource.connection.use { connection ->
            val shareId = requireNotNull(
                queryString(
                    connection,
                    "SELECT share_id::text FROM information_request_party WHERE id = ?",
                    issued.request.parties.getValue(party)
                ),
            )
            execute(
                connection,
                """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength,
                     contact_otp_hash, contact_otp_expires_at)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 1, 0, 1, now() + interval '30 days', now(), 'EMAIL_OTP',
                        ?, now() + interval '10 minutes')
                """.trimIndent(),
                UUID.randomUUID(),
                UUID.fromString(shareId),
                CommandRequestFingerprint.sha256Hex(rawToken),
                codeHash,
            )
        }
    }

    private fun issue(
        fieldKeys: List<String>,
        partyRoles: List<String>,
        participants: Set<String>,
        fixtureOf: (UUID, Map<String, WalkingSkeletonField>) -> InformationRequestTemplateWalkingSkeletonFixture,
    ): IssuedWalkingSkeleton {
        val templateKey = fixtureOf(
            UUID.randomUUID(),
            fieldKeys.associateWith { WalkingSkeletonField(UUID.randomUUID(), UUID.randomUUID()) }).templateKey
        var issuedFixture: InformationRequestTemplateWalkingSkeletonFixture? = null
        val request = published.issue(templateKey, fieldKeys, partyRoles, participants) { schemaVersionId, fields ->
            fixtureOf(schemaVersionId, fields).also { issuedFixture = it }.configuration
        }
        return IssuedWalkingSkeleton(requireNotNull(issuedFixture), request)
    }

    private fun pdf(title: String): InformationRequestEvidenceFile {
        val target = File.createTempFile("walking-skeleton-evidence", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.documentInformation.title = "$title-${UUID.randomUUID()}"
            document.save(target)
        }
        return InformationRequestEvidenceFile(target, "$title.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    companion object {
        const val ROOT = "root"
        const val RECORDED_SUMMARY = "recorded-summary"
        const val SUPPORTING_RECORD = "supporting-record"
        const val RESPONSE_CONFIRMATION = "response-confirmation"
        const val SUBJECT_STATUS = "subject-status"
        const val DELEGATE_NOTE = "delegate-note"
        const val PRIMARY_EVIDENCE = "primary-evidence-record"
        const val ALTERNATE_EVIDENCE = "alternate-evidence-record"
        const val SUBMITTER_ATTESTATION = "submitter-attestation"
        const val REPORTED_ITEM = "reported-item"
        const val SUBJECT_RULE = "when-subject-is-active"
        const val RESPONSE_STAGE = "response-stage"
        const val EVIDENCE_STAGE = "evidence-stage"
        const val CONFIRMATION_STAGE = "confirmation-stage"
        const val CONTENT_REVIEW = "content-review"
    }
}
