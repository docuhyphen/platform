package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.auth.authz.ShareConstraints
import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED
import com.docuhyphen.app.api.model.entity.ExchangeStatus.INITIATED
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.exchange.SearchResult
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class ExchangeRetrievalService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val shareService: ShareService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val noAuthExchangeAccessTokenService: NoAuthExchangeAccessTokenService,
    private val noAuthExchangeAccessWindowService: NoAuthExchangeAccessWindowService,
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
        if (decision is Decision.Deny &&
            !canViewPendingPrimaryInvitation(session, principal) &&
            !canViewArchivedExchange(session, principal)
        )
        {
            throw ExchangeNotFoundException("Exchange not found")
        }

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        return session
    }

    fun hasDocumentInExchange(exchangeId: UUID, documentId: UUID): Boolean =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId) != null

    fun getDocumentIdsForExchange(exchangeId: UUID): List<UUID>
    {
        val exchange = exchangeRepository.findByIdWithDocumentsOrderedByTitle(exchangeId) ?: return emptyList()
        return exchange.documents.filter { it.isDeleted == false }.map { it.id }
    }

    fun getExchangeNameForDisplay(exchangeId: UUID): String? = exchangeRepository.findById(exchangeId)?.name

    fun getDocumentTitleForDisplay(exchangeId: UUID, documentId: UUID): String? =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, documentId)?.title

    fun getAllSessionsForSignedInAppUser(): List<Exchange>
    {
        val appUserId = authTokenContext.authToken.appUser?.id ?: return emptyList()
        val participatingSessions = exchangeRepository.findByParticipatingAppUser(appUserId)

        return (participatingSessions)
            .distinctBy { it.id }
            .filter(::canCurrentPrincipalView)
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
        if (authTokenContext.authToken.appUser?.id == null) return false
        return getAllSessionsForSignedInAppUser().isNotEmpty()
    }

    fun getNoAuthExchange(
        exchangeId: String,
        noAuthAccessToken: String? = null,
        shareLinkTokenValidated: Boolean = false,
    ): NoAuthExchangeBasicDto
    {
        val session = exchangeRepository.findByIdWithDocumentsOrderedByTitle(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (!shareLinkTokenValidated)
        {
            noAuthExchangeAccessTokenService.requireValid(session, noAuthAccessToken)
        }

        session.documents = session.documents.filter { it.isDeleted == false } as MutableList<Document>

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access an Exchange that requires recipient sign-in")
            throw ExchangeNotFoundException("Exchange not found")
        }

        if (session.status != ACCEPTED_STARTED && session.status != INITIATED)
        {
            logger.error("Attempted to access an Exchange that is not in the correct status")
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
        dto.accessVerificationRequired = session.status == ACCEPTED_STARTED &&
                !noAuthExchangeAccessWindowService.isActive(session)

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

        val appUserId =
            authTokenContext.authToken.appUser?.id ?: throw IllegalArgumentException("User not authenticated")

        val parsedStatuses = status?.split(",")?.mapNotNull {
            try
            {
                ExchangeStatus.valueOf(it.trim())
            }
            catch (e: IllegalArgumentException)
            {
                null
            }
        }?.takeIf { it.isNotEmpty() }

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val authorizedSessions = exchangeRepository.searchSessions(
            appUserId,
            query,
            parsedStatuses,
            initiatedBy,
            0,
            Int.MAX_VALUE,
            sortBy,
            sortDirection
        ).filter(::canCurrentPrincipalView)

        val totalElements = authorizedSessions.size.toLong()
        val totalPages = (totalElements + safeSize - 1) / safeSize
        val fromIndex = (safePage.toLong() * safeSize).coerceAtMost(totalElements).toInt()
        val toIndex = (fromIndex + safeSize).coerceAtMost(authorizedSessions.size)
        val sessions = authorizedSessions.subList(fromIndex, toIndex)

        return SearchResult(
            content = sessions.map { BasicEntityToDtoTransformer.toDto(it) }.toTypedArray(),
            totalElements = totalElements,
            totalPages = totalPages.toInt(),
            currentPage = safePage,
            pageSize = safeSize
        )
    }

    private fun canCurrentPrincipalView(exchange: Exchange): Boolean
    {
        val principal = authorizationContextFactory.currentPrincipal() ?: return false
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_VIEW,
            resource = ResourceRef.exchange(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )
        return decision is Decision.Allow ||
                canViewPendingPrimaryInvitation(exchange, principal) ||
                canViewArchivedExchange(exchange, principal)
    }

    private fun canViewPendingPrimaryInvitation(exchange: Exchange, principal: PrincipalRef): Boolean =
        principal.kind == PrincipalKind.USER &&
                exchange.status == ExchangeStatus.INITIATED &&
                exchangeRecipientService.canViewPendingPrimaryInvitation(exchange.id, principal.id)

    private fun canViewArchivedExchange(exchange: Exchange, principal: PrincipalRef): Boolean =
        principal.kind == PrincipalKind.USER &&
                (
                        exchange.status == ExchangeStatus.ENDED ||
                                exchange.status == ExchangeStatus.REJECTED ||
                                exchange.status == ExchangeStatus.RESCINDED
                        ) &&
                exchangeRepository.hasHistoricalArchiveAccess(exchange.id, principal.id)

    fun getExchangesLinkedToAppUserId(appUserId: UUID): List<Exchange>
    {
        return exchangeRepository.getAppUserLinkedExchanges(appUserId)
    }
}
