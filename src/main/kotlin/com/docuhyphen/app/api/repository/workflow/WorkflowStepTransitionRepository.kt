package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.WorkflowStepTransition
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowStepTransitionRepository :
    BaseRepository<WorkflowStepTransition>(WorkflowStepTransition::class.java)
{
    /** All traversed edges for an instance, oldest first (chronological execution order). */
    fun findByInstanceId(instanceId: UUID): List<WorkflowStepTransition> =
        entityManager.createQuery(
            """SELECT t FROM WorkflowStepTransition t
               WHERE t.instanceId = :iid
               ORDER BY t.recordedAt ASC""",
            WorkflowStepTransition::class.java,
        )
            .setParameter("iid", instanceId)
            .resultList

    /** Whether the given source step instance has already recorded its single outgoing edge. */
    fun existsByFromStepInstanceId(fromStepInstanceId: UUID): Boolean =
        entityManager.createQuery(
            """SELECT COUNT(t) FROM WorkflowStepTransition t
               WHERE t.fromStepInstanceId = :sid""",
            java.lang.Long::class.java,
        )
            .setParameter("sid", fromStepInstanceId)
            .singleResult
            .toLong() > 0

    /** Whether the instance's single START edge has already been recorded. */
    fun existsStartByInstanceId(instanceId: UUID): Boolean =
        entityManager.createQuery(
            """SELECT COUNT(t) FROM WorkflowStepTransition t
               WHERE t.instanceId = :iid AND t.fromStepInstanceId IS NULL""",
            java.lang.Long::class.java,
        )
            .setParameter("iid", instanceId)
            .singleResult
            .toLong() > 0
}
