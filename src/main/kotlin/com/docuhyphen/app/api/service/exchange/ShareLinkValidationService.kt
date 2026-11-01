package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ShareLinkValidationService @Inject constructor(
    private val shareLinkRepository: ShareLinkRepository,
    private val shareRepository: ShareRepository,
)
{
    fun validateForNoAuth(
        rawToken: String,
        exchangeId: UUID,
        clientIp: String? = null,
        mfaSatisfied: Boolean = false,
    ): Share
    {
        val tokenHash = sha256Hex(rawToken)
        val now = Timestamp.from(Instant.now())

        val shareLink = shareLinkRepository.findByTokenHash(tokenHash)
            ?: throw ForbiddenException("Invalid or unknown share link")

        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            throw ForbiddenException("Share link has been revoked")
        }
        if (shareLink.status == ShareLinkStatus.EXPIRED ||
            (shareLink.expiresAt != null && !shareLink.expiresAt!!.after(now))
        )
        {
            throw ForbiddenException("Share link has expired")
        }

        val maxUses = shareLink.maxUses
        if (maxUses != null && shareLink.usedCount >= maxUses)
        {
            throw ForbiddenException("Share link usage limit reached")
        }

        if (shareLink.requireMfa && !mfaSatisfied)
        {
            throw ForbiddenException("Share link requires MFA verification")
        }

        val share = shareRepository.findById(shareLink.shareId)
            ?: throw ForbiddenException("Share link references a missing grant")

        if (share.status != ShareStatus.ACTIVE ||
            (share.expiresAt != null && !share.expiresAt!!.after(now))
        )
        {
            throw ForbiddenException("Share link grant is no longer active")
        }

        if (share.resourceType != ResourceType.EXCHANGE || share.resourceId != exchangeId)
        {
            throw ForbiddenException("Share link is not valid for this Exchange")
        }

        return share
    }

    private fun sha256Hex(raw: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
