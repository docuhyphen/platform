package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowInstanceRepository :
    BaseRepository<WorkflowInstance>(WorkflowInstance::class.java)
{
    fun findActiveForSubject(resourceType: String, resourceId: UUID): WorkflowInstance? =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.status IN :statuses""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("statuses", WorkflowInstanceStatus.ACTIVE)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findAllActiveForSubject(resourceType: String, resourceId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.status IN :statuses
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("statuses", WorkflowInstanceStatus.ACTIVE)
            .resultList

    /**
     * Returns the first active (RUNNING or ESCALATED) [WorkflowInstance] for [subjectResourceId]
     * whose frozen trigger is [triggerEvent]. The frozen value remains valid when the backing
     * definition is edited or deleted while an instance is running.
     */
    fun findActiveForSubjectAndTrigger(subjectResourceId: UUID, triggerEvent: String): WorkflowInstance? =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.subjectResourceId = :rid
                 AND i.status IN :statuses
                 AND i.triggerEventSnapshot = :te
               ORDER BY i.createdAt ASC""",
            WorkflowInstance::class.java,
        )
            .setParameter("rid", subjectResourceId)
            .setParameter("statuses", WorkflowInstanceStatus.ACTIVE)
            .setParameter("te", triggerEvent)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findActiveForOrg(organizationId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.organizationId = :oid AND i.status IN :statuses
               ORDER BY i.createdAt DESC""",
            WorkflowInstance::class.java,
        )
            .setParameter("oid", organizationId)
            .setParameter("statuses", WorkflowInstanceStatus.ACTIVE)
            .resultList

    /** All active (RUNNING or ESCALATED) instances referencing [definitionId]. Used to block edits or deletes. */
    fun findActiveForDefinition(definitionId: UUID): List<WorkflowInstance> =
        entityManager.createQuery(
            """SELECT i FROM WorkflowInstance i
               WHERE i.definitionId = :did AND i.status IN :statuses""",
            WorkflowInstance::class.java,
        )
            .setParameter("did", definitionId)
            .setParameter("statuses", WorkflowInstanceStatus.ACTIVE)
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
     * Own-org instances for [organizationId] PLUS any active (RUNNING or ESCALATED) instances from
     * other orgs where [callerId] is an assignee on an active PENDING step (via the indexed
     * workflow_step_assignee table). This preserves org sovereignty while still surfacing cross-org
     * acceptance workflows where the calling user is the designated approver (e.g.
     * exchange.acceptance_pending using $subject.recipientId), including escalated pending ones.
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
            """SELECT DISTINCT i FROM WorkflowInstance i, WorkflowStepInstance s, WorkflowStepAssignee a
               WHERE s.instanceId = i.id
                 AND a.stepInstanceId = s.id
                 AND i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
                 AND i.organizationId <> :oid
                 AND i.status IN :activeStatuses
                 AND s.status = :pending
                 AND a.principalKind = :userKind
                 AND a.principalId = :callerId""",
            WorkflowInstance::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("oid", organizationId)
            .setParameter("activeStatuses", WorkflowInstanceStatus.ACTIVE)
            .setParameter("pending", WorkflowStepStatus.PENDING)
            .setParameter("userKind", com.docuhyphen.app.api.model.entity.PrincipalKind.USER)
            .setParameter("callerId", callerId)
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
               ORDER BY i.id ASC""",
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

