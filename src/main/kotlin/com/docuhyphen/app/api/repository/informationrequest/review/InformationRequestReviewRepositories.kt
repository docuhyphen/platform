package com.docuhyphen.app.api.repository.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionItem
import com.docuhyphen.app.api.model.entity.InformationRequestCorrectionState
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewComment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecision
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDraftItem
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.model.entity.InformationRequestReviewRemediation
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestReviewRepository :
    BaseRepository<InformationRequestReview>(InformationRequestReview::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestReview> =
        entityManager.createQuery(
            """
            SELECT review
            FROM InformationRequestReview review
            WHERE review.informationRequestId = :requestId
            ORDER BY review.reviewNumber
            """.trimIndent(),
            InformationRequestReview::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForIds(ids: Collection<UUID>): List<InformationRequestReview>
    {
        if (ids.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT review FROM InformationRequestReview review WHERE review.id IN :ids",
            InformationRequestReview::class.java,
        )
            .setParameter("ids", ids)
            .resultList
    }

    fun nextReviewNumber(requestId: UUID): Int = findForRequest(requestId).maxOfOrNull { it.reviewNumber }?.plus(1) ?: 1
}

@ApplicationScoped
class InformationRequestReviewAssignmentRepository :
    BaseRepository<InformationRequestReviewAssignment>(InformationRequestReviewAssignment::class.java)
{
    fun findForReview(reviewId: UUID): List<InformationRequestReviewAssignment> = findForReviews(listOf(reviewId))

    fun findForReviews(reviewIds: Collection<UUID>): List<InformationRequestReviewAssignment>
    {
        if (reviewIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT assignment
            FROM InformationRequestReviewAssignment assignment
            WHERE assignment.reviewId IN :reviewIds
            ORDER BY assignment.assignedAt, assignment.id
            """.trimIndent(),
            InformationRequestReviewAssignment::class.java,
        )
            .setParameter("reviewIds", reviewIds)
            .resultList
    }

    fun findActiveForPrincipal(kind: PrincipalKind, principalId: UUID): List<InformationRequestReviewAssignment> =
        entityManager.createQuery(
            """
            SELECT assignment
            FROM InformationRequestReviewAssignment assignment
            WHERE assignment.reviewerPrincipalKind = :kind
              AND assignment.reviewerPrincipalId = :principalId
              AND assignment.state = :state
            ORDER BY assignment.assignedAt, assignment.id
            """.trimIndent(),
            InformationRequestReviewAssignment::class.java,
        )
            .setParameter("kind", kind)
            .setParameter("principalId", principalId)
            .setParameter("state", InformationRequestReviewAssignmentState.ACTIVE)
            .resultList
}

@ApplicationScoped
class InformationRequestReviewDraftItemRepository :
    BaseRepository<InformationRequestReviewDraftItem>(InformationRequestReviewDraftItem::class.java)
{
    fun findForAssignment(assignmentId: UUID): List<InformationRequestReviewDraftItem> =
        entityManager.createQuery(
            """
            SELECT draft
            FROM InformationRequestReviewDraftItem draft
            WHERE draft.assignmentId = :assignmentId
            ORDER BY draft.submissionItemId
            """.trimIndent(),
            InformationRequestReviewDraftItem::class.java,
        )
            .setParameter("assignmentId", assignmentId)
            .resultList
}

@ApplicationScoped
class InformationRequestReviewDecisionRepository :
    BaseRepository<InformationRequestReviewDecision>(InformationRequestReviewDecision::class.java)
{
    fun findForReview(reviewId: UUID): List<InformationRequestReviewDecision> = findForReviews(listOf(reviewId))

    fun findForReviews(reviewIds: Collection<UUID>): List<InformationRequestReviewDecision>
    {
        if (reviewIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT decision
            FROM InformationRequestReviewDecision decision
            WHERE decision.reviewId IN :reviewIds
            ORDER BY decision.reviewId, decision.sequenceNumber
            """.trimIndent(),
            InformationRequestReviewDecision::class.java,
        )
            .setParameter("reviewIds", reviewIds)
            .resultList
    }

    fun nextSequenceNumber(reviewId: UUID): Int = findForReview(reviewId).maxOfOrNull { it.sequenceNumber }?.plus(1) ?: 1
}

@ApplicationScoped
class InformationRequestReviewFindingRepository :
    BaseRepository<InformationRequestReviewFinding>(InformationRequestReviewFinding::class.java)
{
    fun findForReview(reviewId: UUID): List<InformationRequestReviewFinding> = findForReviews(listOf(reviewId))

    fun findForReviews(reviewIds: Collection<UUID>): List<InformationRequestReviewFinding>
    {
        if (reviewIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT finding
            FROM InformationRequestReviewFinding finding
            WHERE finding.reviewId IN :reviewIds
            ORDER BY finding.reviewId, finding.sequenceNumber
            """.trimIndent(),
            InformationRequestReviewFinding::class.java,
        )
            .setParameter("reviewIds", reviewIds)
            .resultList
    }

    fun nextSequenceNumber(reviewId: UUID): Int = findForReview(reviewId).maxOfOrNull { it.sequenceNumber }?.plus(1) ?: 1
}

@ApplicationScoped
class InformationRequestCorrectionRepository :
    BaseRepository<InformationRequestCorrection>(InformationRequestCorrection::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestCorrection> =
        entityManager.createQuery(
            """
            SELECT correction
            FROM InformationRequestCorrection correction
            WHERE correction.informationRequestId = :requestId
            ORDER BY correction.openedAt, correction.id
            """.trimIndent(),
            InformationRequestCorrection::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findOpenForRequest(requestId: UUID): List<InformationRequestCorrection> =
        findForRequest(requestId).filter { it.state == InformationRequestCorrectionState.OPEN }
}

@ApplicationScoped
class InformationRequestCorrectionItemRepository :
    BaseRepository<InformationRequestCorrectionItem>(InformationRequestCorrectionItem::class.java)
{
    fun findForCorrections(correctionIds: Collection<UUID>): List<InformationRequestCorrectionItem>
    {
        if (correctionIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT corrected
            FROM InformationRequestCorrectionItem corrected
            WHERE corrected.correctionId IN :correctionIds
            """.trimIndent(),
            InformationRequestCorrectionItem::class.java,
        )
            .setParameter("correctionIds", correctionIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestCorrectionEvidenceRepository :
    BaseRepository<InformationRequestCorrectionEvidence>(InformationRequestCorrectionEvidence::class.java)
{
    fun findForCorrections(correctionIds: Collection<UUID>): List<InformationRequestCorrectionEvidence>
    {
        if (correctionIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT returned
            FROM InformationRequestCorrectionEvidence returned
            WHERE returned.correctionId IN :correctionIds
            """.trimIndent(),
            InformationRequestCorrectionEvidence::class.java,
        )
            .setParameter("correctionIds", correctionIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestReviewCommentRepository :
    BaseRepository<InformationRequestReviewComment>(InformationRequestReviewComment::class.java)
{
    fun findForReviews(reviewIds: Collection<UUID>): List<InformationRequestReviewComment>
    {
        if (reviewIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT comment
            FROM InformationRequestReviewComment comment
            WHERE comment.reviewId IN :reviewIds
            ORDER BY comment.reviewId, comment.sequenceNumber
            """.trimIndent(),
            InformationRequestReviewComment::class.java,
        )
            .setParameter("reviewIds", reviewIds)
            .resultList
    }

    fun nextSequenceNumber(reviewId: UUID): Int =
        findForReviews(listOf(reviewId)).maxOfOrNull { it.sequenceNumber }?.plus(1) ?: 1
}

@ApplicationScoped
class InformationRequestReviewRemediationRepository :
    BaseRepository<InformationRequestReviewRemediation>(InformationRequestReviewRemediation::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestReviewRemediation> =
        entityManager.createQuery(
            """
            SELECT remediation
            FROM InformationRequestReviewRemediation remediation
            WHERE remediation.informationRequestId = :requestId
            ORDER BY remediation.recordedAt, remediation.id
            """.trimIndent(),
            InformationRequestReviewRemediation::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
