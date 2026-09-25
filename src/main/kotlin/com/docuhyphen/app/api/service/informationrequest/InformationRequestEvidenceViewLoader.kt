package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceArtifactView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceVersionView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestEvidenceVersionRepository
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceViewLoader @Inject constructor(
    private val versionRepository: InformationRequestEvidenceVersionRepository,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
)
{
    fun view(
        artifact: InformationRequestEvidenceArtifact,
        throughVersionNumber: Int? = null,
    ): InformationRequestEvidenceArtifactView =
        InformationRequestEvidenceArtifactView(
            artifact,
            versionRepository.findForArtifact(artifact.id)
                .filter { throughVersionNumber == null || it.versionNumber <= throughVersionNumber }
                .map { version ->
                    InformationRequestEvidenceVersionView(
                        version,
                        version.documentVersionId?.let(documentVersionRecordingService::findVersion),
                    )
                },
        )
}
