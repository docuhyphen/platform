package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.SubjectIdentityRef
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SubjectIdentityRefRepository : BaseRepository<SubjectIdentityRef>(SubjectIdentityRef::class.java)
{
    fun findOwned(id: UUID, ownerType: SubjectIdentityOwnerType, ownerId: UUID): SubjectIdentityRef? =
        entityManager.createQuery(
            """
            SELECT subject
            FROM SubjectIdentityRef subject
            WHERE subject.id = :id
              AND subject.ownerType = :ownerType
              AND subject.ownerId = :ownerId
            """.trimIndent(),
            SubjectIdentityRef::class.java,
        )
            .setParameter("id", id)
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .resultList
            .firstOrNull()

    fun findForOwner(ownerType: SubjectIdentityOwnerType, ownerId: UUID): List<SubjectIdentityRef> =
        entityManager.createQuery(
            """
            SELECT subject
            FROM SubjectIdentityRef subject
            WHERE subject.ownerType = :ownerType
              AND subject.ownerId = :ownerId
            ORDER BY subject.createdAt DESC, subject.id
            """.trimIndent(),
            SubjectIdentityRef::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setMaxResults(OWNER_LIST_LIMIT)
            .resultList

    private companion object
    {
        const val OWNER_LIST_LIMIT = 500
    }
}
