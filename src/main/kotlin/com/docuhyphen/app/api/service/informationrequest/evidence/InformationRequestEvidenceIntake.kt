package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAbuseLog
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestEvidenceIntake @Inject constructor(
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
    private val limits: InformationRequestEvidenceUploadLimits,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val policyLoader: InformationRequestEvidencePolicyLoader,
    private val inspector: InformationRequestEvidenceContentInspector,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val clock: Clock,
)
{
    fun inspect(file: InformationRequestEvidenceFile): InformationRequestEvidenceInspectionFacts? =
        if (file.encryptionMode == DocumentEncryptionMode.END_TO_END) null else inspector.inspect(file.file)

    fun admit(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        access: RequestAccessContext,
        surface: InformationRequestEvidenceSurface,
        file: InformationRequestEvidenceFile,
        digest: DocumentVersionContentDigest,
        inspection: InformationRequestEvidenceInspectionFacts?,
        replacing: InformationRequestEvidenceArtifact?,
    ): InformationRequestEvidenceInspectionFacts
    {
        deploymentPolicy.requireUploadAvailable()
        requireWithinUploadLimits(request, access, surface, digest)
        if (inspection == null || file.encryptionMode == DocumentEncryptionMode.END_TO_END) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_OPAQUE_CONTENT_REFUSED,
                "Evidence must be readable so its type can be checked and it can be scanned; upload it without end-to-end encryption",
            )
        }

        val current = currentContent(requirement, excluding = replacing)
        requireWithinPolicy(requirement, file, digest, inspection, current, replacing == null)
        if (current.any { it.describes(digest) })
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_DUPLICATE_CONTENT,
                "This file is already provided for this Requirement",
            )
        }
        return inspection
    }

    fun requireWithinFileLimit(surface: InformationRequestEvidenceSurface, bytes: Long) {
        val fileLimit = limits.fileLimit(surface)
        if (bytes > fileLimit) limitExceeded(
            "An evidence file is at most ${
                InformationRequestEvidenceSizes.describe(
                    fileLimit
                )
            }"
        )
    }

    fun recordInspection(
        version: InformationRequestEvidenceVersion,
        content: DocumentVersion,
        inspection: InformationRequestEvidenceInspectionFacts,
    )
    {
        assessmentRepository.save(
            InformationRequestEvidenceAssessment().apply {
                informationRequestId = version.informationRequestId
                evidenceVersionId = version.id
                assessmentKind = InformationRequestEvidenceAssessmentKind.CONTENT_INSPECTION
                outcome = when
                {
                    inspection.corrupt -> InformationRequestEvidenceInspectionOutcome.CORRUPT
                    inspection.encrypted -> InformationRequestEvidenceInspectionOutcome.ENCRYPTED
                    else -> InformationRequestEvidenceInspectionOutcome.INSPECTED
                }.name
                contentHashAlgorithm = content.contentHashAlgorithm
                contentHash = content.contentHash
                contentLength = content.contentLength
                detectedMediaType = inspection.detectedMediaType
                pageCount = inspection.pageCount
                assessedAt = Timestamp.from(clock.instant())
            },
        )
    }

    private fun requireWithinUploadLimits(
        request: InformationRequest,
        access: RequestAccessContext,
        surface: InformationRequestEvidenceSurface,
        digest: DocumentVersionContentDigest,
    )
    {
        requireWithinFileLimit(surface, digest.length)

        val grant = executionGrantService.findForRequest(request.id)
        val requestFiles = minOf(limits.maximumRequestFiles, grant?.evidenceFileAllowance ?: Long.MAX_VALUE)
        val requestBytes = minOf(limits.maximumRequestBytes, grant?.evidenceByteAllowance ?: Long.MAX_VALUE)
        val requestUsage = versionRepository.storedUsageForRequest(request.id)
        if (requestUsage.files + 1 > requestFiles)
        {
            limitExceeded("This request holds at most $requestFiles evidence files")
        }
        if (requestUsage.bytes + digest.length > requestBytes)
        {
            limitExceeded("This request holds at most ${InformationRequestEvidenceSizes.describe(requestBytes)} of evidence")
        }

        val respondentUsage = versionRepository.storedUsageForUploaders(
            request.id,
            requirementContext.linkedIdentitiesOf(access.principal),
        )
        if (respondentUsage.files + 1 > limits.maximumPartyFiles)
        {
            limitExceeded("Each respondent stores at most ${limits.maximumPartyFiles} evidence files on a request")
        }
        if (respondentUsage.bytes + digest.length > limits.maximumPartyBytes)
        {
            limitExceeded(
                "Each respondent stores at most ${InformationRequestEvidenceSizes.describe(limits.maximumPartyBytes)} " +
                        "of evidence on a request"
            )
        }
    }

    private fun requireWithinPolicy(
        requirement: InformationRequestRequirement,
        file: InformationRequestEvidenceFile,
        digest: DocumentVersionContentDigest,
        inspection: InformationRequestEvidenceInspectionFacts,
        current: List<DocumentVersion>,
        addsArtifact: Boolean,
    )
    {
        val policy = policyLoader.forBinding(requirement.sourceTemplateBindingId) ?: return

        policy.maximumFileSizeBytes?.let { maximum ->
            if (digest.length > maximum) {
                policyRefused(
                    "This Requirement accepts files of at most ${
                        InformationRequestEvidenceSizes.describe(
                            maximum
                        )
                    }"
                )
            }
        }
        policy.maximumFileCount?.let { maximum ->
            if (addsArtifact && current.size + 1 > maximum)
            {
                policyRefused("This Requirement accepts at most $maximum ${if (maximum == 1) "file" else "files"}")
            }
        }
        policy.maximumTotalSizeBytes?.let { maximum ->
            if (current.sumOf { it.contentLength } + digest.length > maximum)
            {
                policyRefused(
                    "This Requirement accepts at most ${InformationRequestEvidenceSizes.describe(maximum)} of files in total"
                )
            }
        }
        val accepted = policy.acceptedValues[InformationRequestEvidenceAttribute.CONTENT_TYPE].orEmpty()
        val detected = inspection.detectedMediaType
        if (accepted.isNotEmpty() &&
            !InformationRequestEvidenceMediaTypes.accepts(
                accepted,
                detected,
                file.declaredMediaType,
                file.declaredFileName
            )
        )
        {
            val judged =
                InformationRequestEvidenceMediaTypes.judgedType(detected, file.declaredMediaType, file.declaredFileName)
            policyRefused("This Requirement does not accept this type of file (this file is $judged)")
        }
    }

    private fun currentContent(
        requirement: InformationRequestRequirement,
        excluding: InformationRequestEvidenceArtifact?,
    ): List<DocumentVersion> =
        artifactRepository.findForRequirement(requirement.id)
            .filter { it.collectionState == InformationRequestEvidenceCollectionState.ACTIVE && it.id != excluding?.id }
            .mapNotNull { versionRepository.findLatest(it.id)?.documentVersionId }
            .mapNotNull(documentVersionRecordingService::findVersion)

    private fun DocumentVersion.describes(digest: DocumentVersionContentDigest): Boolean =
        contentHashAlgorithm == digest.algorithm && contentHash == digest.value && contentLength == digest.length

    private fun limitExceeded(message: String): Nothing
    {
        InformationRequestAbuseLog.refused(InformationRequestAbuseControl.EVIDENCE_UPLOAD_LIMIT)
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED,
            message
        )
    }

    private fun policyRefused(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED, message)
}
