package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionSupportingLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionPackageReader @Inject constructor(
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val evidenceRepository: InformationRequestSubmissionEvidenceRepository,
    private val linkRepository: InformationRequestSubmissionSupportingLinkRepository,
    private val packageAttestationRepository: InformationRequestSubmissionPackageAttestationRepository,
    private val attestationRepository: InformationRequestSubmissionAttestationRepository,
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository,
)
{
    fun view(requestId: UUID, packageId: UUID): InformationRequestSubmissionPackageView =
        views(requestId).firstOrNull { it.submissionPackage.id == packageId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Submission Package not found")

    fun views(requestId: UUID): List<InformationRequestSubmissionPackageView>
    {
        val packages = packageRepository.findForRequest(requestId)
        if (packages.isEmpty()) return emptyList()
        val ids = packages.map { it.id }
        val items = itemRepository.findForPackages(ids).groupBy { it.packageId }
        val evidence = evidenceRepository.findForPackages(ids).groupBy { it.packageId }
        val links = linkRepository.findForPackages(ids).groupBy { it.packageId }
        val attestationsById = attestationRepository.findForRequest(requestId).associateBy { it.id }
        val frozen = packageAttestationRepository.findForPackages(ids).groupBy { it.packageId }
        val withdrawals = withdrawalRepository.findForRequest(requestId).associateBy { it.packageId }
        return packages.map { submission ->
            InformationRequestSubmissionPackageView(
                submissionPackage = submission,
                items = items[submission.id].orEmpty(),
                evidence = evidence[submission.id].orEmpty(),
                links = links[submission.id].orEmpty(),
                attestations = frozen[submission.id].orEmpty()
                    .mapNotNull { attestationsById[it.attestationId] }
                    .sortedBy { it.sequenceNumber },
                withdrawal = withdrawals[submission.id],
            )
        }
    }
}
