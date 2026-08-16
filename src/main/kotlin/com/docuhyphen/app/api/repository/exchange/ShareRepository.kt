package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ShareRepository : BaseRepository<Share>(Share::class.java)
{
    fun findActiveByResource(resourceType: ResourceType, resourceId: UUID): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.resourceType = :rt AND s.resourceId = :rid AND s.status = :status""",
            Share::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("status", ShareStatus.ACTIVE)
            .resultList

    fun findByResourceAndStatus(
        resourceType: ResourceType,
        resourceId: UUID,
        status: ShareStatus,
    ): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.resourceType = :rt AND s.resourceId = :rid AND s.status = :status""",
            Share::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("status", status)
            .resultList

    fun findAllByResource(resourceType: ResourceType, resourceId: UUID): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.resourceType = :rt AND s.resourceId = :rid
               ORDER BY s.grantedAt DESC""",
            Share::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList

    fun findActiveForPrincipal(kind: PrincipalKind, principalId: UUID): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.principalKind = :pk AND s.principalId = :pid AND s.status = :status""",
            Share::class.java,
        )
            .setParameter("pk", kind)
            .setParameter("pid", principalId)
            .setParameter("status", ShareStatus.ACTIVE)
            .resultList

    fun findActiveDirectByResourceOrdered(
        resourceType: ResourceType,
        resourceId: UUID,
    ): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.resourceType = :rt AND s.resourceId = :rid
                 AND s.status = :status AND s.source = :source
                 AND s.sourceShareId IS NULL
               ORDER BY s.grantedAt ASC, s.id ASC""",
            Share::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("status", ShareStatus.ACTIVE)
            .setParameter("source", ShareSource.DIRECT)
            .resultList

    fun findAllForPrincipal(kind: PrincipalKind, principalId: UUID): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.principalKind = :pk AND s.principalId = :pid""",
            Share::class.java,
        )
            .setParameter("pk", kind)
            .setParameter("pid", principalId)
            .resultList

    fun findActiveForPrincipalOnResource(
        kind: PrincipalKind,
        principalId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.principalKind = :pk AND s.principalId = :pid
                 AND s.resourceType = :rt AND s.resourceId = :rid
                 AND s.status = :status""",
            Share::class.java,
        )
            .setParameter("pk", kind)
            .setParameter("pid", principalId)
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("status", ShareStatus.ACTIVE)
            .resultList

    fun findDirectForPrincipalOnResource(
        kind: PrincipalKind,
        principalId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
    ): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s
               WHERE s.principalKind = :pk AND s.principalId = :pid
                 AND s.resourceType = :rt AND s.resourceId = :rid
                 AND s.source = :source AND s.sourceShareId IS NULL
               ORDER BY s.grantedAt DESC""",
            Share::class.java,
        )
            .setParameter("pk", kind)
            .setParameter("pid", principalId)
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .setParameter("source", ShareSource.DIRECT)
            .resultList

    /** Inherited (materialised) shares that point back to a parent group/org share. */
    fun findBySourceShareId(sourceShareId: UUID): List<Share> =
        entityManager.createQuery(
            """SELECT s FROM Share s WHERE s.sourceShareId = :sid""",
            Share::class.java,
        )
            .setParameter("sid", sourceShareId)
            .resultList
}

