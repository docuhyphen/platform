package com.docuhyphen.app.api.repository

import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import java.util.*

abstract class BaseRepository<T>(private val entityClass: Class<T>)
{
    @Inject
    lateinit var entityManager: EntityManager

    fun findById(id: UUID): T?
    {
        return entityManager.find(entityClass, id)
    }

    /**
     * Loads the entity and acquires a row-level pessimistic write lock (`SELECT ... FOR UPDATE`),
     * refreshing its state so it reflects the exact committed row that was locked. Callers that
     * must serialize competing transactions (concurrent decisions, SLA escalation, cancellation,
     * and advancement) lock the parent instance and the current step before deciding whether a
     * transition is still permitted, so only the first committer proceeds and later callers observe
     * the already-advanced state. Returns null when no row exists.
     */
    fun findByIdForUpdate(id: UUID): T?
    {
        val entity = entityManager.find(entityClass, id) ?: return null
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)
        return entity
    }

    @Transactional
    open fun save(entity: T): T
    {
        entityManager.persist(entity)
        return entity
    }

    @Transactional
    open fun update(entity: T): T
    {
        return entityManager.merge(entity)
    }

    @Transactional
    open fun deleteById(id: UUID)
    {
        val entity = findById(id)
        if (entity != null)
        {
            entityManager.remove(entity)
        }
    }

    @Transactional
    open fun delete(entity: T)
    {
        entityManager.remove(if (entityManager.contains(entity)) entity else entityManager.merge(entity))
    }

    fun findAll(): List<T>
    {
        val query = entityManager.createQuery("SELECT e FROM ${entityClass.simpleName} e", entityClass)
        return query.resultList
    }
}