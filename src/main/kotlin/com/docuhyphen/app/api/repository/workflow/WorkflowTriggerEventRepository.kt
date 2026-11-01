package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager

@ApplicationScoped
class WorkflowTriggerEventRepository
{
    @Inject
    lateinit var entityManager: EntityManager

    fun findAll(): List<WorkflowTriggerEventRegistry> =
        entityManager.createQuery(
            "SELECT t FROM WorkflowTriggerEventRegistry t WHERE t.isActive = true ORDER BY t.eventName ASC",
            WorkflowTriggerEventRegistry::class.java,
        ).resultList

    fun findByEventName(eventName: String): WorkflowTriggerEventRegistry? =
        entityManager.find(WorkflowTriggerEventRegistry::class.java, eventName)
}

