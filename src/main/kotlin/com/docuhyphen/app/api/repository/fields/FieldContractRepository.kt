package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class FieldContractRepository :
    BaseRepository<FieldContract>(FieldContract::class.java)
{
    fun findByDefinition(fieldDefinitionId: UUID): List<FieldContract> =
        entityManager.createQuery(
            """SELECT c FROM FieldContract c
               WHERE c.fieldDefinitionId = :did
               ORDER BY c.contractVersion DESC""",
            FieldContract::class.java,
        )
            .setParameter("did", fieldDefinitionId)
            .resultList

    fun findMaxVersion(fieldDefinitionId: UUID): Int =
        entityManager.createQuery(
            """SELECT COALESCE(MAX(c.contractVersion), 0) FROM FieldContract c
               WHERE c.fieldDefinitionId = :did""",
            Integer::class.java,
        )
            .setParameter("did", fieldDefinitionId)
            .singleResult
            .toInt()

    fun findByIds(ids: Collection<UUID>): List<FieldContract>
    {
        if (ids.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT c FROM FieldContract c WHERE c.id IN :ids",
            FieldContract::class.java,
        )
            .setParameter("ids", ids)
            .resultList
    }
}
