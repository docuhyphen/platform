package com.docuhyphen.app.api.repository.contactdetails

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.UserContact
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import jakarta.transaction.Transactional
import java.util.*

@RequestScoped
class UserContactRepository : BaseRepository<UserContact>(UserContact::class.java)
{
    fun findByOwnerAndEmail(ownerAppUserId: UUID, contactEmail: String): UserContact?
    {
        val query: TypedQuery<UserContact> = entityManager.createQuery(
            """
                SELECT c FROM UserContact c
                WHERE c.ownerAppUserId = :ownerId
                  AND LOWER(c.contactEmail) = LOWER(:email)
            """.trimIndent(),
            UserContact::class.java
        )
        query.setParameter("ownerId", ownerAppUserId)
        query.setParameter("email", contactEmail)
        return query.resultList.firstOrNull()
    }

    fun searchByOwner(ownerAppUserId: UUID, query: String, limit: Int): List<UserContact>
    {
        val pattern = "%${query.lowercase()}%"
        val q: TypedQuery<UserContact> = entityManager.createQuery(
            """
                SELECT c FROM UserContact c
                WHERE c.ownerAppUserId = :ownerId
                  AND (
                    LOWER(c.contactEmail) LIKE :pattern
                    OR LOWER(COALESCE(c.contactFirstName, '')) LIKE :pattern
                    OR LOWER(COALESCE(c.contactLastName, '')) LIKE :pattern
                  )
                ORDER BY c.lastSharedAt DESC
            """.trimIndent(),
            UserContact::class.java
        )
        q.setParameter("ownerId", ownerAppUserId)
        q.setParameter("pattern", pattern)
        q.maxResults = limit
        return q.resultList
    }

    fun findRecentByOwner(ownerAppUserId: UUID, limit: Int): List<UserContact>
    {
        val q: TypedQuery<UserContact> = entityManager.createQuery(
            """
                SELECT c FROM UserContact c
                WHERE c.ownerAppUserId = :ownerId
                ORDER BY c.lastSharedAt DESC
            """.trimIndent(),
            UserContact::class.java
        )
        q.setParameter("ownerId", ownerAppUserId)
        q.maxResults = limit
        return q.resultList
    }

    /**
     * Used by the temp-user signup merge to backfill the contactAppUserId on rows that
     * previously only had an email (because the recipient was a temp AppUser).
     */
    @Transactional
    fun backfillContactAppUserId(contactEmail: String, newContactAppUserId: UUID): Int
    {
        val updated = entityManager.createQuery(
            """
                UPDATE UserContact c
                SET c.contactAppUserId = :newId
                WHERE LOWER(c.contactEmail) = LOWER(:email)
                  AND c.contactAppUserId IS NULL
            """.trimIndent()
        )
            .setParameter("newId", newContactAppUserId)
            .setParameter("email", contactEmail)
            .executeUpdate()
        return updated
    }

    /**
     * Returns true when both sides of the contact relationship exist:
     * [ownerAppUserId] lists [contactAppUserId] **and** vice-versa.
     */
    fun isMutualContact(ownerAppUserId: UUID, contactAppUserId: UUID): Boolean
    {
        val count = entityManager.createQuery(
            """
                SELECT COUNT(c) FROM UserContact c
                WHERE c.ownerAppUserId = :a AND c.contactAppUserId = :b
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("a", ownerAppUserId)
            .setParameter("b", contactAppUserId)
            .singleResult
            .toLong()
        if (count == 0L) return false

        val reverseCount = entityManager.createQuery(
            """
                SELECT COUNT(c) FROM UserContact c
                WHERE c.ownerAppUserId = :a AND c.contactAppUserId = :b
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("a", contactAppUserId)
            .setParameter("b", ownerAppUserId)
            .singleResult
            .toLong()
        return reverseCount > 0
    }
}
