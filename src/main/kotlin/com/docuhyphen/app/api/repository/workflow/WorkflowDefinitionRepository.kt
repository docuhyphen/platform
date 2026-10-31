package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class WorkflowDefinitionRepository :
    BaseRepository<WorkflowDefinition>(WorkflowDefinition::class.java)
{
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

    fun findAllActiveForPersonalTrigger(triggerEvent: String, ownerAppUserId: UUID): List<WorkflowDefinition> {
        val personal = entityManager.createQuery(
            """SELECT d FROM WorkflowDefinition d
               WHERE d.triggerEvent = :ev
                 AND d.isActive = true
                 AND d.isDeleted = false
                 AND d.scope = com.docuhyphen.app.api.model.entity.WorkflowScope.PERSONAL
                 AND d.createdByAppUserId = :uid
               ORDER BY d.version DESC""",
            WorkflowDefinition::class.java,
        )
            .setParameter("ev", triggerEvent)
            .setParameter("uid", ownerAppUserId)
            .resultList
        return personal.ifEmpty { findAllActiveForTrigger(triggerEvent, null) }
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

