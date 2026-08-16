package com.docuhyphen.app.api.repository.application

import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class AppRoleAssignmentRepositoryTest
{
    @Test
    fun `findActiveForUser applies the complete role effectiveness boundary`()
    {
        val appUserId = UUID.randomUUID()
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<AppRoleAssignment>>()
        val jpql = argumentCaptor<String>()
        whenever(
            entityManager.createQuery(jpql.capture(), eq(AppRoleAssignment::class.java)),
        ).thenReturn(query)
        whenever(query.setParameter("uid", appUserId)).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList())
        val repository = AppRoleAssignmentRepository().apply { this.entityManager = entityManager }

        repository.findActiveForUser(appUserId)

        assertEffectivenessPredicates(jpql.firstValue)
        assertTrue(jpql.firstValue.contains("r.appUserId = :uid"))
    }

    @Test
    fun `findActiveAppAdmins applies the complete role effectiveness boundary`()
    {
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<AppRoleAssignment>>()
        val jpql = argumentCaptor<String>()
        whenever(
            entityManager.createQuery(jpql.capture(), eq(AppRoleAssignment::class.java)),
        ).thenReturn(query)
        whenever(query.setParameter("role", AppRoleName.APP_ADMIN)).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList())
        val repository = AppRoleAssignmentRepository().apply { this.entityManager = entityManager }

        repository.findActiveAppAdmins()

        assertEffectivenessPredicates(jpql.firstValue)
        assertTrue(jpql.firstValue.contains("r.roleName = :role"))
    }

    @Test
    fun `countActiveAppAdmins applies the complete role effectiveness boundary`()
    {
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<Long>>()
        val jpql = argumentCaptor<String>()
        whenever(entityManager.createQuery(jpql.capture(), eq(Long::class.java))).thenReturn(query)
        whenever(query.setParameter("role", AppRoleName.APP_ADMIN)).thenReturn(query)
        whenever(query.singleResult).thenReturn(0)
        val repository = AppRoleAssignmentRepository().apply { this.entityManager = entityManager }

        repository.countActiveAppAdmins()

        assertEffectivenessPredicates(jpql.firstValue)
        assertTrue(jpql.firstValue.contains("r.roleName = :role"))
    }

    private fun assertEffectivenessPredicates(jpql: String)
    {
        assertTrue(jpql.contains("r.isActive = true"))
        assertTrue(jpql.contains("r.expiresAt IS NULL OR r.expiresAt > CURRENT_TIMESTAMP"))
        assertTrue(jpql.contains("u.isActive = true"))
        assertTrue(jpql.contains("u.deprovisionedAt IS NULL"))
        assertTrue(jpql.contains("u.id = r.appUserId"))
    }
}
