package com.docuhyphen.app.api.service.informationrequest.submission.model

import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestSubmissionAttestationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService

internal data class SubmissionServices(
    val submissions: InformationRequestSubmissionService,
    val attestations: InformationRequestSubmissionAttestationService,
    val evidence: InformationRequestEvidenceCollectionService,
    val responses: InformationRequestResponseDraftService,
)
