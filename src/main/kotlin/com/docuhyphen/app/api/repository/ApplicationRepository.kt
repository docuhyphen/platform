package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.Application
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ApplicationRepository : BaseRepository<Application>(Application::class.java)
{
    fun findByApiKey(apiKey: String): Application? =
        entityManager.createQuery(
            "SELECT a FROM Application a WHERE a.apiKey = :apiKey",
            Application::class.java,
        )
            .setParameter("apiKey", apiKey)
            .resultList
            .firstOrNull()

    fun findActiveById(id: UUID): Application? =
        entityManager.createQuery(
            "SELECT a FROM Application a WHERE a.id = :id AND a.isActive = true",
            Application::class.java,
        )
            .setParameter("id", id)
            .resultList
            .firstOrNull()

    fun findAllOrdered(): List<Application> =
        entityManager.createQuery("SELECT a FROM Application a ORDER BY a.createdDate DESC", Application::class.java)
            .resultList
}
