package com.docuhyphen.app.api.repository.blueprint

import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class BlueprintDefinitionRepository :
    BaseRepository<BlueprintDefinition>(BlueprintDefinition::class.java)
{
    fun findAllAccessibleForCaller(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
    ): List<BlueprintDefinition>
    {
        return if (callerOrgId != null)
        {
            entityManager.createQuery(
                """SELECT b FROM BlueprintDefinition b
                   WHERE b.isDeleted = false
                     AND (
                           (b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.PERSONAL
                            AND b.createdByAppUserId = :uid)
                        OR (b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.ORG
                            AND b.organizationId = :oid
                            AND (:showUnpublished = true OR b.isPublished = true))
                        OR (b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.APP
                            AND b.isTemplate = true)
                     )
                   ORDER BY b.updatedAt DESC""",
                BlueprintDefinition::class.java,
            )
                .setParameter("uid", callerUserId)
                .setParameter("oid", callerOrgId)
                .setParameter("showUnpublished", isOrgAdmin)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT b FROM BlueprintDefinition b
                   WHERE b.isDeleted = false
                     AND (
                           (b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.PERSONAL
                            AND b.createdByAppUserId = :uid)
                        OR (b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.APP
                            AND b.isTemplate = true)
                     )
                   ORDER BY b.updatedAt DESC""",
                BlueprintDefinition::class.java,
            )
                .setParameter("uid", callerUserId)
                .resultList
        }
    }

    fun findOrgBlueprints(organizationId: UUID): List<BlueprintDefinition> =
        entityManager.createQuery(
            """SELECT b FROM BlueprintDefinition b
               WHERE b.isDeleted = false
                 AND b.scope = com.docuhyphen.app.api.model.entity.BlueprintScope.ORG
                 AND b.organizationId = :oid
               ORDER BY b.updatedAt DESC""",
            BlueprintDefinition::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList

    fun findByName(name: String, callerUserId: UUID): BlueprintDefinition? =
        entityManager.createQuery(
            """SELECT b FROM BlueprintDefinition b
               WHERE b.name = :n AND b.isDeleted = false AND b.createdByAppUserId = :uid""",
            BlueprintDefinition::class.java,
        )
            .setParameter("n", name)
            .setParameter("uid", callerUserId)
            .resultList
            .firstOrNull()
}
