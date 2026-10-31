package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestArchivedEvidence
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestHiddenResponseFiles @Inject constructor(
    private val artifactRepository: InformationRequestEvidenceArtifactRepository,
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val gate: InformationRequestEvidenceGate,
) {
    fun active(requirementId: UUID): List<InformationRequestEvidenceArtifact> =
        artifactRepository.findForRequirement(requirementId)
            .filter { it.collectionState == InformationRequestEvidenceCollectionState.ACTIVE }

    fun activeChangeableBy(
        request: InformationRequest,
        requirementId: UUID,
        access: RequestAccessContext,
    ): List<InformationRequestEvidenceArtifact> =
        active(requirementId).filter { gate.changeableBy(access, request, it) }

    fun currentVersionsOf(files: List<InformationRequestEvidenceArtifact>): List<InformationRequestArchivedEvidence> =
        files.mapNotNull { artifact ->
            versionRepository.findLatest(artifact.id)?.let { version ->
                InformationRequestArchivedEvidence(artifact.id, version.id, version.versionNumber)
            }
        }
}
