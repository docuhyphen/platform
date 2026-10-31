package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ExternalParticipantRepository :
    BaseRepository<ExternalParticipant>(ExternalParticipant::class.java)
{
    fun lockOwnerEmail(ownerOrganizationId: UUID?, ownerAppUserId: UUID?, email: String) {
        val owner = ownerOrganizationId?.let { "organization:$it" }
            ?: "user:${requireNotNull(ownerAppUserId)}"
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
            .setParameter("key", "external-participant:$owner:${email.trim().lowercase()}")
            .singleResult
    }

    fun findByOwnerAndEmail(
        ownerOrganizationId: UUID?,
        ownerAppUserId: UUID?,
        email: String,
    ): ExternalParticipant?
    {
        val lower = email.trim().lowercase()
        return when
        {
            ownerOrganizationId != null ->
                entityManager.createQuery(
                    """
                    SELECT p FROM ExternalParticipant p
                    WHERE p.ownerOrganizationId = :oid
                      AND p.ownerAppUserId IS NULL
                      AND p.emailLower = :email
                    """.trimIndent(),
                    ExternalParticipant::class.java,
                )
                    .setParameter("oid", ownerOrganizationId)
                    .setParameter("email", lower)
                    .resultList
                    .firstOrNull()

            ownerAppUserId != null ->
                entityManager.createQuery(
                    """
                    SELECT p FROM ExternalParticipant p
                    WHERE p.ownerOrganizationId IS NULL
                      AND p.ownerAppUserId = :uid
                      AND p.emailLower = :email
                    """.trimIndent(),
                    ExternalParticipant::class.java,
                )
                    .setParameter("uid", ownerAppUserId)
                    .setParameter("email", lower)
                    .resultList
                    .firstOrNull()

            else -> null
        }
    }

}
