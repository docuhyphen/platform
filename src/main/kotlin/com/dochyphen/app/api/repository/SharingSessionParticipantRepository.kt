package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.SharingSessionParticipant
import com.dochyphen.app.api.model.entity.SharingSessionParticipantType
import jakarta.enterprise.context.RequestScoped
import java.util.*

@RequestScoped
class SharingSessionParticipantRepository :
    BaseRepository<SharingSessionParticipant>(SharingSessionParticipant::class.java)
{
    fun countByOrganizationGroupId(groupId: UUID): Long
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(p) FROM SharingSessionParticipant p WHERE p.organizationGroup.id = :groupId AND " +
                    "p.participantType = :participantType",
            Long::class.java
        )
        query.setParameter("groupId", groupId)
        query.setParameter("participantType", SharingSessionParticipantType.GROUP)

        return query.singleResult
    }
}