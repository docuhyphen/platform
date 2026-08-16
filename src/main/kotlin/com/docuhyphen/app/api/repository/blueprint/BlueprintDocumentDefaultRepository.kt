package com.docuhyphen.app.api.repository.blueprint

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.BlueprintDocumentDefault
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class BlueprintDocumentDefaultRepository :
    BaseRepository<BlueprintDocumentDefault>(BlueprintDocumentDefault::class.java)
{
    fun findAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): List<BlueprintDocumentDefault> =
        entityManager.createQuery(
            """SELECT d FROM BlueprintDocumentDefault d
               WHERE d.blueprintDefinitionId = :bid
               ORDER BY d.displayOrder ASC""",
            BlueprintDocumentDefault::class.java,
        )
            .setParameter("bid", blueprintDefinitionId)
            .resultList

    @Transactional
    fun deleteAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM BlueprintDocumentDefault d WHERE d.blueprintDefinitionId = :bid"
        )
            .setParameter("bid", blueprintDefinitionId)
            .executeUpdate()
}
