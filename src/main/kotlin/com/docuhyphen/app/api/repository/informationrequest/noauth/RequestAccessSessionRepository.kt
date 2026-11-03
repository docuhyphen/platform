package com.docuhyphen.app.api.repository.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.noauth.RequestAccessSessionBinding
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class RequestAccessSessionRepository :
    BaseRepository<RequestAccessSession>(RequestAccessSession::class.java)
{
    fun lockParentForShare(shareId: UUID): com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot?
    {
        val parent = entityManager.createQuery(
            """
            SELECT parent FROM Exchange parent, InformationRequest request, Share share
            WHERE share.id = :shareId AND share.resourceType = :type
              AND share.resourceId = request.id AND request.exchangeId = parent.id
            """.trimIndent(),
            com.docuhyphen.app.api.model.entity.Exchange::class.java,
        ).setParameter("shareId", shareId)
            .setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .resultList.firstOrNull() ?: return null
        entityManager.lock(parent, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        entityManager.refresh(parent)
        return com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot(
            parent.status,
            parent.isDeleted,
            true
        )
    }

    fun requestStateForShare(shareId: UUID): com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState? =
        entityManager.createQuery(
            """
            SELECT request.state FROM InformationRequest request, Share share
            WHERE share.id = :shareId AND share.resourceType = :type AND share.resourceId = request.id
            """.trimIndent(),
            com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState::class.java,
        ).setParameter("shareId", shareId)
            .setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .resultList
            .firstOrNull()

    fun recipientSignInRequired(shareLinkId: UUID): Boolean =
        entityManager.createQuery(
            """
            SELECT parent.requireRecipientSignIn
            FROM ShareLink link, Share share, InformationRequest request, Exchange parent
            WHERE link.id = :shareLinkId AND share.id = link.shareId AND share.resourceType = :type
              AND share.resourceId = request.id AND request.exchangeId = parent.id
            """.trimIndent(),
            java.lang.Boolean::class.java,
        ).setParameter("shareLinkId", shareLinkId)
            .setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .resultList
            .any { it.booleanValue() }

    fun findSessionByIdForUpdate(id: UUID): RequestAccessSession?
    {
        val session = findById(id) ?: return null
        val link = entityManager.find(com.docuhyphen.app.api.model.entity.ShareLink::class.java, session.shareLinkId)
            ?: return null
        lockParentForShare(link.shareId) ?: return null
        entityManager.lock(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        entityManager.refresh(session)
        return session
    }

    fun findLiveForRequest(requestId: UUID, now: java.sql.Timestamp): List<RequestAccessSessionBinding> =
        entityManager.createQuery(
            """
            SELECT session, party.id
            FROM RequestAccessSession session
            JOIN ShareLink link ON link.id = session.shareLinkId
            JOIN Share share ON share.id = link.shareId
            LEFT JOIN InformationRequestParty party
                ON party.shareId = share.id AND party.informationRequestId = :requestId
            WHERE share.resourceType = :type AND share.resourceId = :requestId
              AND session.revokedAt IS NULL AND session.expiresAt > :now
            ORDER BY session.issuedAt, session.id
            """.trimIndent(),
            Array<Any?>::class.java,
        ).setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .setParameter("requestId", requestId)
            .setParameter("now", now)
            .resultList
            .map { row -> RequestAccessSessionBinding(row[0] as RequestAccessSession, row[1] as UUID?) }

    fun findActiveByShareLinkId(shareLinkId: UUID): List<RequestAccessSession> =
        entityManager.createQuery(
            """
            SELECT session
            FROM RequestAccessSession session
            WHERE session.shareLinkId = :shareLinkId
              AND session.revokedAt IS NULL
            """.trimIndent(),
            RequestAccessSession::class.java,
        )
            .setParameter("shareLinkId", shareLinkId)
            .resultList
}
