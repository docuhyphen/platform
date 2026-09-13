package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class ShareLinkRepository : BaseRepository<ShareLink>(ShareLink::class.java)
{
    fun findByTokenHash(tokenHash: String): ShareLink? =
        entityManager.createQuery(
            "SELECT l FROM ShareLink l WHERE l.tokenHash = :h",
            ShareLink::class.java,
        )
            .setParameter("h", tokenHash)
            .resultList
            .firstOrNull()

    fun findByTokenHashForUpdate(tokenHash: String): ShareLink? =
        entityManager.createQuery(
            "SELECT l FROM ShareLink l WHERE l.tokenHash = :h",
            ShareLink::class.java,
        )
            .setParameter("h", tokenHash)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()

    /** Every bootstrap-mode ShareLink still bound to [shareId] that has not already been revoked. */
    fun findActiveBootstrapLinksForShare(shareId: UUID): List<ShareLink> =
        entityManager.createQuery(
            """
            SELECT l FROM ShareLink l
            WHERE l.shareId = :shareId
              AND l.linkMode = :mode
              AND l.status <> :revoked
            """.trimIndent(),
            ShareLink::class.java,
        )
            .setParameter("shareId", shareId)
            .setParameter("mode", ShareLinkMode.VERIFICATION_BOOTSTRAP)
            .setParameter("revoked", ShareLinkStatus.REVOKED)
            .resultList
}

