package com.dochyphen.app.api.repository

import jakarta.inject.Inject
import jakarta.persistence.EntityManager
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