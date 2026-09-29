package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactEvidenceRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestAcceptedFactEvidenceService @Inject constructor(
    private val repository: InformationRequestAcceptedFactEvidenceRepository,
)
{
    fun select(
        submission: InformationRequestSubmissionPackageView,
        sourceRequirementId: UUID,
        evidenceVersionIds: List<UUID>,
    ): List<InformationRequestSubmissionEvidence>
    {
        if (evidenceVersionIds.size != evidenceVersionIds.toSet().size)
        {
            throw InformationRequestCommandRequestException("A promoted evidence version is selected once")
        }
        val supportingRequirements = submission.links
            .filter { it.supportedRequirementId == sourceRequirementId }
            .map { it.supportingRequirementId }
            .toSet()
        val supportingItems = submission.items
            .filter { it.informationRequestRequirementId in supportingRequirements }
            .map { it.id }
            .toSet()
        val eligible = submission.evidence
            .filter { it.itemId in supportingItems && it.conformance == "CONFORMING" }
            .associateBy { it.evidenceVersionId }
        return evidenceVersionIds.map { versionId ->
            eligible[versionId]
                ?: throw InformationRequestCommandRequestException(
                    "A promoted evidence version belongs to the source package and supports the accepted answer",
                )
        }
    }

    fun save(factId: UUID, selected: List<InformationRequestSubmissionEvidence>)
    {
        selected.forEach { evidence ->
            repository.save(
                InformationRequestAcceptedFactEvidence().apply {
                    this.factId = factId
                    sourceSubmissionEvidenceId = evidence.id
                    evidenceVersionId = evidence.evidenceVersionId
                },
            )
        }
    }
}
