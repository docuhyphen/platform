package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.SubjectIdentityExternalIdentifier
import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class SubjectIdentityExternalIdentifierRepository :
    BaseRepository<SubjectIdentityExternalIdentifier>(SubjectIdentityExternalIdentifier::class.java)
{
    fun findByTenantValue(
        ownerType: SubjectIdentityOwnerType,
        ownerId: UUID,
        authority: String,
        identifierType: String,
        identifierValue: String,
    ): SubjectIdentityExternalIdentifier? =
        entityManager.createQuery(
            """
            SELECT identifier
            FROM SubjectIdentityExternalIdentifier identifier
            WHERE identifier.ownerType = :ownerType
              AND identifier.ownerId = :ownerId
              AND identifier.authority = :authority
              AND identifier.identifierType = :identifierType
              AND identifier.identifierValue = :identifierValue
            """.trimIndent(),
            SubjectIdentityExternalIdentifier::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setParameter("authority", authority)
            .setParameter("identifierType", identifierType)
            .setParameter("identifierValue", identifierValue)
            .resultList
            .firstOrNull()
}
