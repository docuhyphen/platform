package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFile
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceFindingCode
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceInspectionFacts
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceInspectionOutcome
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMediaTypes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceAssessmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestEvidenceIntake @Inject constructor(
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
    private val limits: InformationRequestEvidenceUploadLimits,
    private val policyLoader: InformationRequestEvidencePolicyLoader,
    private val inspector: InformationRequestEvidenceContentInspector,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val assessmentRepository: InformationRequestEvidenceAssessmentRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val clock: Clock,
)
{
    fun admit(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        access: RequestAccessContext,
        surface: InformationRequestEvidenceSurface,
        file: InformationRequestEvidenceFile,
        digest: DocumentVersionContentDigest,
        replacing: InformationRequestEvidenceArtifact?,
    ): InformationRequestEvidenceInspectionFacts?
    {
        deploymentPolicy.requireUploadAvailable()
        requireWithinUploadLimits(request, access, surface, digest)

        val opaque = file.encryptionMode == DocumentEncryptionMode.END_TO_END
        val inspection = if (opaque) null else inspector.inspect(file.file)
        val current = currentContent(requirement, excluding = replacing)
        requireWithinPolicy(requirement, digest, inspection, current, replacing == null)
        if (current.any { it.describes(digest) })
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_DUPLICATE_CONTENT,
                "This file is already provided for this Requirement",
            )
        }
        return inspection
    }

    fun recordInspection(
        version: InformationRequestEvidenceVersion,
        content: DocumentVersion,
        inspection: InformationRequestEvidenceInspectionFacts?,
    )
    {
        inspection ?: return
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
        val fileLimit = when (surface)
        {
            InformationRequestEvidenceSurface.AUTHENTICATED -> limits.maximumFileBytes
            InformationRequestEvidenceSurface.NO_AUTH -> minOf(limits.maximumFileBytes, limits.maximumNoAuthFileBytes)
        }
        if (digest.length > fileLimit) limitExceeded("An evidence file is at most $fileLimit bytes")

        val requestUsage = versionRepository.storedUsageForRequest(request.id)
        if (requestUsage.files + 1 > limits.maximumRequestFiles)
        {
            limitExceeded("This request holds at most ${limits.maximumRequestFiles} evidence files")
        }
        if (requestUsage.bytes + digest.length > limits.maximumRequestBytes)
        {
            limitExceeded("This request holds at most ${limits.maximumRequestBytes} bytes of evidence")
        }

        val partyUsage = versionRepository.storedUsageForUploader(request.id, access.principal)
        if (partyUsage.files + 1 > limits.maximumPartyFiles)
        {
            limitExceeded("Each respondent stores at most ${limits.maximumPartyFiles} evidence files on a request")
        }
        if (partyUsage.bytes + digest.length > limits.maximumPartyBytes)
        {
            limitExceeded("Each respondent stores at most ${limits.maximumPartyBytes} bytes of evidence on a request")
        }
    }

    private fun requireWithinPolicy(
        requirement: InformationRequestRequirement,
        digest: DocumentVersionContentDigest,
        inspection: InformationRequestEvidenceInspectionFacts?,
        current: List<DocumentVersion>,
        addsArtifact: Boolean,
    )
    {
        val policy = policyLoader.forBinding(requirement.sourceTemplateBindingId) ?: return

        policy.maximumFileSizeBytes?.let { maximum ->
            if (digest.length > maximum) policyRefused(InformationRequestEvidenceFindingCode.FILE_TOO_LARGE, "at most $maximum bytes")
        }
        policy.maximumFileCount?.let { maximum ->
            if (addsArtifact && current.size + 1 > maximum)
            {
                policyRefused(InformationRequestEvidenceFindingCode.FILE_COUNT_ABOVE_MAXIMUM, "at most $maximum files")
            }
        }
        policy.maximumTotalSizeBytes?.let { maximum ->
            if (current.sumOf { it.contentLength } + digest.length > maximum)
            {
                policyRefused(InformationRequestEvidenceFindingCode.TOTAL_SIZE_ABOVE_MAXIMUM, "at most $maximum bytes in total")
            }
        }
        val accepted = policy.acceptedValues[InformationRequestEvidenceAttribute.CONTENT_TYPE].orEmpty()
            .map(InformationRequestEvidenceMediaTypes::canonical)
            .toSet()
        val detected = inspection?.detectedMediaType?.let(InformationRequestEvidenceMediaTypes::canonical)
        if (inspection != null && accepted.isNotEmpty() && detected !in accepted)
        {
            policyRefused(InformationRequestEvidenceFindingCode.CONTENT_TYPE_NOT_ACCEPTED, "this file is $detected")
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

    private fun limitExceeded(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED, message)

    private fun policyRefused(code: InformationRequestEvidenceFindingCode, detail: String): Nothing =
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.EVIDENCE_POLICY_REFUSED,
            "This file does not meet the Requirement's evidence policy: ${code.name} ($detail)",
        )
}
