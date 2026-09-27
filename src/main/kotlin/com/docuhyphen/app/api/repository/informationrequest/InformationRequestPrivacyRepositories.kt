package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestItemCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequest
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyTargetOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestSubjectRestriction
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestPrivacyTarget
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class InformationRequestPrivacyRequestRepository :
    BaseRepository<InformationRequestPrivacyRequest>(InformationRequestPrivacyRequest::class.java)
{
    fun findForOwner(ownerKind: RecordOwnerKind, ownerId: UUID, subjectIdentityRefId: UUID?): List<InformationRequestPrivacyRequest>
    {
        val subjectClause = if (subjectIdentityRefId != null) "AND privacy.subjectIdentityRefId = :subject" else ""
        val query = entityManager.createQuery(
            """
            SELECT privacy
            FROM InformationRequestPrivacyRequest privacy
            WHERE privacy.ownerKind = :ownerKind
              AND privacy.ownerId = :ownerId
              $subjectClause
            ORDER BY privacy.recordedAt DESC, privacy.id
            """.trimIndent(),
            InformationRequestPrivacyRequest::class.java,
        )
            .setParameter("ownerKind", ownerKind)
            .setParameter("ownerId", ownerId)
        subjectIdentityRefId?.let { query.setParameter("subject", it) }
        return query.resultList
    }

    fun findForUpdate(id: UUID): InformationRequestPrivacyRequest? =
        entityManager.find(InformationRequestPrivacyRequest::class.java, id, LockModeType.PESSIMISTIC_WRITE)

    fun insertTarget(privacyRequestId: UUID, requestId: UUID, outcome: InformationRequestPrivacyTargetOutcome, reasonCode: String?, claimId: UUID?)
    {
        entityManager.createNativeQuery(
            """
            INSERT INTO information_request_privacy_target
                (privacy_request_id, information_request_id, outcome, reason_code, disposal_claim_id)
            VALUES (:privacy, :request, :outcome, :reason, :claim)
            """.trimIndent(),
        )
            .setParameter("privacy", privacyRequestId)
            .setParameter("request", requestId)
            .setParameter("outcome", outcome.name)
            .setParameter("reason", reasonCode)
            .setParameter("claim", claimId)
            .executeUpdate()
    }

    fun targetsOf(privacyRequestId: UUID): List<InformationRequestPrivacyTarget> =
        entityManager.createNativeQuery(
            """
            SELECT information_request_id, outcome, reason_code, disposal_claim_id
            FROM information_request_privacy_target
            WHERE privacy_request_id = :privacy
            ORDER BY recorded_at, information_request_id
            """.trimIndent(),
        )
            .setParameter("privacy", privacyRequestId)
            .resultList
            .map { row ->
                val columns = row as Array<*>
                InformationRequestPrivacyTarget(
                    requestId = columns[0] as UUID,
                    outcome = InformationRequestPrivacyTargetOutcome.valueOf(columns[1] as String),
                    reasonCode = columns[2] as String?,
                    disposalClaimId = columns[3] as UUID?,
                )
            }

    fun subjectRequestIds(ownerKind: RecordOwnerKind, ownerId: UUID, subjectIdentityRefId: UUID): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT DISTINCT request.id
            FROM information_request request
                     JOIN information_request_party party ON party.information_request_id = request.id
            WHERE party.role_key = 'SUBJECT'
              AND party.subject_identity_ref_id = :subject
              AND request.owner_type = :ownerKind
              AND COALESCE(request.owner_organization_id, request.owner_user_id) = :ownerId
            ORDER BY request.id
            """.trimIndent(),
        )
            .setParameter("subject", subjectIdentityRefId)
            .setParameter("ownerKind", ownerKind.name)
            .setParameter("ownerId", ownerId)
            .resultList
            .map { it as UUID }

    fun subjectOwnedBy(subjectIdentityRefId: UUID, ownerKind: RecordOwnerKind, ownerId: UUID): Boolean =
        (entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM subject_identity_ref WHERE id = :subject AND owner_type = :ownerKind AND owner_id = :ownerId",
        )
            .setParameter("subject", subjectIdentityRefId)
            .setParameter("ownerKind", ownerKind.name)
            .setParameter("ownerId", ownerId)
            .singleResult as Number).toLong() > 0
}

@ApplicationScoped
class InformationRequestSubjectRestrictionRepository :
    BaseRepository<InformationRequestSubjectRestriction>(InformationRequestSubjectRestriction::class.java)
{
    fun findActive(ownerKind: RecordOwnerKind, ownerId: UUID, subjectIdentityRefId: UUID): InformationRequestSubjectRestriction? =
        entityManager.createQuery(
            """
            SELECT restriction
            FROM InformationRequestSubjectRestriction restriction
            WHERE restriction.ownerKind = :ownerKind
              AND restriction.ownerId = :ownerId
              AND restriction.subjectIdentityRefId = :subject
              AND restriction.liftedAt IS NULL
            """.trimIndent(),
            InformationRequestSubjectRestriction::class.java,
        )
            .setParameter("ownerKind", ownerKind)
            .setParameter("ownerId", ownerId)
            .setParameter("subject", subjectIdentityRefId)
            .resultList
            .firstOrNull()

    fun findForOwner(ownerKind: RecordOwnerKind, ownerId: UUID): List<InformationRequestSubjectRestriction> =
        entityManager.createQuery(
            """
            SELECT restriction
            FROM InformationRequestSubjectRestriction restriction
            WHERE restriction.ownerKind = :ownerKind
              AND restriction.ownerId = :ownerId
            ORDER BY restriction.restrictedAt DESC, restriction.id
            """.trimIndent(),
            InformationRequestSubjectRestriction::class.java,
        )
            .setParameter("ownerKind", ownerKind)
            .setParameter("ownerId", ownerId)
            .resultList
}

@ApplicationScoped
class InformationRequestItemCorrectionRepository :
    BaseRepository<InformationRequestItemCorrection>(InformationRequestItemCorrection::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestItemCorrection> =
        entityManager.createQuery(
            """
            SELECT correction
            FROM InformationRequestItemCorrection correction
            WHERE correction.informationRequestId = :requestId
            ORDER BY correction.recordedAt, correction.id
            """.trimIndent(),
            InformationRequestItemCorrection::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
