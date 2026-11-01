package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestEvidenceQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestEvidenceGate,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val viewLoader: InformationRequestEvidenceViewLoader,
    private val contentRelease: InformationRequestEvidenceContentRelease,
    private val evaluationService: InformationRequestEvidenceEvaluationService,
    private val responseStore: InformationRequestResponseStore,
    private val contentOpener: InformationRequestEvidenceContentOpener,
)
{
    fun list(requestId: UUID, requirementId: UUID, access: RequestAccessContext): InformationRequestEvidenceList
    {
        val reader = reader(requestId, requirementId, access)
        val artifacts = artifactRepository.findForRequirement(requirementId)
        val shown = if (gate.shows(reader.request, reader.requirement)) artifacts.filter(reader::sees) else emptyList()

        return InformationRequestEvidenceList(
            requirementId = requirementId,
            evidenceETag = InformationRequestETag.evidenceOf(requirementId, artifacts),
            artifacts = shown.map { gate.forCaller(access, reader.request, viewLoader.view(it)) },
            evaluation = evaluationService.evaluate(reader.requirement, currentDisposition(requestId, requirementId)),
        )
    }

    fun attachedFileCounts(
        requestId: UUID,
        requirementIds: Collection<UUID>,
        access: RequestAccessContext
    ): Map<UUID, Int> {
        val readable = requirementIds.filter { requirementId ->
            gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, requirementId) ||
                    gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_VIEW, requirementId)
        }.toSet()
        if (readable.isEmpty()) return emptyMap()
        val attached = artifactRepository.findForRequest(requestId)
            .filter { it.informationRequestRequirementId in readable }
            .filter { it.collectionState == InformationRequestEvidenceCollectionState.ACTIVE }
            .groupingBy { it.informationRequestRequirementId }
            .eachCount()
        return readable.associateWith { attached[it] ?: 0 }
    }

    fun artifact(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        access: RequestAccessContext,
    ): InformationRequestEvidenceArtifactView
    {
        val reader = reader(requestId, requirementId, access)
        if (!gate.shows(reader.request, reader.requirement)) throw notFound()
        return gate.forCaller(
            access,
            reader.request,
            viewLoader.view(visibleArtifact(reader, requirementId, artifactId))
        )
    }

    fun openContent(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        versionId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): InformationRequestEvidenceContent =
        contentOpener.open(requestId, access, use) { current ->
            releasable(requestId, requirementId, artifactId, versionId, current)
        }

    fun versionRecords(versionIds: Collection<UUID>): Map<UUID, InformationRequestEvidenceVersionRecord> =
        versionRepository.findForIds(versionIds.toSet()).associate { version ->
            version.id to InformationRequestEvidenceVersionRecord(
                evidenceVersionId = version.id,
                evidenceArtifactId = version.evidenceArtifactId,
                versionNumber = version.versionNumber,
                declaredFileName = version.declaredFileName,
                createdBy = PrincipalRef(version.createdByPrincipalKind, version.createdByPrincipalId),
            )
        }

    private fun releasable(
        requestId: UUID,
        requirementId: UUID,
        artifactId: UUID,
        versionId: UUID,
        access: RequestAccessContext,
    ): InformationRequestEvidenceRelease
    {
        val reader = reader(requestId, requirementId, access)
        if (!gate.shows(reader.request, reader.requirement)) throw notFound()
        val artifact = visibleArtifact(reader, requirementId, artifactId)
        val version = versionRepository.findById(versionId)
            ?.takeIf { it.evidenceArtifactId == artifact.id }
            ?: throw notFound()
        val documentVersionId = InformationRequestEvidenceVersionSourceMapper.read(version).documentVersionId
        contentRelease.requireReleasable(version, access)
        return InformationRequestEvidenceRelease(reader.request, version, documentVersionId, access, requirementId)
    }

    private fun reader(requestId: UUID, requirementId: UUID, access: RequestAccessContext): EvidenceReader
    {
        val request = queryService.findById(requestId, access)
        val manages = gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, requirementId)
        if (!manages && !gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_VIEW, requirementId))
        {
            throw ForbiddenException("Access denied to Information Request evidence")
        }
        val requirement = gate.requireEvidenceOccurrence(request, requirementId)
        return EvidenceReader(request, requirement, manages)
    }

    private fun currentDisposition(requestId: UUID, requirementId: UUID): InformationRequestResponseDisposition? =
        responseStore.findCurrentForRequest(requestId)
            .firstOrNull { it.informationRequestRequirementId == requirementId }
            ?.disposition

    private fun visibleArtifact(
        reader: EvidenceReader,
        requirementId: UUID,
        artifactId: UUID
    ): InformationRequestEvidenceArtifact =
        artifactRepository.findById(artifactId)
            ?.takeIf { it.informationRequestRequirementId == requirementId && reader.sees(it) }
            ?: throw notFound()

    private fun notFound() =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Information Request evidence not found"
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
