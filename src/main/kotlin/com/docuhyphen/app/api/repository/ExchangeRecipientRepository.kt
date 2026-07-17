package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
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
}
