package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowDefinitionRepository :
    BaseRepository<WorkflowDefinition>(WorkflowDefinition::class.java)
{
    /**
     * All active definitions that should fire for a given trigger event. Resolution order:
     *   1. All ORG-scoped definitions for [organizationId] (if any exist, APP tier is skipped).
     *   2. All APP-scoped definitions (fallback when no ORG definitions match).
     * Within each tier all matching definitions are returned so every configured workflow fires.
     */
    fun findAllActiveForTrigger(triggerEvent: String, organizationId: UUID?): List<WorkflowDefinition>
    {
        if (organizationId != null)
        {
            val orgScoped = entityManager.createQuery(
                """SELECT d FROM WorkflowDefinition d
                   WHERE d.triggerEvent = :ev
                     AND d.isActive = true
                     AND d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.ORG
                     AND d.organizationId = :oid
                   ORDER BY d.version DESC""",
                WorkflowDefinition::class.java,
            )
                .setParameter("ev", triggerEvent)
                .setParameter("oid", organizationId)
                .resultList
            if (orgScoped.isNotEmpty()) return orgScoped
        }
        return entityManager.createQuery(
            """SELECT d FROM WorkflowDefinition d
               WHERE d.triggerEvent = :ev
                 AND d.isActive = true
                 AND d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.APP
               ORDER BY d.version DESC""",
            WorkflowDefinition::class.java,
        )
            .setParameter("ev", triggerEvent)
            .resultList
    }

    fun findByNameAndVersion(name: String, version: Int): WorkflowDefinition? =
        entityManager.createQuery(
            "SELECT d FROM WorkflowDefinition d WHERE d.name = :n AND d.version = :v AND d.isDeleted = false",
            WorkflowDefinition::class.java,
        )
            .setParameter("n", name)
            .setParameter("v", version)
            .resultList
            .firstOrNull()

    fun findByNameVersionAndCreator(name: String, version: Int, creatorId: UUID): WorkflowDefinition? =
        entityManager.createQuery(
            """SELECT d FROM WorkflowDefinition d
               WHERE d.name = :n AND d.version = :v AND d.createdByAppUserId = :uid AND d.isDeleted = false""",
            WorkflowDefinition::class.java,
        )
            .setParameter("n", name)
            .setParameter("v", version)
            .setParameter("uid", creatorId)
            .resultList
            .firstOrNull()

    /**
     * All definitions accessible to the caller:
     *   - APP templates for authenticated users and every APP definition for app admins
     *   - ORG definitions owned by [organizationId], published-only unless [isOrgAdmin]
     *   - PERSONAL definitions owned by [callerUserId]
     */
    fun findAllAccessibleForCaller(
        callerUserId: UUID,
        organizationId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): List<WorkflowDefinition>
    {
        return if (organizationId != null)
        {
            entityManager.createQuery(
                """SELECT d FROM WorkflowDefinition d
                   WHERE d.isDeleted = false
                     AND (
                           (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.PERSONAL
                            AND d.createdByAppUserId = :uid)
                        OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.ORG
                            AND d.organizationId = :oid
                            AND (:showUnpublishedOrg = true OR d.isPublished = true))
                        OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.APP
                            AND (:showAllApp = true OR d.isTemplate = true))
                     )
                   ORDER BY d.createdAt DESC""",
                WorkflowDefinition::class.java,
            )
                .setParameter("uid", callerUserId)
                .setParameter("oid", organizationId)
                .setParameter("showUnpublishedOrg", isOrgAdmin)
                .setParameter("showAllApp", isAppAdmin)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT d FROM WorkflowDefinition d
                   WHERE d.isDeleted = false
                     AND (
                           (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.PERSONAL
                            AND d.createdByAppUserId = :uid)
                        OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.APP
                            AND (:showAllApp = true OR d.isTemplate = true))
                     )
                   ORDER BY d.createdAt DESC""",
                WorkflowDefinition::class.java,
            )
                .setParameter("uid", callerUserId)
                .setParameter("showAllApp", isAppAdmin)
                .resultList
        }
    }
}

