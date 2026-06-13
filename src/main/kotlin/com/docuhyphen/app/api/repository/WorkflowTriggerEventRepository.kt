package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager

/**
 * Read-only repository for the `workflow_trigger_event_registry` table.
 * Rows are seeded by Flyway migrations and are not written at runtime.
 * Cannot extend [BaseRepository] because the primary key is a String, not UUID.
 */
@ApplicationScoped
class WorkflowTriggerEventRepository
{
    @Inject
    lateinit var entityManager: EntityManager

    /** All active trigger events, sorted alphabetically by event name. */
    fun findAll(): List<WorkflowTriggerEventRegistry> =
        entityManager.createQuery(
            "SELECT t FROM WorkflowTriggerEventRegistry t WHERE t.isActive = true ORDER BY t.eventName ASC",
            WorkflowTriggerEventRegistry::class.java,
        ).resultList

    fun findByEventName(eventName: String): WorkflowTriggerEventRegistry? =
        entityManager.find(WorkflowTriggerEventRegistry::class.java, eventName)
}

