package com.docuhyphen.app.api.repository.blueprint

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.BlueprintFieldDefault
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class BlueprintFieldDefaultRepository :
    BaseRepository<BlueprintFieldDefault>(BlueprintFieldDefault::class.java)
{
    fun findAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): List<BlueprintFieldDefault> =
        entityManager.createQuery(
            """SELECT f FROM BlueprintFieldDefault f
               WHERE f.blueprintDefinitionId = :bid
               ORDER BY f.displayOrder ASC""",
            BlueprintFieldDefault::class.java,
        )
            .setParameter("bid", blueprintDefinitionId)
            .resultList

    @Transactional
    fun deleteAllByBlueprintDefinitionId(blueprintDefinitionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM BlueprintFieldDefault f WHERE f.blueprintDefinitionId = :bid"
        )
            .setParameter("bid", blueprintDefinitionId)
            .executeUpdate()
}
