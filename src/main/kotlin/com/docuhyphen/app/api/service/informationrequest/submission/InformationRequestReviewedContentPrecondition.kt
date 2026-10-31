package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException

object InformationRequestReviewedContentPrecondition {
    fun requireSatisfiedBy(precondition: CommandPrecondition, currentETag: String) {
        if (precondition !is CommandPrecondition.ExpectedRevision) {
            throw CommandPreconditionException.required(currentETag)
        }
        precondition.requireSatisfiedBy(currentETag)
    }
}
