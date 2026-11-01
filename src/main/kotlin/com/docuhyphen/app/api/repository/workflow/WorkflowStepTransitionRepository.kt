package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.model.entity.WorkflowStepTransition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class WorkflowStepTransitionRepository :
    BaseRepository<WorkflowStepTransition>(WorkflowStepTransition::class.java)
{
    fun findByInstanceId(instanceId: UUID): List<WorkflowStepTransition> =
        entityManager.createQuery(
            """SELECT t FROM WorkflowStepTransition t
               WHERE t.instanceId = :iid
               ORDER BY t.recordedAt ASC""",
            WorkflowStepTransition::class.java,
        )
            .setParameter("iid", instanceId)
            .resultList

    fun existsByFromStepInstanceId(fromStepInstanceId: UUID): Boolean =
        entityManager.createQuery(
            """SELECT COUNT(t) FROM WorkflowStepTransition t
               WHERE t.fromStepInstanceId = :sid""",
            java.lang.Long::class.java,
        )
            .setParameter("sid", fromStepInstanceId)
            .singleResult
            .toLong() > 0

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
