package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ExternalIdentityResolutionRepository :
    BaseRepository<ExternalIdentityResolution>(ExternalIdentityResolution::class.java)
{
    fun findForUpdate(id: UUID): ExternalIdentityResolution? = findByIdForUpdate(id)

    fun findExpiredBefore(cutoff: Instant): List<ExternalIdentityResolution> =
        entityManager.createQuery(
            """SELECT r FROM ExternalIdentityResolution r
               WHERE r.expiresAt < :cutoff""",
            ExternalIdentityResolution::class.java,
        )
            .setParameter("cutoff", java.sql.Timestamp.from(cutoff))
            .resultList

    @Transactional
    fun deleteAll(resolutions: List<ExternalIdentityResolution>)
    {
        resolutions.forEach { resolution ->
            entityManager.remove(if (entityManager.contains(resolution)) resolution else entityManager.merge(resolution))
        }
    }
}
