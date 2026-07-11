package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.UUID

@ApplicationScoped
class WorkflowStepInstanceRepository :
    BaseRepository<WorkflowStepInstance>(WorkflowStepInstance::class.java)
{
    /**
     * Returns just the parent instance id for a step, without loading the managed step entity.
     * Used by decision recording to discover which instance to lock first, preserving a consistent
     * instance-before-step lock order that avoids deadlocks with escalation and cancellation.
     */
    fun findInstanceIdById(stepInstanceId: UUID): UUID? =
        entityManager.createQuery(
            "SELECT s.instanceId FROM WorkflowStepInstance s WHERE s.id = :id",
            UUID::class.java,
        )
            .setParameter("id", stepInstanceId)
            .resultList
            .firstOrNull()

    fun findByInstance(instanceId: UUID): List<WorkflowStepInstance> =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s
               WHERE s.instanceId = :iid
               ORDER BY s.stepIndex ASC""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("iid", instanceId)
            .resultList

    fun findPendingDueBefore(cutoff: Timestamp): List<WorkflowStepInstance> =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s
               WHERE s.status = :status AND s.dueAt IS NOT NULL AND s.dueAt < :cutoff
               ORDER BY s.dueAt ASC""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("status", WorkflowStepStatus.PENDING)
            .setParameter("cutoff", cutoff)
            .setMaxResults(500)
            .resultList

    fun findCurrent(instanceId: UUID, stepIndex: Int): WorkflowStepInstance? =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s
               WHERE s.instanceId = :iid AND s.stepIndex = :idx""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("iid", instanceId)
            .setParameter("idx", stepIndex)
            .resultList
            .firstOrNull()

    /**
     * Every PENDING step instance. Used by the scheduler for addon (reminder) processing,
     * which must visit all pending steps. Capped at 1000.
     */
    fun findAllPending(): List<WorkflowStepInstance> =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s
               WHERE s.status = :status
               ORDER BY s.createdAt DESC""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("status", WorkflowStepStatus.PENDING)
            .setMaxResults(1000)
            .resultList

    /**
     * PENDING step instances on which [userId] can act: either a direct USER assignee or a
     * member of an assignee PRINCIPAL_GROUP (passed in as [groupIds]). Backed by the indexed
     * workflow_step_assignee table, replacing the former scan-all-pending + in-memory filter.
     */
    fun findPendingForAssignee(userId: UUID, groupIds: Collection<UUID>): List<WorkflowStepInstance>
    {
        val groupClause =
            if (groupIds.isEmpty()) ""
            else " OR (a.principalKind = :groupKind AND a.principalId IN :gids)"

        val query = entityManager.createQuery(
            """SELECT DISTINCT s FROM WorkflowStepInstance s, WorkflowStepAssignee a
               WHERE s.status = :status
                 AND a.stepInstanceId = s.id
                 AND ( (a.principalKind = :userKind AND a.principalId = :uid)$groupClause )
               ORDER BY s.createdAt DESC""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("status", WorkflowStepStatus.PENDING)
            .setParameter("userKind", com.docuhyphen.app.api.model.entity.PrincipalKind.USER)
            .setParameter("uid", userId)
        if (groupIds.isNotEmpty())
        {
            query.setParameter("groupKind", com.docuhyphen.app.api.model.entity.PrincipalKind.PRINCIPAL_GROUP)
            query.setParameter("gids", groupIds)
        }
        return query.setMaxResults(1000).resultList
    }

    /**
     * All AWAITING_COUNTERPARTY step instances whose parent instance targets the given subject.
     * Used by the counterparty-clearance unblock sweep after any instance on the subject terminates.
     */
    fun findAwaitingCounterpartyForSubject(resourceType: String, resourceId: UUID): List<WorkflowStepInstance> =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s, WorkflowInstance i
               WHERE s.instanceId = i.id
                 AND s.status = :status
                 AND i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid
               ORDER BY i.id ASC""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("status", WorkflowStepStatus.AWAITING_COUNTERPARTY)
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList
}

