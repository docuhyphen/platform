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
     * Every PENDING step instance. Caller filters by assignee in app code
     * (assignees are stored as a JSON snapshot, not an indexable column). Capped at 1000
     * since the per-user inbox is meant to be small.
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
     * All AWAITING_COUNTERPARTY step instances whose parent instance targets the given subject.
     * Used by the counterparty-clearance unblock sweep after any instance on the subject terminates.
     */
    fun findAwaitingCounterpartyForSubject(resourceType: String, resourceId: UUID): List<WorkflowStepInstance> =
        entityManager.createQuery(
            """SELECT s FROM WorkflowStepInstance s, WorkflowInstance i
               WHERE s.instanceId = i.id
                 AND s.status = :status
                 AND i.subjectResourceType = :rt
                 AND i.subjectResourceId = :rid""",
            WorkflowStepInstance::class.java,
        )
            .setParameter("status", WorkflowStepStatus.AWAITING_COUNTERPARTY)
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList
}

