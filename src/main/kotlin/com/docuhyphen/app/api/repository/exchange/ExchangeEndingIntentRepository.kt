package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.model.entity.ExchangeEndingIntent
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ExchangeEndingIntentRepository :
    BaseRepository<ExchangeEndingIntent>(ExchangeEndingIntent::class.java) {
    fun findForExchange(exchangeId: UUID): List<ExchangeEndingIntent> =
        entityManager.createQuery(
            "SELECT intent FROM ExchangeEndingIntent intent WHERE intent.exchangeId = :exchangeId ORDER BY intent.createdAt",
            ExchangeEndingIntent::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList

    fun findForWorkflowInstance(workflowInstanceId: UUID): ExchangeEndingIntent? =
        entityManager.createQuery(
            "SELECT intent FROM ExchangeEndingIntent intent WHERE intent.workflowInstanceId = :instanceId",
            ExchangeEndingIntent::class.java,
        )
            .setParameter("instanceId", workflowInstanceId)
            .resultList
            .firstOrNull()
}
