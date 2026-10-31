package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewReopenFacts
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReviewReopenPolicy @Inject constructor(
    private val reviewRepository: InformationRequestReviewRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val lockService: InformationRequestSubmissionLockService,
) {
    fun factsFor(requestId: UUID): InformationRequestReviewReopenFacts =
        InformationRequestReviewReopenFacts(
            latestReviewIdByPackage = reviewRepository.findForRequest(requestId)
                .groupBy { it.packageId }
                .mapValues { (_, reviews) -> reviews.maxBy { it.reviewNumber }.id },
            currentPackageIds = lockService.activePackages(requestId).map { it.id }.toSet(),
            correctionByReview = correctionRepository.findForRequest(requestId).associateBy { it.reviewId },
        )

    fun reopenable(review: InformationRequestReview): Boolean =
        reopenable(review, factsFor(review.informationRequestId))

    fun reopenable(review: InformationRequestReview, facts: InformationRequestReviewReopenFacts): Boolean =
        facts.latestReviewIdByPackage[review.packageId] == review.id &&
                review.packageId in facts.currentPackageIds &&
                when (review.state) {
                    InformationRequestReviewState.REJECTED -> true
                    InformationRequestReviewState.CHANGES_REQUESTED ->
                        facts.correctionByReview[review.id]?.state == InformationRequestCorrectionState.OPEN

                    else -> false
                }
}
