package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.noauth.RequestAccessSessionBinding
import com.docuhyphen.app.api.repository.informationrequest.noauth.RequestAccessSessionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
        if (participant.kind != PrincipalKind.PARTICIPANT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
                "A request access session is only issued to a participant identified by contact proof",
            )
        }
        val parent = sessionRepository.lockParentForShare(shareLink.shareId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange not found"
            )
        requireCredentialCommandAllowed(
            parent,
            sessionRepository.requestStateForShare(shareLink.shareId),
            InformationRequestMutation.ISSUE_ACCESS_SESSION,
        )
        requireNoAuthPermitted(shareLink.id)
        val now = Timestamp.from(Instant.now())
        val expiry = listOfNotNull(expiresAt, shareLink.expiresAt, Timestamp.from(now.toInstant().plusSeconds(86400)))
            .minOrNull()!!
        require(expiry.after(now)) { "Session expiry must be in the future" }
        val secret =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
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

    @Transactional(Transactional.TxType.MANDATORY)
    fun authenticate(sessionToken: String?): RequestAccessSession = requireActive(requireCredential(sessionToken).id)

    @Transactional(Transactional.TxType.MANDATORY)
    fun verifyCredential(sessionToken: String?): RequestAccessSession = requireCredential(sessionToken)

    fun requireNoAuthPermitted(shareLinkId: UUID) {
        if (sessionRepository.recipientSignInRequired(shareLinkId)) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
                "This Exchange requires recipients to sign in; access links and their sessions no longer work",
            )
        }
    }

    private fun requireCredential(sessionToken: String?): RequestAccessSession
    {
        val parts = sessionToken?.split('.', limit = 2)
        val id = parts?.firstOrNull()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val secret = parts?.getOrNull(1)
        if (id == null || secret == null || !secret.matches(Regex("[A-Za-z0-9_-]{43}"))) refuseCredential()
        val presented = sessionRepository.findById(id) ?: refuseCredential()
        if (!MessageDigest.isEqual(
                presented.credentialHash.toByteArray(Charsets.UTF_8),
                hash(secret).toByteArray(Charsets.UTF_8)
            )
        )
            refuseCredential()
        return sessionRepository.findSessionByIdForUpdate(id) ?: refuseCredential()
    }

    private fun refuseCredential(): Nothing = throw InformationRequestLifecycleException(
        InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
        "A verified request session credential is required",
    )

    private fun hash(secret: String): String = MessageDigest.getInstance("SHA-256")
        .digest(secret.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Transactional(Transactional.TxType.MANDATORY)
    fun requireActive(sessionId: UUID): RequestAccessSession
    {
        val session = sessionRepository.findSessionByIdForUpdate(sessionId)
            ?: throw IllegalArgumentException("Request access session not found")
        requireNoAuthPermitted(session.shareLinkId)
        if (session.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
                "This request access session has been revoked",
            )
        }
        if (!session.expiresAt.after(Timestamp.from(Instant.now())))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED,
                "This request access session has expired",
            )
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

    @Transactional(Transactional.TxType.MANDATORY)
    fun update(session: RequestAccessSession): RequestAccessSession = sessionRepository.update(session)

    @Transactional
    fun touchUse(sessionId: UUID): RequestAccessSession
    {
        val session = sessionRepository.findSessionByIdForUpdate(sessionId)
            ?: throw IllegalArgumentException("Request access session not found")
        session.useCount += 1
        session.lastUsedAt = Timestamp.from(Instant.now())
        return sessionRepository.update(session)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeLiveForRequest(requestId: UUID): List<RequestAccessSessionBinding>
    {
        val now = Timestamp.from(Instant.now())
        return sessionRepository.findLiveForRequest(requestId, now).mapNotNull { live ->
            val session = sessionRepository.findSessionByIdForUpdate(live.session.id)
            if (session == null || session.revokedAt != null) return@mapNotNull null
            session.revokedAt = now
            RequestAccessSessionBinding(sessionRepository.update(session), live.partyId)
        }
    }
}
