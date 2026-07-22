package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class ExchangeRecipientRepository : BaseRepository<ExchangeRecipient>(ExchangeRecipient::class.java)
{
    fun findByDirectShareId(directShareId: UUID): ExchangeRecipient? =
        entityManager.createQuery(
            """SELECT r FROM ExchangeRecipient r
               WHERE r.directShareId = :directShareId""",
            ExchangeRecipient::class.java,
        )
            .setParameter("directShareId", directShareId)
            .resultList
            .singleOrNull()

    fun findByExchangeId(exchangeId: UUID): List<ExchangeRecipient> =
        entityManager.createQuery(
            """SELECT r FROM ExchangeRecipient r
               WHERE r.exchangeId = :exchangeId
               ORDER BY r.createdAt ASC, r.id ASC""",
            ExchangeRecipient::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList

    fun findPrimary(exchangeId: UUID): ExchangeRecipient? =
        entityManager.createQuery(
            """SELECT r FROM ExchangeRecipient r
               WHERE r.exchangeId = :exchangeId AND r.purpose = :purpose""",
            ExchangeRecipient::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .setParameter("purpose", ExchangeRecipientPurpose.PRIMARY)
            .resultList
            .singleOrNull()

    fun findPrimaryForUpdate(exchangeId: UUID): ExchangeRecipient? =
        entityManager.createQuery(
            """SELECT r FROM ExchangeRecipient r
               WHERE r.exchangeId = :exchangeId AND r.purpose = :purpose""",
            ExchangeRecipient::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .setParameter("purpose", ExchangeRecipientPurpose.PRIMARY)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .singleOrNull()

    fun findPendingTrustedParticipants(exchangeId: UUID): List<ExchangeRecipient> =
        entityManager.createQuery(
            """SELECT r FROM ExchangeRecipient r
               WHERE r.exchangeId = :exchangeId
               AND r.purpose = :purpose
               AND r.acceptanceStatus = :status
               AND r.selectionType IN :selectionTypes""",
            ExchangeRecipient::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .setParameter("purpose", ExchangeRecipientPurpose.PARTICIPANT)
            .setParameter("status", ExchangeRecipientAcceptanceStatus.PENDING)
            .setParameter(
                "selectionTypes",
                setOf(
                    ExchangeRecipientSelectionType.TRUSTED_PERSON,
                    ExchangeRecipientSelectionType.TRUSTED_GROUP,
                ),
            )
            .resultList

    fun findPendingTrustedParticipantsFor(appUserId: UUID): List<ExchangeRecipient> =
        entityManager.createQuery(
            """SELECT DISTINCT r FROM ExchangeRecipient r, Share s
               WHERE r.purpose = :purpose
               AND r.acceptanceStatus = :status
               AND s.id = r.directShareId
               AND s.source = :directSource
               AND s.sourceShareId IS NULL
               AND s.status = :shareStatus
               AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)
               AND (
                   (r.selectionType = :trustedPerson
                       AND s.principalKind = :userKind
                       AND s.principalId = :appUserId)
                   OR
                   (r.selectionType = :trustedGroup
                       AND s.principalKind = :groupKind
                       AND EXISTS (
                           SELECT m.id FROM PrincipalGroupMember m
                           WHERE m.principalGroupId = s.principalId
                           AND m.principalKind = :userKind
                           AND m.principalId = :appUserId
                           AND m.isActive = true
                           AND m.groupRole IN :decisionRoles
                       ))
               )
               ORDER BY r.createdAt ASC, r.id ASC""",
            ExchangeRecipient::class.java,
        )
            .setParameter("purpose", ExchangeRecipientPurpose.PARTICIPANT)
            .setParameter("status", ExchangeRecipientAcceptanceStatus.PENDING)
            .setParameter("directSource", ShareSource.DIRECT)
            .setParameter("shareStatus", ShareStatus.PENDING_APPROVAL)
            .setParameter("trustedPerson", ExchangeRecipientSelectionType.TRUSTED_PERSON)
            .setParameter("trustedGroup", ExchangeRecipientSelectionType.TRUSTED_GROUP)
            .setParameter("userKind", PrincipalKind.USER)
            .setParameter("groupKind", PrincipalKind.PRINCIPAL_GROUP)
            .setParameter("appUserId", appUserId)
            .setParameter(
                "decisionRoles",
                setOf(PrincipalGroupRoleName.OWNER, PrincipalGroupRoleName.MANAGER),
            )
            .resultList
}
