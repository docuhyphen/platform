package com.docuhyphen.app.api.repository.communication

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.Communication
import com.docuhyphen.app.api.model.entity.CommunicationScope
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class CommunicationRepository :
    BaseRepository<Communication>(Communication::class.java)
{
    /**
     * Returns all communications the caller may see:
     * - PERSONAL communications owned by [callerUserId]
     * - ORG communications for [callerOrgId] (published-only unless [isOrgAdmin])
     * - PLATFORM-scoped communications (isTemplate=true, always visible to all)
     *
     * Org admins cannot see other users' PERSONAL communications.
     */
    fun findAllAccessibleForCaller(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
    ): List<Communication>
    {
        return if (callerOrgId != null)
        {
            entityManager.createQuery(
                """SELECT t FROM Communication t
                   WHERE t.isDeleted = false
                     AND (
                           (t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.PERSONAL
                            AND t.createdByAppUserId = :uid)
                        OR (t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.ORG
                            AND t.organizationId = :oid
                            AND (:showUnpublished = true OR t.isPublished = true))
                        OR (t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.PLATFORM
                            AND t.isTemplate = true)
                     )
                   ORDER BY t.updatedAt DESC""",
                Communication::class.java,
            )
                .setParameter("uid", callerUserId)
                .setParameter("oid", callerOrgId)
                .setParameter("showUnpublished", isOrgAdmin)
                .resultList
        }
        else
        {
            entityManager.createQuery(
                """SELECT t FROM Communication t
                   WHERE t.isDeleted = false
                     AND (
                           (t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.PERSONAL
                            AND t.createdByAppUserId = :uid)
                        OR (t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.PLATFORM
                            AND t.isTemplate = true)
                     )
                   ORDER BY t.updatedAt DESC""",
                Communication::class.java,
            )
                .setParameter("uid", callerUserId)
                .resultList
        }
    }

    fun findOrgCommunications(organizationId: UUID): List<Communication> =
        entityManager.createQuery(
            """SELECT t FROM Communication t
               WHERE t.isDeleted = false
                 AND t.scope = com.docuhyphen.app.api.model.entity.CommunicationScope.ORG
                 AND t.organizationId = :oid
               ORDER BY t.updatedAt DESC""",
            Communication::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList
}
