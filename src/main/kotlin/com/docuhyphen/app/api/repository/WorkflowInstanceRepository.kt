package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
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

    /**
     * Returns the first RUNNING [WorkflowInstance] for [subjectResourceId] whose backing
     * [WorkflowDefinition] fires on [triggerEvent]. Used to check whether an acceptance or
     * ending workflow is already in flight before allowing a direct status write.
     */
    fun findRunningForSubjectAndTrigger(subjectResourceId: UUID, triggerEvent: String): WorkflowInstance? =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i, WorkflowDefinition d
               WHERE d.id = i.definitionId
                 AND i.subjectResourceId = :rid
                 AND i.status = :status
                 AND d.triggerEvent = :te""",
            WorkflowInstance::class.java,
        )
            .setParameter("rid", subjectResourceId)
            .setParameter("status", WorkflowInstanceStatus.RUNNING)
            .setParameter("te", triggerEvent)
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

    /** All RUNNING instances referencing [definitionId]. Used to block edits or deletes. */
    fun findRunningForDefinition(definitionId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.definitionId = :did AND i.status = :status""",
            WorkflowInstance::class.java,
        )
            .setParameter("did", definitionId)
            .setParameter("status", WorkflowInstanceStatus.RUNNING)
            .resultList

    /** All instances (any status) for a given subject resource, newest first. */
    fun findForSubject(resourceType: String, resourceId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList

    /** All instances for a given subject resource owned by [organizationId], newest first. */
    fun findForSubject(resourceType: String, resourceId: UUID, organizationId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.organizationId = :oid
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("oid", organizationId)
            .resultList

    /**
     * Own-org instances for [organizationId] PLUS any RUNNING instances from other orgs where
     * [callerId] appears in an active PENDING step's `assigneesSnapshotJson`. This preserves
     * org sovereignty while still surfacing cross-org acceptance workflows where the calling
     * user is the designated approver (e.g. exchange.acceptance_pending using $subject.recipientId).
     */
    fun findForSubjectIncludingCrossOrgPendingAssignee(
        resourceType: String,
        resourceId: UUID,
        organizationId: UUID,
        callerId: UUID,
    ): List<WorkflowInstance>
    {
        val ownOrg = findForSubject(resourceType, resourceId, organizationId)

        val crossOrg = entityManager.createQuery(
            """SELECT DISTINCT i FROM WorkflowInstance i, WorkflowStepInstance s
               WHERE s.instanceId = i.id
                 AND i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.organizationId <> :oid
                 AND i.status = :running
                 AND s.status = :pending
                 AND s.assigneesSnapshotJson LIKE :pattern""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("oid", organizationId)
            .setParameter("running", WorkflowInstanceStatus.RUNNING)
            .setParameter("pending", WorkflowStepStatus.PENDING)
            .setParameter("pattern", "%\"id\":\"${callerId}\"%")
            .resultList

        val seen = mutableSetOf<UUID>()
        val merged = mutableListOf<WorkflowInstance>()
        for (i in ownOrg) { if (seen.add(i.id)) merged.add(i) }
        for (i in crossOrg) { if (seen.add(i.id)) merged.add(i) }
        return merged
    }

    /** All instances for a given subject resource NOT owned by [excludeOrgId], newest first. Used for counterparty clearance checks. */
    fun findForSubjectExcludingOrg(resourceType: String, resourceId: UUID, excludeOrgId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.organizationId <> :oid
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("oid", excludeOrgId)
            .resultList

    /**
     * Paginated instances for [organizationId]. Optionally filtered by [status] and
     * [subjectResourceType]. Results are ordered newest first.
     */
    fun findForOrg(
        organizationId: UUID,
        status: WorkflowInstanceStatus?,
        subjectResourceType: String?,
        page: Int,
        pageSize: Int,
    ): List<WorkflowInstance>
    {
        val jpql = buildString {
            append("SELECT i FROM WorkflowInstance i WHERE i.organizationId = :oid")
            if (status != null) append(" AND i.status = :status")
            if (subjectResourceType != null) append(" AND i.subjectResourceType = :srt")
            append(" ORDER BY i.createdAt DESC")
        }
        val query = entityManager.createQuery(jpql, WorkflowInstance::class.java)
            .setParameter("oid", organizationId)
            .setFirstResult(page * pageSize)
            .setMaxResults(pageSize)
        if (status != null) query.setParameter("status", status)
        if (subjectResourceType != null) query.setParameter("srt", subjectResourceType)
        return query.resultList
    }
}

