package com.docuhyphen.app.api.model.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.util.*

data class InformationRequestRetainedEvidenceVersion(
    val packageId: UUID,
    val submissionItemId: UUID,
    val requirementId: UUID,
    val artifactId: UUID,
    val versionId: UUID,
    val versionNumber: Int,
    val documentVersionId: UUID?,
    val contentHashAlgorithm: String?,
    val contentHash: String?,
    val contentLength: Long?,
)

class InformationRequestEvidenceRelease(
    val request: InformationRequest,
    val version: InformationRequestEvidenceVersion,
    val documentVersionId: UUID,
    val access: RequestAccessContext,
    val requirementId: UUID,
    val retained: InformationRequestRetainedEvidenceVersion? = null,
)
