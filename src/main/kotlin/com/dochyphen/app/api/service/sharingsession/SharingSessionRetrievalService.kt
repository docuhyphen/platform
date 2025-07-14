package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.BasicEntityToDtoTransformer
import com.dochyphen.app.api.model.dto.SharingSessionBasicDto
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.SharingSession
import com.dochyphen.app.api.model.entity.SharingSessionStatus
import com.dochyphen.app.api.model.entity.SharingSessionStatus.ACCEPTED_STARTED
import com.dochyphen.app.api.model.entity.SharingSessionStatus.INITIATED
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.resource.ResourceEndpointDelayHelper
import com.yubico.webauthn.extension.appid.AppId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class SharingSessionRetrievalService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val authTokenContext: AuthTokenContext
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionRetrievalService::class.java)
    }

    fun getSharingSession(sessionId: String): SharingSession
    {
        val session = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        return session
    }

    fun getAllSessionsForSignedInAppUser(): List<SharingSession>
    {
        val appUserId = authTokenContext.authToken.appUser?.id ?: return emptyList()
        val initiatedSessions = sharingSessionRepository.findByInitiatorId(appUserId)
        val receivedSessions = sharingSessionRepository.findByRecipientId(appUserId)
        val participatingSessions = sharingSessionRepository.findByParticipatingAppUser(appUserId)

        return (initiatedSessions + receivedSessions + participatingSessions)
            .distinctBy { it.id }
            .sortedByDescending { it.createdDate }
            .filter { !it.isDeleted }
            .map { session ->
                session.apply {
                    documents = documents.filter { !it.isDeleted } as MutableList<Document>
                }
            }
    }

    fun checkUserHasSharingSessions(): Boolean
    {
        val appUserId = authTokenContext.authToken.appUser?.id
        return sharingSessionRepository.userHasSharingSessions(appUserId!!)
    }

    fun getNoAuthSharingSession(sessionId: String): SharingSession
    {
        val session = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a sharing session that requires recipient sign-in")
            throw SharingSessionNotFoundException("Sharing session not found")
        }

        if (session.status != ACCEPTED_STARTED && session.status != INITIATED)
        {
            logger.error("Attempted to access a sharing session that is not in the correct status")
            throw SharingSessionNotFoundException("Sharing session not found")
        }

        return session
    }

    @Serializable
    data class SearchResult(
        val content: Array<SharingSessionBasicDto?>,
        val totalElements: Long,
        val totalPages: Int,
        val currentPage: Int,
        val pageSize: Int
    )

    fun searchSharingSessions(
        query: String?,
        status: String?,
        initiatedBy: Boolean?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): SearchResult
    {
        ResourceEndpointDelayHelper.delayEndpoint(1000, 2000)

        val appUserId =
            authTokenContext.authToken.appUser?.id ?: throw IllegalArgumentException("User not authenticated")

        val sessions = sharingSessionRepository.searchSessions(
            appUserId,
            query,
            status?.let {
                try
                {
                    SharingSessionStatus.valueOf(it)
                }
                catch (e: IllegalArgumentException)
                {
                    null
                }
            },
            initiatedBy,
            page,
            size,
            sortBy,
            sortDirection
        )

        val totalElements = sharingSessionRepository.countSearchResults(
            appUserId,
            query,
            status?.let {
                try
                {
                    SharingSessionStatus.valueOf(it)
                }
                catch (e: IllegalArgumentException)
                {
                    null
                }
            },
            initiatedBy
        )

        val totalPages = if (size > 0) (totalElements + size - 1) / size else 0

        return SearchResult(
            content = sessions.map { BasicEntityToDtoTransformer.toDto(it) }.toTypedArray(),
            totalElements = totalElements,
            totalPages = totalPages.toInt(),
            currentPage = page,
            pageSize = size
        )
    }

    fun getSharingSessionsLinkedToAppUserId(appUserId: UUID): List<SharingSession>
    {
        return sharingSessionRepository.getAppUserLinkedSharingSessions(appUserId)
    }
}