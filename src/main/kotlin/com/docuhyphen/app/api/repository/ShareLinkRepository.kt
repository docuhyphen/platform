package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.ShareLink
import jakarta.enterprise.context.ApplicationScoped

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
}

