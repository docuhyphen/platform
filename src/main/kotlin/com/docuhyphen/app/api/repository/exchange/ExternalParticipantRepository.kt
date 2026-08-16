package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.ExternalParticipant
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ExternalParticipantRepository :
    BaseRepository<ExternalParticipant>(ExternalParticipant::class.java)
{
    /** Owner-scoped lookup. `ownerOrganizationId = null` queries the "personal" partition. */
    fun findByOwnerAndEmail(ownerOrganizationId: UUID?, email: String): ExternalParticipant?
    {
        val lower = email.lowercase()
        return if (ownerOrganizationId == null)
        {
            entityManager.createQuery(
                """SELECT p FROM ExternalParticipant p
                   WHERE p.ownerOrganizationId IS NULL AND p.emailLower = :e""",
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
                   WHERE p.ownerOrganizationId = :oid AND p.emailLower = :e""",
                ExternalParticipant::class.java,
            )
                .setParameter("oid", ownerOrganizationId)
                .setParameter("e", lower)
                .resultList
                .firstOrNull()
        }
    }
}

