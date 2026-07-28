package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.Organization
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlatformOrganizationRepositoryTest
{
    @Test
    fun `platform organization query is allowlisted filtered and stably sorted`()
    {
        val entityManager = mock<EntityManager>()
        val query = mock<TypedQuery<Organization>>()
        val jpql = argumentCaptor<String>()
        whenever(entityManager.createQuery(jpql.capture(), eq(Organization::class.java))).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(query.resultList).thenReturn(emptyList())
        val repository = OrganizationRepository().apply { this.entityManager = entityManager }

        repository.findForPlatformAdministration(
            normalizedQuery = "acme",
            active = true,
            tierCode = "BUSINESS",
            sort = "name",
            direction = "asc",
            limit = 25,
            offset = 50,
        )

        assertTrue(jpql.firstValue.contains("LOWER(o.name) LIKE :queryPattern"))
        assertTrue(jpql.firstValue.contains("LOWER(o.registrationNumber) LIKE :queryPattern"))
        assertTrue(jpql.firstValue.contains("COALESCE(p.tierCode, 'FREE') = :tierCode"))
        assertTrue(jpql.firstValue.contains("ORDER BY LOWER(o.name) ASC, o.id ASC"))
        assertFalse(jpql.firstValue.contains("contactDetails"))
        assertFalse(jpql.firstValue.contains("settings"))
        verify(query).firstResult = 50
        verify(query).maxResults = 25
    }
}
