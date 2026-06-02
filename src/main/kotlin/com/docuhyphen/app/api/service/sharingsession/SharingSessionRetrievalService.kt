package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.SharingSessionBasicDto
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.SharingSession
import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.model.entity.SharingSessionStatus.ACCEPTED_STARTED
import com.docuhyphen.app.api.model.entity.SharingSessionStatus.INITIATED
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.ResourceEndpointDelayHelper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
data class SearchResult(
    val content: Array<SharingSessionBasicDto?>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)

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
        val session = sharingSessionRepository.findByIdWithDocumentsOrderedByTitle(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        return session
    }

    fun getAllSessionsForSignedInAppUser(): List<SharingSession>
    {
        val appUserId = authTokenContext.authToken.appUser?.id ?: return emptyList()
        // findByParticipatingAppUser now resolves access through Share rows (initiator + any
        // active USER share — direct, group-inherited, or participant), so it is the single
        // source of "sessions this user can see".
        val participatingSessions = sharingSessionRepository.findByParticipatingAppUser(appUserId)

        return (participatingSessions)
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
        // Defensive: NPE-ing here would surface as a generic 500 with a misleading
        // "Failed to check for sharing sessions" alert on the frontend. The auth
        // filter normally guarantees appUser is populated, but treat a missing
        // principal as "no sessions" rather than crashing,  the filter already
        // rejects truly unauthenticated calls upstream, so reaching here with a
        // null appUser is a soft anomaly, not a security boundary.
        val appUserId = authTokenContext.authToken.appUser?.id ?: return false
        return sharingSessionRepository.userHasSharingSessions(appUserId)
    }

    fun getNoAuthSharingSession(sessionId: String): SharingSession
    {
        val session = sharingSessionRepository.findByIdWithDocumentsOrderedByTitle(UUID.fromString(sessionId))
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
//        ResourceEndpointDelayHelper.delayEndpoint(1000, 2000)

        val appUserId =
            authTokenContext.authToken.appUser?.id ?: throw IllegalArgumentException("User not authenticated")

        val parsedStatuses = status?.split(",")?.mapNotNull {
            try { SharingSessionStatus.valueOf(it.trim()) }
            catch (e: IllegalArgumentException) { null }
        }?.takeIf { it.isNotEmpty() }

        val sessions = sharingSessionRepository.searchSessions(
            appUserId,
            query,
            parsedStatuses,
            initiatedBy,
            page,
            size,
            sortBy,
            sortDirection
        )

        val totalElements = sharingSessionRepository.countSearchResults(
            appUserId,
            query,
            parsedStatuses,
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