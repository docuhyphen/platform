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
            "SELECT d FROM WorkflowDefinition d WHERE d.name = :n AND d.version = :v",
            WorkflowDefinition::class.java,
        )
            .setParameter("n", name)
            .setParameter("v", version)
            .resultList
            .firstOrNull()

    /**
     * All definitions accessible to [organizationId]: platform templates (isTemplate=true)
     * plus any ORG-scoped definitions owned by that org. When [organizationId] is null,
     * only platform templates are returned (caller has no org context).
     */
    fun findAllAccessibleForOrg(organizationId: UUID?): List<WorkflowDefinition>
    {
        return if (organizationId != null)
        {
            entityManager.createQuery(
                """SELECT d FROM WorkflowDefinition d
                   WHERE d.isTemplate = true
                      OR (d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.ORG
                          AND d.organizationId = :oid)
                   ORDER BY d.createdAt DESC""",
                WorkflowDefinition::class.java,
            )
                .setParameter("oid", organizationId)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT d FROM WorkflowDefinition d
                   WHERE d.isTemplate = true
                   ORDER BY d.createdAt DESC""",
                WorkflowDefinition::class.java,
            ).resultList
        }
    }
}

