package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.SharingSession
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SharingSessionRepository : BaseRepository<SharingSession>(SharingSession::class.java)
{

    fun findByInitiatorId(initiatorId: UUID): List<SharingSession>
    {
        val query = entityManager.createQuery(
            "SELECT s FROM SharingSession s WHERE s.initiator.id = :initiatorId",
            SharingSession::class.java
        )
        query.setParameter("initiatorId", initiatorId)
        return query.resultList
    }

    fun findByReceiverId(receiverId: UUID): List<SharingSession>
    {
        val query = entityManager.createQuery(
            "SELECT s FROM SharingSession s WHERE s.receiver.id = :receiverId",
            SharingSession::class.java
        )
        query.setParameter("receiverId", receiverId)
        return query.resultList
    }
}