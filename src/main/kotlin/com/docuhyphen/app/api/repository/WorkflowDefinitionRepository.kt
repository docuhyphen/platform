package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowDefinitionRepository :
    BaseRepository<WorkflowDefinition>(WorkflowDefinition::class.java)
{
    /**
     * Latest-active definition for a given trigger. Resolution order:
     *   1. Org-scoped definition for [organizationId] (most specific).
     *   2. App-scoped definition (fallback).
     * Within each tier, highest version wins.
     */
    fun findActiveForTrigger(triggerEvent: String, organizationId: UUID?): WorkflowDefinition?
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
                .setMaxResults(1)
                .resultList
                .firstOrNull()
            if (orgScoped != null) return orgScoped
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
            .setMaxResults(1)
            .resultList
            .firstOrNull()
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
     *   - Platform templates (isTemplate = true, any scope)
     *   - ORG-scoped definitions owned by [organizationId] (when present)
     *   - PERSONAL definitions owned by [callerUserId] (when present)
     */
    fun findAllAccessibleForCaller(callerUserId: UUID?, organizationId: UUID?): List<WorkflowDefinition>
    {
        return when
        {
            callerUserId != null && organizationId != null ->
                entityManager.createQuery(
                    """SELECT d FROM WorkflowDefinition d
                       WHERE d.isDeleted = false
                         AND (d.isTemplate = true
                              OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.ORG
                                  AND d.organizationId = :oid)
                              OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.PERSONAL
                                  AND d.createdByAppUserId = :uid))
                       ORDER BY d.createdAt DESC""",
                    WorkflowDefinition::class.java,
                )
                    .setParameter("oid", organizationId)
                    .setParameter("uid", callerUserId)
                    .resultList

            callerUserId != null ->
                entityManager.createQuery(
                    """SELECT d FROM WorkflowDefinition d
                       WHERE d.isDeleted = false
                         AND (d.isTemplate = true
                              OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.PERSONAL
                                  AND d.createdByAppUserId = :uid))
                       ORDER BY d.createdAt DESC""",
                    WorkflowDefinition::class.java,
                )
                    .setParameter("uid", callerUserId)
                    .resultList

            else ->
                entityManager.createQuery(
                    """SELECT d FROM WorkflowDefinition d
                       WHERE d.isDeleted = false
                         AND d.isTemplate = true
                       ORDER BY d.createdAt DESC""",
                    WorkflowDefinition::class.java,
                ).resultList
        }
    }
}

