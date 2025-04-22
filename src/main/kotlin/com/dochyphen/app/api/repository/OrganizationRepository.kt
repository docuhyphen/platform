package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.Organization
import jakarta.enterprise.context.RequestScoped
import java.util.*

@RequestScoped
class OrganizationRepository : BaseRepository<Organization>(Organization::class.java)
{
    fun existsByRegistrationNumber(registrationNumber: String): Boolean
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(c) FROM Organization c WHERE LOWER(c.registrationNumber) = LOWER(:registrationNumber)",
            Long::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.singleResult > 0
    }

    fun findByRegistrationNumber(registrationNumber: String): Organization?
    {
        val query = entityManager.createQuery(
            "SELECT COUNT(c) FROM Organization c WHERE LOWER(c.registrationNumber) = LOWER(:registrationNumber)",
            Organization::class.java
        )
        query.setParameter("registrationNumber", registrationNumber)
        return query.resultList.firstOrNull()
    }

    fun findByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization?
    {
        val query = entityManager.createQuery(
            "SELECT c FROM Organization c JOIN c.appUsers u WHERE u.id = :appUserId AND u.person.id = :personId",
            Organization::class.java
        )
        query.setParameter("appUserId", appUserId)
        query.setParameter("personId", personId)
        return query.resultList.firstOrNull()
    }

    fun findByAppUserIdAndAppId(appUserId: UUID, appId: UUID): Organization
    {
        TODO("Not implemented")

//        val query = entityManager.createQuery(
//            "SELECT c FROM Organization c JOIN c.appUsers u WHERE u.id = :appUserId AND u.application.id = :appId",
//            Organization::class.java
//        )
//        query.setParameter("appUserId", appUserId)
//        query.setParameter("appId", appId)
//        return query.resultList.firstOrNull()
//            ?: throw IllegalArgumentException("Organization not found for appUserId: $appUserId and appId: $appId")
    }
}