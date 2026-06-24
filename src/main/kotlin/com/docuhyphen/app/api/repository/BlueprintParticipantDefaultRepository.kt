package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class BlueprintParticipantDefaultRepository :
    BaseRepository<BlueprintParticipantDefault>(BlueprintParticipantDefault::class.java)
{
    fun findAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): List<BlueprintParticipantDefault> =
        entityManager.createQuery(
            """SELECT p FROM BlueprintParticipantDefault p
               WHERE p.blueprintDefinitionId = :bid
               ORDER BY p.displayOrder ASC""",
            BlueprintParticipantDefault::class.java,
        )
            .setParameter("bid", blueprintDefinitionId)
            .resultList

    @Transactional
    fun deleteAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM BlueprintParticipantDefault p WHERE p.blueprintDefinitionId = :bid"
        )
            .setParameter("bid", blueprintDefinitionId)
            .executeUpdate()
}
