package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestRetainedEvidenceQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestSubmissionEvidenceQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val retainedEvidence: InformationRequestRetainedEvidenceQueryService,
) {
    fun openContent(
        requestId: UUID,
        packageId: UUID,
        evidenceVersionId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
    ): InformationRequestEvidenceContent {
        queryService.findById(requestId, access)
        val retained = packageReader.findRetainedEvidence(requestId, packageId, evidenceVersionId)
            ?: retainedEvidence.refuseMissing(
                requestId,
                packageId,
                packageReader.retainedEvidenceRequirementIds(requestId, packageId),
                access,
            )
        return retainedEvidence.openContent(requestId, retained, access, use)
    }
}
