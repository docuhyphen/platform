package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class WorkflowStepInstanceRepository :
    BaseRepository<WorkflowStepInstance>(WorkflowStepInstance::class.java)
{
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

