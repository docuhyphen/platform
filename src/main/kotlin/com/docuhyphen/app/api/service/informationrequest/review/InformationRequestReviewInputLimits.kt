package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDraftPatch
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog

object InformationRequestReviewInputLimits {
    const val MAXIMUM_TEXT_LENGTH = 4000
    const val MAXIMUM_REASON_LENGTH = 128

    fun requireText(value: String?, field: String, maximum: Int = MAXIMUM_TEXT_LENGTH) {
        if (value != null && value.length > maximum) refuse("$field holds at most $maximum characters")
    }

    fun requireWorksheet(entries: List<InformationRequestReviewDraftPatch>) {
        if (entries.map { it.submissionItemId }
                .toSet().size != entries.size) refuse("A worksheet names each submitted item once")
        entries.forEach { requireText(it.narrative, "A worksheet narrative") }
    }

    private fun refuse(message: String): Nothing = throw InformationRequestCommandRequestException(
        message, InformationRequestErrorCatalog.REVIEW_INPUT_INVALID,
    )
}