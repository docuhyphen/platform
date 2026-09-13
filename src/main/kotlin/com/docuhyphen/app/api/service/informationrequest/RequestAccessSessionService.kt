package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.informationrequest.IssuedRequestAccessSession
import com.docuhyphen.app.api.repository.informationrequest.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@ApplicationScoped
class RequestAccessSessionService @Inject constructor(
    private val sessionRepository: RequestAccessSessionRepository,
)
{
    @Transactional
    fun issue(
        shareLink: ShareLink,
        participant: PrincipalRef,
        verificationStrength: RequestAccessSessionVerificationStrength,
        expiresAt: Timestamp?,
    ): IssuedRequestAccessSession
    {
        require(shareLink.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP) {
            "A request access session may only be issued against a VERIFICATION_BOOTSTRAP ShareLink"
        }
        val parent = sessionRepository.lockParentForShare(shareLink.shareId)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange not found")
        if (InformationRequestTransitionMatrix.canRead(parent, InformationRequestReadActor.EXTERNAL_SESSION)
            is InformationRequestPolicyDecision.Deny)
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange does not permit respondent sessions")
        val now = Timestamp.from(Instant.now())
        val expiry = listOfNotNull(expiresAt, shareLink.expiresAt, Timestamp.from(now.toInstant().plusSeconds(86400)))
            .minOrNull()!!
        require(expiry.after(now)) { "Session expiry must be in the future" }
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        val session = RequestAccessSession().apply {
            shareLinkId = shareLink.id
            participantPrincipalKind = participant.kind
            participantPrincipalId = participant.id
            this.verificationStrength = verificationStrength
            issuedAt = now
            this.expiresAt = expiry
            credentialHash = hash(secret)
            createdAt = now
        }
        val saved = sessionRepository.save(session)
        return IssuedRequestAccessSession(saved, "${saved.id}.$secret")
    }

    fun authenticate(sessionToken: String?): RequestAccessSession = requireActive(requireCredential(sessionToken).id)

    private fun requireCredential(sessionToken: String?): RequestAccessSession
    {
        val parts = sessionToken?.split('.', limit = 2)
        val id = parts?.firstOrNull()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val secret = parts?.getOrNull(1)
        if (id == null || secret == null || !secret.matches(Regex("[A-Za-z0-9_-]{43}"))) refuseCredential()
        val session = sessionRepository.findSessionByIdForUpdate(id) ?: refuseCredential()
        val expected = session.credentialHash ?: refuseCredential()
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), hash(secret).toByteArray(Charsets.UTF_8)))
            refuseCredential()
        return session
    }

    private fun refuseCredential(): Nothing = throw InformationRequestLifecycleException(
        InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
        "A verified request session credential is required",
    )

    private fun hash(secret: String): String = MessageDigest.getInstance("SHA-256")
        .digest(secret.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun requireActive(sessionId: UUID): RequestAccessSession
    {
        val session = sessionRepository.findSessionByIdForUpdate(sessionId)
            ?: throw IllegalArgumentException("Request access session not found")
        if (session.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
                "This request access session has been revoked",
            )
        }
        val expiresAt = session.expiresAt
        if (expiresAt == null || !expiresAt.after(Timestamp.from(Instant.now())))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED,
                "This request access session has expired",
            )
        }
        return session
    }


    @Transactional
    fun revoke(sessionId: UUID): RequestAccessSession
    {
        val session = sessionRepository.findSessionByIdForUpdate(sessionId)
            ?: throw IllegalArgumentException("Request access session not found")
        if (session.revokedAt == null)
        {
            session.revokedAt = Timestamp.from(Instant.now())
            return sessionRepository.update(session)
        }
        return session
    }

    @Transactional
    fun revokeAllForShareLink(shareLinkId: UUID): List<RequestAccessSession>
    {
        val now = Timestamp.from(Instant.now())
        return sessionRepository.findActiveByShareLinkId(shareLinkId).map { session ->
            session.revokedAt = now
            sessionRepository.update(session)
        }
    }

    @Transactional
    fun touchUse(sessionId: UUID): RequestAccessSession
    {
        val session = sessionRepository.findSessionByIdForUpdate(sessionId)
            ?: throw IllegalArgumentException("Request access session not found")
        session.useCount += 1
        session.lastUsedAt = Timestamp.from(Instant.now())
        return sessionRepository.update(session)
    }

    @Transactional
    fun revokeAllForRequest(requestId: UUID)
    {
        sessionRepository.findActiveForRequest(requestId).forEach { revoke(it.id) }
    }
}
