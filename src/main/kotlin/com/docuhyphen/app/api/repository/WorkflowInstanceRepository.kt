package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowInstanceRepository :
    BaseRepository<WorkflowInstance>(WorkflowInstance::class.java)
{
    fun findRunningForSubject(resourceType: String, resourceId: UUID): WorkflowInstance? =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.status = :status""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("status", WorkflowInstanceStatus.RUNNING)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findRunningForOrg(organizationId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.organizationId = :oid AND i.status = :status
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("oid", organizationId)
            .setParameter("status", WorkflowInstanceStatus.RUNNING)
            .resultList
}

