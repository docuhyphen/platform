package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ShareLinkService @Inject constructor(
    private val shareLinkRepository: ShareLinkRepository,
) {
    fun findShareLink(shareLinkId: UUID): ShareLink? = shareLinkRepository.findById(shareLinkId)

    fun lockShareLinkForUpdate(shareLinkId: UUID): ShareLink? = shareLinkRepository.findByIdForUpdate(shareLinkId)

    fun findByTokenHash(tokenHash: String): ShareLink? = shareLinkRepository.findByTokenHash(tokenHash)

    fun bootstrapLinksForShares(shareIds: Collection<UUID>): List<ShareLink> =
        shareLinkRepository.findBootstrapLinksForShares(shareIds)

    fun activeBootstrapLinksForShare(shareId: UUID): List<ShareLink> =
        shareLinkRepository.findActiveBootstrapLinksForShare(shareId)

    fun create(shareLink: ShareLink): ShareLink = shareLinkRepository.save(shareLink)

    fun update(shareLink: ShareLink): ShareLink = shareLinkRepository.update(shareLink)
}
