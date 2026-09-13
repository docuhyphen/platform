package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.SubjectIdentityRef
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

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
}
