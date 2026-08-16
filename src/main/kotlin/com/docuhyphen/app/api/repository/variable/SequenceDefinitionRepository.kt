package com.docuhyphen.app.api.repository.variable

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.SequenceDefinition
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SequenceDefinitionRepository : BaseRepository<SequenceDefinition>(SequenceDefinition::class.java)
{
    fun findAllByOrganizationIdAndIsDeletedFalse(organizationId: UUID): List<SequenceDefinition> =
        entityManager.createQuery(
            """SELECT s FROM SequenceDefinition s
               WHERE s.organizationId = :orgId AND s.isDeleted = FALSE
               ORDER BY s.createdAt DESC""",
            SequenceDefinition::class.java,
        )
            .setParameter("orgId", organizationId)
            .resultList

    fun findByOrganizationIdAndKeyAndIsDeletedFalse(organizationId: UUID, key: String): SequenceDefinition? =
        entityManager.createQuery(
            """SELECT s FROM SequenceDefinition s
               WHERE s.organizationId = :orgId AND s.key = :key AND s.isDeleted = FALSE""",
            SequenceDefinition::class.java,
        )
            .setParameter("orgId", organizationId)
            .setParameter("key", key)
            .resultList
            .firstOrNull()
}
