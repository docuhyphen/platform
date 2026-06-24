package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowStepDecision
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowStepDecisionRepository :
    BaseRepository<WorkflowStepDecision>(WorkflowStepDecision::class.java)
{
    fun findAllByStepInstanceId(stepInstanceId: UUID): List<WorkflowStepDecision> =
        entityManager.createQuery(
            """SELECT d FROM WorkflowStepDecision d
               WHERE d.stepInstanceId = :sid
               ORDER BY d.decidedAt ASC""",
            WorkflowStepDecision::class.java,
        )
            .setParameter("sid", stepInstanceId)
            .resultList

    fun findByStepAndPrincipal(
        stepInstanceId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
    ): WorkflowStepDecision? =
        entityManager.createQuery(
            """SELECT d FROM WorkflowStepDecision d
               WHERE d.stepInstanceId = :sid
                 AND d.principalKind = :kind
                 AND d.principalId = :pid""",
            WorkflowStepDecision::class.java,
        )
            .setParameter("sid", stepInstanceId)
            .setParameter("kind", principalKind)
            .setParameter("pid", principalId)
            .resultList
            .firstOrNull()

    fun countByStepInstanceId(stepInstanceId: UUID): Long =
        entityManager.createQuery(
            """SELECT COUNT(d) FROM WorkflowStepDecision d
               WHERE d.stepInstanceId = :sid""",
            java.lang.Long::class.java,
        )
            .setParameter("sid", stepInstanceId)
            .singleResult
            .toLong()
}
