package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.SubjectIdentityTransition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class SubjectIdentityTransitionRepository :
    BaseRepository<SubjectIdentityTransition>(SubjectIdentityTransition::class.java)
{
    fun findBySource(
        sourceId: UUID,
        ownerType: SubjectIdentityOwnerType,
        ownerId: UUID,
    ): SubjectIdentityTransition? =
        entityManager.createQuery(
            """
            SELECT transition
            FROM SubjectIdentityTransition transition
            WHERE transition.sourceSubjectIdentityId = :sourceId
              AND transition.ownerType = :ownerType
              AND transition.ownerId = :ownerId
            """.trimIndent(),
            SubjectIdentityTransition::class.java,
        )
            .setParameter("sourceId", sourceId)
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .resultList
            .firstOrNull()
}
