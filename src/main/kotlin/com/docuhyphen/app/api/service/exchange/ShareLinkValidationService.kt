package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
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
import java.util.UUID

/**
 * Validates [com.docuhyphen.app.api.model.entity.ShareLink] tokens for no-auth (unauthenticated)
 * Exchange access paths.
 *
 * The raw token is never stored; only its SHA-256 hex hash. This service hashes the
 * token presented by the caller and looks up the [com.docuhyphen.app.api.model.entity.ShareLink]
 * by that hash, then enforces all link and share constraints before returning the covering
 * [Share].
 *
 * This mirrors the same constraint checks performed by
 * [com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService] for authenticated
 * requests that present a share link token, ensuring equivalent enforcement on no-auth paths.
 */
@ApplicationScoped
class ShareLinkValidationService @Inject constructor(
    private val shareLinkRepository: ShareLinkRepository,
    private val shareRepository: ShareRepository,
)
{
    /**
     * Validates a raw share-link token against the given Exchange and returns the covering
     * [Share] if all checks pass.
     *
     * Throws [ForbiddenException] when:
     *  - The token hash does not match any [com.docuhyphen.app.api.model.entity.ShareLink].
     *  - The link is revoked, expired, or exhausted.
     *  - The link's associated [Share] is not ACTIVE, is expired, or does not cover [exchangeId].
     *
     * The [clientIp] and [mfaSatisfied] parameters are present for future IP-allowlist and
     * MFA constraint enforcement on public-link paths. MFA on a no-auth path is not currently
     * satisfiable; callers that set requireMfa on a ShareLink should not expose it on no-auth
     * endpoints.
     */
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
            throw ForbiddenException("Share link is not valid for this exchange")
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
