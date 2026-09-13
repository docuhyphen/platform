package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class RequestAccessSessionRepository :
    BaseRepository<RequestAccessSession>(RequestAccessSession::class.java)
{
    fun lockParentForShare(shareId: UUID): com.docuhyphen.app.api.service.informationrequest.InformationRequestParentSnapshot?
    {
        val parent = entityManager.createQuery(
            """
            SELECT parent FROM Exchange parent, InformationRequest request, Share share
            WHERE share.id = :shareId AND share.resourceType = :type
              AND share.resourceId = request.id AND request.exchangeId = parent.id
            """.trimIndent(), com.docuhyphen.app.api.model.entity.Exchange::class.java,
        ).setParameter("shareId", shareId)
            .setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .resultList.firstOrNull() ?: return null
        entityManager.refresh(parent, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        return com.docuhyphen.app.api.service.informationrequest.InformationRequestParentSnapshot(parent.status, parent.isDeleted, true)
    }

    fun findSessionByIdForUpdate(id: UUID): RequestAccessSession?
    {
        val session = findById(id) ?: return null
        val link = entityManager.find(com.docuhyphen.app.api.model.entity.ShareLink::class.java, session.shareLinkId)
            ?: return null
        lockParentForShare(link.shareId) ?: return null
        entityManager.refresh(session, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        return session
    }

    fun findActiveForRequest(requestId: UUID): List<RequestAccessSession> =
        entityManager.createQuery(
            """
            SELECT session FROM RequestAccessSession session, ShareLink link, Share share
            WHERE session.shareLinkId = link.id AND link.shareId = share.id
              AND share.resourceType = :type AND share.resourceId = :requestId
              AND session.revokedAt IS NULL
            ORDER BY session.id
            """.trimIndent(), RequestAccessSession::class.java,
        ).setParameter("type", com.docuhyphen.app.api.model.entity.ResourceType.INFORMATION_REQUEST)
            .setParameter("requestId", requestId).resultList

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
