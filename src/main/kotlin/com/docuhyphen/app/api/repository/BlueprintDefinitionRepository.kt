package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class BlueprintDefinitionRepository :
    BaseRepository<BlueprintDefinition>(BlueprintDefinition::class.java)
{
    /**
     * Returns all blueprints the caller may see:
     * - PERSONAL blueprints owned by [callerUserId]
     * - ORG blueprints for [callerOrgId] (published-only unless [isOrgAdmin] or [isAppAdmin])
     * - APP-scoped platform templates (isTemplate=true, always visible)
     *
     * Org admins CANNOT see other users' PERSONAL blueprints.
     */
    fun findAllAccessibleForCaller(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): List<BlueprintDefinition>
    {
        val showUnpublishedOrg = isOrgAdmin || isAppAdmin

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
                .setParameter("showUnpublished", showUnpublishedOrg)
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

    /** All ORG-scoped blueprints for [organizationId] regardless of isPublished (admin management view). */
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
