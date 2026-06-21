package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.model.entity.VariableScope
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class VariableDefinitionRepository : BaseRepository<VariableDefinition>(VariableDefinition::class.java)
{
    fun findByScopeAndOrganizationIdAndIsDeletedFalse(scope: VariableScope, organizationId: UUID): List<VariableDefinition> =
        entityManager.createQuery(
            """SELECT v FROM VariableDefinition v
               WHERE v.scope = :scope AND v.organizationId = :orgId AND v.isDeleted = FALSE
               ORDER BY v.createdAt DESC""",
            VariableDefinition::class.java,
        )
            .setParameter("scope", scope)
            .setParameter("orgId", organizationId)
            .resultList

    fun findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(scope: VariableScope, userId: UUID): List<VariableDefinition> =
        entityManager.createQuery(
            """SELECT v FROM VariableDefinition v
               WHERE v.scope = :scope AND v.createdByAppUserId = :userId AND v.isDeleted = FALSE
               ORDER BY v.createdAt DESC""",
            VariableDefinition::class.java,
        )
            .setParameter("scope", scope)
            .setParameter("userId", userId)
            .resultList

    fun findByOrganizationIdAndKeyAndIsDeletedFalse(organizationId: UUID, key: String): VariableDefinition? =
        entityManager.createQuery(
            """SELECT v FROM VariableDefinition v
               WHERE v.organizationId = :orgId AND v.key = :key AND v.isDeleted = FALSE""",
            VariableDefinition::class.java,
        )
            .setParameter("orgId", organizationId)
            .setParameter("key", key)
            .resultList
            .firstOrNull()

    fun findByCreatedByAppUserIdAndKeyAndIsDeletedFalse(userId: UUID, key: String): VariableDefinition? =
        entityManager.createQuery(
            """SELECT v FROM VariableDefinition v
               WHERE v.createdByAppUserId = :userId AND v.key = :key AND v.scope = 'PERSONAL' AND v.isDeleted = FALSE""",
            VariableDefinition::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("key", key)
            .resultList
            .firstOrNull()
}
