package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAccess
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestRetainedEvidenceQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestEvidenceGate,
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val contentRelease: InformationRequestEvidenceContentRelease,
    private val contentOpener: InformationRequestEvidenceContentOpener,
    private val reviewAccess: InformationRequestReviewAccess,
) {
    fun openContent(
        requestId: UUID,
        retained: InformationRequestRetainedEvidenceVersion,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): InformationRequestEvidenceContent =
        contentOpener.open(requestId, access, use) { current -> releasable(requestId, retained, current) }

    fun refuseMissing(
        requestId: UUID,
        packageId: UUID,
        retainedRequirementIds: Set<UUID>,
        access: RequestAccessContext,
    ): Nothing {
        if (retainedRequirementIds.all { readable(access, requestId, packageId, it) }) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submission Package evidence not found",
            )
        }
        throw ForbiddenException(DENIED)
    }

    private fun releasable(
        requestId: UUID,
        retained: InformationRequestRetainedEvidenceVersion,
        access: RequestAccessContext,
    ): InformationRequestEvidenceRelease {
        val request = queryService.findById(requestId, access)
        val manages = gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, retained.requirementId)
        if (!manages && !readable(access, requestId, retained.packageId, retained.requirementId)) {
            throw ForbiddenException(DENIED)
        }
        val artifact = artifactRepository.findById(retained.artifactId)
            ?.takeIf { it.informationRequestId == requestId && it.informationRequestRequirementId == retained.requirementId }
            ?.takeIf { manages || it.collectionState != InformationRequestEvidenceCollectionState.REMOVED }
            ?: throw notFound()
        val version = versionRepository.findById(retained.versionId)
            ?.takeIf { it.evidenceArtifactId == artifact.id && it.versionNumber == retained.versionNumber }
            ?: throw notFound()
        val documentVersionId = InformationRequestEvidenceVersionSourceMapper.read(version).documentVersionId
        if (!frozenAs(retained, documentVersionRecordingService.findVersion(documentVersionId))) {
            throw contentOpener.contentUnavailable()
        }
        contentRelease.requireReleasable(version, access)
        return InformationRequestEvidenceRelease(
            request,
            version,
            documentVersionId,
            access,
            retained.requirementId,
            retained
        )
    }

    private fun readable(access: RequestAccessContext, requestId: UUID, packageId: UUID, requirementId: UUID): Boolean =
        gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, requirementId) ||
                gate.permits(access, Action.INFORMATION_REQUEST_EVIDENCE_VIEW, requirementId) ||
                reviewAccess.readsReviewedItem(access, requestId, packageId, requirementId)

    private fun frozenAs(retained: InformationRequestRetainedEvidenceVersion, document: DocumentVersion?): Boolean =
        document != null &&
                retained.documentVersionId == document.id &&
                retained.contentHashAlgorithm == document.contentHashAlgorithm.name &&
                retained.contentHash == document.contentHash &&
                retained.contentLength == document.contentLength

    private fun notFound() =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Information Request evidence not found",
        )

    private companion object {
        const val DENIED = "Access denied to Information Request evidence"
    }
}
