package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.AuthenticatorEnrollment
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class AuthenticatorEnrollmentRepository : BaseRepository<AuthenticatorEnrollment>(AuthenticatorEnrollment::class.java)
{
    fun findForUser(enrollmentId: UUID, appUserId: UUID): AuthenticatorEnrollment?
    {
        val query = entityManager.createQuery(
            "SELECT e FROM AuthenticatorEnrollment e WHERE e.id = :id AND e.appUser.id = :appUserId",
            AuthenticatorEnrollment::class.java,
        )
        query.setParameter("id", enrollmentId)
        query.setParameter("appUserId", appUserId)
        return query.resultList.firstOrNull()
    }

    fun deleteForUser(appUserId: UUID): Int = entityManager.createQuery(
        "DELETE FROM AuthenticatorEnrollment e WHERE e.appUser.id = :appUserId",
    ).setParameter("appUserId", appUserId).executeUpdate()
}
