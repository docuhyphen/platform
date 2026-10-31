package com.docuhyphen.app.api.repository.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ParticipantAccountLinkRepository :
    BaseRepository<ParticipantAccountLink>(ParticipantAccountLink::class.java)
{
    fun findByParticipantId(participantId: UUID): ParticipantAccountLink? =
        entityManager.createQuery(
            """
            SELECT link
            FROM ParticipantAccountLink link
            WHERE link.participantId = :participantId
            """.trimIndent(),
            ParticipantAccountLink::class.java,
        )
            .setParameter("participantId", participantId)
            .resultList
            .firstOrNull()

    fun findForAppUser(appUserId: UUID): List<ParticipantAccountLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM ParticipantAccountLink link
            WHERE link.appUserId = :appUserId
            ORDER BY link.linkedAt, link.id
            """.trimIndent(),
            ParticipantAccountLink::class.java,
        )
            .setParameter("appUserId", appUserId)
            .resultList
}
