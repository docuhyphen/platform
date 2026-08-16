package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ExchangeRecipientAttestationRepository :
    BaseRepository<ExchangeRecipientAttestation>(ExchangeRecipientAttestation::class.java)
{
    fun findByExchangeRecipientId(exchangeRecipientId: UUID): ExchangeRecipientAttestation? =
        entityManager.createQuery(
            """SELECT a FROM ExchangeRecipientAttestation a
               WHERE a.exchangeRecipientId = :exchangeRecipientId""",
            ExchangeRecipientAttestation::class.java,
        )
            .setParameter("exchangeRecipientId", exchangeRecipientId)
            .resultList
            .singleOrNull()

    fun findByRelationshipId(relationshipId: UUID): List<ExchangeRecipientAttestation> =
        entityManager.createQuery(
            """SELECT a FROM ExchangeRecipientAttestation a
               WHERE a.relationshipId = :relationshipId""",
            ExchangeRecipientAttestation::class.java,
        )
            .setParameter("relationshipId", relationshipId)
            .resultList
}
