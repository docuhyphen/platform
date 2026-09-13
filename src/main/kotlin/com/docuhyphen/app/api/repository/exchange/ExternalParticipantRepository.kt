package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ExternalParticipantRepository :
    BaseRepository<ExternalParticipant>(ExternalParticipant::class.java)
{
    /** Owner-scoped lookup. `ownerAppUserId` is used for personal participant ownership. */
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

    /** Legacy organization-only lookup kept for existing display callers. */
    fun findByOwnerAndEmail(ownerOrganizationId: UUID?, email: String): ExternalParticipant?
    {
        val lower = email.trim().lowercase()
        return if (ownerOrganizationId == null)
        {
            entityManager.createQuery(
                """SELECT p FROM ExternalParticipant p
                   WHERE p.ownerOrganizationId IS NULL
                     AND p.ownerAppUserId IS NULL
                     AND p.emailLower = :e""",
                ExternalParticipant::class.java,
            )
                .setParameter("e", lower)
                .resultList
                .firstOrNull()
        }
        else
        {
            entityManager.createQuery(
                """SELECT p FROM ExternalParticipant p
                   WHERE p.ownerOrganizationId = :oid
                     AND p.ownerAppUserId IS NULL
                     AND p.emailLower = :e""",
                ExternalParticipant::class.java,
            )
                .setParameter("oid", ownerOrganizationId)
                .setParameter("e", lower)
                .resultList
                .firstOrNull()
        }
    }
}

