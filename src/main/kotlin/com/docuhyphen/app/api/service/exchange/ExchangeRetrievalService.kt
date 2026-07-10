package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.ExchangeBasicDto
import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED
import com.docuhyphen.app.api.model.entity.ExchangeStatus.INITIATED
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
data class SearchResult(
    val content: Array<ExchangeBasicDto?>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)

@ApplicationScoped
class ExchangeRetrievalService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val shareService: ShareService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeRetrievalService::class.java)
    }

    fun getExchange(exchangeId: String): Exchange
    {
        val session = exchangeRepository.findByIdWithDocumentsOrderedByTitle(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ExchangeNotFoundException("Exchange not found")
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_VIEW,
            resource = ResourceRef.exchange(session.id),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw ExchangeNotFoundException("Exchange not found")
        }

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        return session
    }

    fun hasDocumentInExchange(exchangeId: UUID, documentId: UUID): Boolean =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId) != null

    /**
     * Every non-deleted document id belonging to [exchangeId]. Used by
     * [com.docuhyphen.app.api.service.audit.AuditSearchProjectionService.listExchangeEvents] to
     * widen a document-scoped ledger query to every document in the Exchange plus the
     * Exchange-level target itself, without that service touching [ExchangeRepository] directly.
     */
    fun getDocumentIdsForExchange(exchangeId: UUID): List<UUID>
    {
        val exchange = exchangeRepository.findByIdWithDocumentsOrderedByTitle(exchangeId) ?: return emptyList()
        return exchange.documents.filter { it.isDeleted == false }.map { it.id }
    }

    /**
     * Display-only Exchange name lookup with no authorization check, following the same
     * "display" convention as [primaryRecipientUserIdForDisplay]: used by
     * [com.docuhyphen.app.api.service.audit.AuditSearchProjectionService] to label an
     * Exchange-scoped audit search without re-running [Action.EXCHANGE_VIEW] authorization,
     * which would wrongly fail the label lookup (and, if not caught, the whole search) for an
     * auditor who has audit-read rights on the organization but no direct Exchange-view grant.
     * Must not be used to decide whether the caller may see the Exchange.
     */
    fun getExchangeNameForDisplay(exchangeId: UUID): String? = exchangeRepository.findById(exchangeId)?.name

    /** Same "display, not authorization" convention as [getExchangeNameForDisplay], for a single document's title. */
    fun getDocumentTitleForDisplay(exchangeId: UUID, documentId: UUID): String? =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)?.title

    fun getAllSessionsForSignedInAppUser(): List<Exchange>
    {
        val appUserId = authTokenContext.authToken.appUser?.id ?: return emptyList()
        // findByParticipatingAppUser now resolves access through Share rows (initiator + any
        // active USER share, direct, group-inherited, or participant), so it is the single
        // source of "sessions this user can see".
        val participatingSessions = exchangeRepository.findByParticipatingAppUser(appUserId)

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

    fun checkUserHasExchanges(): Boolean
    {
        // Defensive: NPE-ing here would surface as a generic 500 with a misleading
        // "Failed to check for exchanges" alert on the frontend. The auth
        // filter normally guarantees appUser is populated, but treat a missing
        // principal as "no sessions" rather than crashing,  the filter already
        // rejects truly unauthenticated calls upstream, so reaching here with a
        // null appUser is a soft anomaly, not a security boundary.
        val appUserId = authTokenContext.authToken.appUser?.id ?: return false
        return exchangeRepository.userHasExchanges(appUserId)
    }

    fun getNoAuthExchange(exchangeId: String): NoAuthExchangeBasicDto
    {
        val session = exchangeRepository.findByIdWithDocumentsOrderedByTitle(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a exchange that requires recipient sign-in")
            throw ExchangeNotFoundException("Exchange not found")
        }

        if (session.status != ACCEPTED_STARTED && session.status != INITIATED)
        {
            logger.error("Attempted to access a exchange that is not in the correct status")
            throw ExchangeNotFoundException("Exchange not found")
        }

        val dto = BasicEntityToDtoTransformer.toNoAuthDto(session)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val constraintsJson = shareService.recipientConstraintsJson(session.id)
        val constraints = ShareConstraints.parse(constraintsJson)
        val downloadAllowed = constraints?.canDownload != false &&
            (constraintsJson?.contains("\"allow_document_download\":true") == true ||
                constraints?.canDownload == true)
        dto.allowDocumentDownload = downloadAllowed

        return dto
    }

    fun searchExchanges(
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
            try { ExchangeStatus.valueOf(it.trim()) }
            catch (e: IllegalArgumentException) { null }
        }?.takeIf { it.isNotEmpty() }

        val sessions = exchangeRepository.searchSessions(
            appUserId,
            query,
            parsedStatuses,
            initiatedBy,
            page,
            size,
            sortBy,
            sortDirection
        )

        val totalElements = exchangeRepository.countSearchResults(
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

    fun getExchangesLinkedToAppUserId(appUserId: UUID): List<Exchange>
    {
        return exchangeRepository.getAppUserLinkedExchanges(appUserId)
    }
}
