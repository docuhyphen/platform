package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestDocumentVersionEvidenceSource
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceArtifactView
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceList
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMediaTypes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceVersionSourceMapper
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestExternalEvidenceSource
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestEvidenceQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestEvidenceGate,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val viewLoader: InformationRequestEvidenceViewLoader,
    private val accessAudit: InformationRequestEvidenceAccessAudit,
    private val contentRelease: InformationRequestEvidenceContentRelease,
    private val evaluationService: InformationRequestEvidenceEvaluationService,
    private val responseStore: InformationRequestResponseStore,
)
{
    fun list(requestId: UUID, requirementId: UUID, access: RequestAccessContext): InformationRequestEvidenceList
    {
        val reader = reader(requestId, requirementId, access)
        val artifacts = artifactRepository.findForRequirement(requirementId)

        return InformationRequestEvidenceList(
            requirementId = requirementId,
            evidenceETag = InformationRequestETag.evidenceOf(requirementId, artifacts),
            artifacts = artifacts.filter(reader::sees).map { viewLoader.view(it) },
            evaluation = evaluationService.evaluate(reader.requirement, currentDisposition(requestId, requirementId)),
        )
    }

    fun artifact(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
    ): InformationRequestEvidenceArtifactView
    {
        val reader = reader(requestId, requirementId, access)
        return viewLoader.view(visibleArtifact(reader, requirementId, artifactId))
    }

    fun openContent(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        versionId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): InformationRequestEvidenceContent
    {
        val reader = reader(requestId, requirementId, access)
        val artifact = visibleArtifact(reader, requirementId, artifactId)
        val version = versionRepository.findById(versionId)
            ?.takeIf { it.evidenceArtifactId == artifact.id }
            ?: throw notFound()
        val documentVersionId = when (val source = InformationRequestEvidenceVersionSourceMapper.read(version))
        {
            is InformationRequestDocumentVersionEvidenceSource -> source.documentVersionId
            is InformationRequestExternalEvidenceSource -> throw contentUnavailable()
        }
        contentRelease.requireReleasable(version, access)
        val documentVersion = documentVersionRecordingService.findVersion(documentVersionId) ?: throw contentUnavailable()
        val content = try
        {
            documentVersionRecordingService.open(documentVersion)
        }
        catch (_: DocumentVersionContentIntegrityException)
        {
            throw contentUnavailable()
        }
        catch (_: DocumentVersionContentNotFoundException)
        {
            throw contentUnavailable()
        }

        val mediaType = InformationRequestEvidenceMediaTypes.detect(content.file)
        val inline = use == InformationRequestEvidenceContentUse.PREVIEW
        if (inline && !InformationRequestEvidenceMediaTypes.isInlineSafe(mediaType))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_PREVIEW_UNAVAILABLE,
                "This evidence cannot be previewed; download it instead",
            )
        }

        accessAudit.record(reader.request, access, use, version, requirementId)
        return InformationRequestEvidenceContent(
            file = content.file,
            fileName = version.declaredFileName ?: content.fileName,
            mediaType = mediaType,
            inline = inline,
        )
    }

    private fun reader(requestId: UUID, requirementId: UUID, access: RequestAccessContext): EvidenceReader
    {
        val request = queryService.findById(requestId, access)
        val requirement = gate.requireEvidenceOccurrence(request, requirementId)
        val manages = gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, requirementId)
        if (!manages && !gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_VIEW, requirementId))
        {
            throw ForbiddenException("Access denied to Information Request evidence")
        }
        return EvidenceReader(request, requirement, manages)
    }

    private fun currentDisposition(requestId: UUID, requirementId: UUID): InformationRequestResponseDisposition? =
        responseStore.findCurrentForRequest(requestId)
            .firstOrNull { it.informationRequestRequirementId == requirementId }
            ?.disposition

    private fun visibleArtifact(reader: EvidenceReader, requirementId: UUID, artifactId: UUID): InformationRequestEvidenceArtifact =
        artifactRepository.findById(artifactId)
            ?.takeIf { it.informationRequestRequirementId == requirementId && reader.sees(it) }
            ?: throw notFound()

    private fun notFound() =
        InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request evidence not found")

    private fun contentUnavailable() =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.EVIDENCE_CONTENT_UNAVAILABLE,
            "This evidence content is not available",
        )

    private class EvidenceReader(
        val request: InformationRequest,
        val requirement: InformationRequestRequirement,
        private val manages: Boolean,
    )
    {
        fun sees(artifact: InformationRequestEvidenceArtifact): Boolean =
            manages || artifact.collectionState != InformationRequestEvidenceCollectionState.REMOVED
    }
}
