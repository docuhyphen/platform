package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowStepAssignee
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class WorkflowStepAssigneeRepository :
    BaseRepository<WorkflowStepAssignee>(WorkflowStepAssignee::class.java)
{
    fun findAllByStepInstanceId(stepInstanceId: UUID): List<WorkflowStepAssignee> =
        entityManager.createQuery(
            """SELECT a FROM WorkflowStepAssignee a
               WHERE a.stepInstanceId = :sid""",
            WorkflowStepAssignee::class.java,
        )
            .setParameter("sid", stepInstanceId)
            .resultList

    @Transactional
    fun deleteAllByStepInstanceId(stepInstanceId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM WorkflowStepAssignee a WHERE a.stepInstanceId = :sid"
        )
            .setParameter("sid", stepInstanceId)
            .executeUpdate()
}
