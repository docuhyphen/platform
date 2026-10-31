package com.docuhyphen.app.api.service.informationrequest.review.model

import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class AssignmentChangeSetup(
    val fixture: ReviewFixture,
    val services: InformationRequestRuntimeServices,
    val review: InformationRequestReview,
    val documentItem: UUID,
    val colleagueParty: UUID,
    val colleagueUser: UUID,
)
