package com.docuhyphen.app.api.service.informationrequest.review.model

import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class ReturnedReviewScenario(
    val fixture: ReviewFixture,
    val services: InformationRequestRuntimeServices,
    val first: InformationRequestReview,
    val shown: InformationRequestReviewFinding,
    val reviewersOnly: InformationRequestReviewFinding,
    val hiddenRequirement: InformationRequestReviewFinding,
    val resubmittedPackageId: UUID,
)
