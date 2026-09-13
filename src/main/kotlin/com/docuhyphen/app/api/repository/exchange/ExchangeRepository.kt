package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository.Companion.SEARCH_PREDICATE
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class ExchangeRepository : BaseRepository<Exchange>(Exchange::class.java)
{
    companion object
    {
        /**
         * An Exchange is accessible when the user initiated it, holds an active USER Share, or is
         * the pending primary recipient. Pending primary visibility is limited to the exact Share
         * referenced by the primary recipient binding, so pending participant Shares stay hidden.
         */
        private const val PENDING_PRIMARY_ACCESS =
            "EXISTS (SELECT er FROM ExchangeRecipient er, Share psh " +
                "WHERE er.exchangeId = s.id AND er.purpose = :primaryPurpose " +
                "AND er.acceptanceStatus = :pendingAcceptanceStatus " +
                "AND psh.id = er.directShareId AND psh.status = :pendingShareStatus " +
                "AND ((psh.principalKind = :upk AND psh.principalId = :appUserId) OR " +
                "(psh.principalKind = :gpk AND EXISTS (SELECT pgm FROM PrincipalGroupMember pgm " +
                "WHERE pgm.principalGroupId = psh.principalId AND pgm.principalKind = :upk " +
                "AND pgm.principalId = :appUserId AND pgm.isActive = true " +
                "AND pgm.groupRole IN :decisionGroupRoles))) " +
                "AND (psh.expiresAt IS NULL OR psh.expiresAt > CURRENT_TIMESTAMP))"

        private const val ACCESSIBLE =
            "(s.initiator.id = :appUserId OR EXISTS (" +
                "SELECT sh FROM Share sh WHERE sh.resourceType = :srt AND sh.resourceId = s.id " +
                "AND sh.principalKind = :upk AND sh.principalId = :appUserId AND sh.status = :ass " +
                "AND (sh.expiresAt IS NULL OR sh.expiresAt > CURRENT_TIMESTAMP)) OR " +
                PENDING_PRIMARY_ACCESS + ")"

        private const val ARCHIVED_VISIBLE =
            "(s.status IN :archivedStatuses AND (s.initiator.id = :appUserId OR EXISTS (" +
                "SELECT ash FROM Share ash WHERE ash.resourceType = :srt AND ash.resourceId = s.id " +
                "AND ash.principalKind = :upk AND ash.principalId = :appUserId " +
                "AND s.endDate IS NOT NULL AND ash.grantedAt <= s.endDate " +
                "AND (ash.expiresAt IS NULL OR ash.expiresAt >= s.endDate) " +
                "AND (ash.revokedAt IS NULL OR ash.revokedAt >= s.endDate))))"

        private const val ACCESSIBLE_OR_ARCHIVED = "($ACCESSIBLE OR $ARCHIVED_VISIBLE)"

        /**
         * Visibility predicate for INITIATED (draft) exchanges in [searchSessions] /
         * [countSearchResults]. A draft exchange is visible to the user when at least one of:
         *  - the exchange is not in draft state (status != INITIATED)
         *  - the user is the initiator
         *  - the user holds an active USER Share with a role other than PARTICIPANT
         *  - the user is the pending primary recipient
         *
         * This prevents pure participant-role users (co-witnesses added at creation) from seeing
         * an exchange before the primary recipient has accepted, while correctly showing it to
         * users with elevated roles such as MANAGER - even when they also carry an inherited
         * PARTICIPANT share from a group that is itself a participant on the exchange.
         *
         * Requires :initiatedStatus, :srt, :upk, :ass, :appUserId (all bound by the callers).
         */
        private const val DRAFT_VISIBLE =
            "(s.status <> :initiatedStatus OR s.initiator.id = :appUserId OR EXISTS (" +
                "SELECT sh2 FROM Share sh2 WHERE sh2.resourceType = :srt AND sh2.resourceId = s.id " +
                "AND sh2.principalKind = :upk AND sh2.principalId = :appUserId AND sh2.status = :ass " +
                "AND (sh2.expiresAt IS NULL OR sh2.expiresAt > CURRENT_TIMESTAMP) " +
                    "AND sh2.roleName <> 'PARTICIPANT') OR " + PENDING_PRIMARY_ACCESS + ")"

        /**
         * Free-text predicate for [searchSessions] / [countSearchResults]. Matches the session's
         * own fields plus its recipients, which under the unified model live in `share` rows keyed
         * by a polymorphic `principalId` (no JPA relationship), so each recipient kind is reached
         * via a correlated subquery: USER shares map to [com.docuhyphen.app.api.model.entity.AppUser]
         * email, PARTICIPANT shares map to [com.docuhyphen.app.api.model.entity.ExternalParticipant]
         * email/name, and PRINCIPAL_GROUP shares map to [com.docuhyphen.app.api.model.entity.PrincipalGroup]
         * name. Requires :query, :srt, :upk, :ass (bound by [bindAccess]) plus :ppk, :gpk.
         */
        private const val SEARCH_PREDICATE =
            " AND (" +
                "LOWER(s.name) LIKE LOWER(:query) " +
                "OR LOWER(s.description) LIKE LOWER(:query) " +
                "OR LOWER(s.initiator.email) LIKE LOWER(:query) " +
                "OR EXISTS (SELECT rsh FROM Share rsh, AppUser ru " +
                    "WHERE rsh.resourceType = :srt AND rsh.resourceId = s.id " +
                    "AND rsh.principalKind = :upk AND rsh.status = :ass " +
                    "AND ru.id = rsh.principalId AND LOWER(ru.email) LIKE LOWER(:query)) " +
                "OR EXISTS (SELECT psh FROM Share psh, ExternalParticipant ep " +
                    "WHERE psh.resourceType = :srt AND psh.resourceId = s.id " +
                    "AND psh.principalKind = :ppk AND psh.status = :ass " +
                    "AND ep.id = psh.principalId " +
                    "AND (LOWER(ep.email) LIKE LOWER(:query) OR LOWER(ep.displayName) LIKE LOWER(:query))) " +
                "OR EXISTS (SELECT gsh FROM Share gsh, PrincipalGroup pg " +
                    "WHERE gsh.resourceType = :srt AND gsh.resourceId = s.id " +
                    "AND gsh.principalKind = :gpk AND gsh.status = :ass " +
                    "AND pg.id = gsh.principalId AND LOWER(pg.name) LIKE LOWER(:query))" +
            ")"
    }

    /** Binds the extra principal-kind params used by [SEARCH_PREDICATE]. */
    private fun bindSearchKinds(query: jakarta.persistence.TypedQuery<*>)
    {
        query.setParameter("ppk", PrincipalKind.PARTICIPANT)
        query.setParameter("gpk", PrincipalKind.PRINCIPAL_GROUP)
    }

    private fun <T> bindAccess(query: jakarta.persistence.TypedQuery<T>, userId: UUID): jakarta.persistence.TypedQuery<T> =
        query
            .setParameter("appUserId", userId)
            .setParameter("srt", ResourceType.EXCHANGE)
            .setParameter("upk", PrincipalKind.USER)
            .setParameter("ass", ShareStatus.ACTIVE)

    private fun <T> bindAccessible(query: jakarta.persistence.TypedQuery<T>, userId: UUID): jakarta.persistence.TypedQuery<T> =
        bindAccess(query, userId)
            .setParameter("primaryPurpose", ExchangeRecipientPurpose.PRIMARY)
            .setParameter("pendingAcceptanceStatus", ExchangeRecipientAcceptanceStatus.PENDING)
            .setParameter("pendingShareStatus", ShareStatus.PENDING_APPROVAL)
            .setParameter(
                "archivedStatuses",
                setOf(ExchangeStatus.ENDED, ExchangeStatus.REJECTED, ExchangeStatus.RESCINDED),
            )
            .setParameter("gpk", PrincipalKind.PRINCIPAL_GROUP)
            .setParameter(
                "decisionGroupRoles",
                setOf(PrincipalGroupRoleName.OWNER, PrincipalGroupRoleName.MANAGER),
            )

    fun userHasExchanges(userId: UUID): Boolean {
        val count = bindAccessible(
            entityManager.createQuery(
                "SELECT COUNT(DISTINCT s) FROM Exchange s WHERE $ACCESSIBLE_OR_ARCHIVED AND s.isDeleted = false",
                Long::class.java,
            ),
            userId,
        ).singleResult ?: 0
        return count > 0
    }

    /** Sessions where the user is a recipient, i.e. holds a non-OWNER active USER share. */
    fun findByRecipientId(recipient: UUID): List<Exchange> =
        bindAccess(
            entityManager.createQuery(
                """SELECT DISTINCT s FROM Exchange s WHERE EXISTS (
                   SELECT sh FROM Share sh WHERE sh.resourceType = :srt AND sh.resourceId = s.id
                   AND sh.principalKind = :upk AND sh.principalId = :appUserId AND sh.status = :ass
                   AND sh.roleName <> 'OWNER')""",
                Exchange::class.java,
            ),
            recipient,
        ).resultList

    fun findByParticipatingAppUser(appUserId: UUID): List<Exchange>
    {
        return bindAccessible(
            entityManager.createQuery(
                "SELECT DISTINCT s FROM Exchange s WHERE $ACCESSIBLE_OR_ARCHIVED AND s.isDeleted = false",
                Exchange::class.java,
            ),
            appUserId,
        ).resultList
    }

    @Transactional
    fun updateSessionName(exchangeId: UUID, name: String)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.name = :name WHERE s.id = :exchangeId"
        )
        query.setParameter("name", name)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateDescription(exchangeId: UUID, description: String)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.description = :description WHERE s.id = :exchangeId"
        )
        query.setParameter("description", description)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateStatus(exchangeId: UUID, status: ExchangeStatus)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.status = :status WHERE s.id = :exchangeId"
        )
        query.setParameter("status", status)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateEndDate(exchangeId: UUID, timestamp: Timestamp)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.endDate = :timestamp WHERE s.id = :exchangeId"
        )
        query.setParameter("timestamp", timestamp)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateRejectionReason(exchangeId: UUID, rejectionReason: String?)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.rejectionReason = :rejectionReason WHERE s.id = :exchangeId"
        )
        query.setParameter("rejectionReason", rejectionReason)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateLastActivity(exchangeId: UUID, lastActivity: Timestamp)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.lastActivity = :lastActivity WHERE s.id = :exchangeId"
        )
        query.setParameter("lastActivity", lastActivity)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateRequireRecipientSignIn(exchangeId: UUID, requestRecipientSignIn: Boolean)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.requireRecipientSignIn = :requestRecipientSignIn WHERE s.id = :exchangeId"
        )
        query.setParameter("requestRecipientSignIn", requestRecipientSignIn)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateNoAuthAccessValidityDays(exchangeId: UUID, noAuthAccessValidityDays: Int)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.noAuthAccessValidityDays = :noAuthAccessValidityDays WHERE s.id = :exchangeId"
        )
        query.setParameter("noAuthAccessValidityDays", noAuthAccessValidityDays)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    @Transactional
    fun updateNoAuthAccessVerifiedAt(exchangeId: UUID, verifiedAt: Timestamp?)
    {
        val query = entityManager.createQuery(
            "UPDATE Exchange s SET s.noAuthAccessVerifiedAt = :verifiedAt WHERE s.id = :exchangeId"
        )
        query.setParameter("verifiedAt", verifiedAt)
        query.setParameter("exchangeId", exchangeId)
        query.executeUpdate()
    }

    fun searchSessions(
        appUserId: UUID,
        query: String?,
        statuses: List<ExchangeStatus>?,
        initiatedBy: Boolean?,
        page: Int,
        size: Int,
        sortBy: String,
        sortDirection: String
    ): List<Exchange>
    {
        val queryBuilder = StringBuilder(
            """
        SELECT DISTINCT s FROM Exchange s
        WHERE $ACCESSIBLE_OR_ARCHIVED
        AND s.isDeleted = false
        AND $DRAFT_VISIBLE
    """
        )

        if (!query.isNullOrBlank())
        {
            queryBuilder.append(SEARCH_PREDICATE)
        }

        if (!statuses.isNullOrEmpty())
        {
            queryBuilder.append(" AND s.status IN :statuses")
        }

        if (initiatedBy != null)
        {
            if (initiatedBy)
            {
                queryBuilder.append(" AND s.initiator.id = :appUserId")
            }
            else
            {
                queryBuilder.append(" AND s.initiator.id <> :appUserId")
            }
        }

        val validSortFields = setOf("createdDate", "name", "lastActivity")
        val safeSort = if (validSortFields.contains(sortBy)) sortBy else "createdDate"
        val safeDirection = if (sortDirection.equals("ASC", ignoreCase = true)) "ASC" else "DESC"

        queryBuilder.append(" ORDER BY s.$safeSort $safeDirection")

        val jpaQuery = bindAccessible(entityManager.createQuery(queryBuilder.toString(), Exchange::class.java), appUserId)
        jpaQuery.setParameter("initiatedStatus", ExchangeStatus.INITIATED)

        if (!query.isNullOrBlank())
        {
            jpaQuery.setParameter("query", "%${query.trim()}%")
            bindSearchKinds(jpaQuery)
        }

        if (!statuses.isNullOrEmpty())
        {
            jpaQuery.setParameter("statuses", statuses)
        }

        jpaQuery.firstResult = page * size
        jpaQuery.maxResults = size

        val sessions = jpaQuery.resultList

        sessions.forEach { session ->
            session.documents = session.documents.filter { !it.isDeleted } as MutableList<Document>
        }

        return sessions
    }

    fun countSearchResults(
        appUserId: UUID,
        query: String?,
        statuses: List<ExchangeStatus>?,
        initiatedBy: Boolean?
    ): Long
    {
        val queryBuilder = StringBuilder(
            """
        SELECT COUNT(DISTINCT s) FROM Exchange s
        WHERE $ACCESSIBLE_OR_ARCHIVED
        AND s.isDeleted = false
        AND $DRAFT_VISIBLE
    """
        )

        if (!query.isNullOrBlank())
        {
            queryBuilder.append(SEARCH_PREDICATE)
        }

        if (!statuses.isNullOrEmpty())
        {
            queryBuilder.append(" AND s.status IN :statuses")
        }

        if (initiatedBy != null)
        {
            if (initiatedBy)
            {
                queryBuilder.append(" AND s.initiator.id = :appUserId")
            }
            else
            {
                queryBuilder.append(" AND s.initiator.id <> :appUserId")
            }
        }

        val jpaQuery = bindAccessible(entityManager.createQuery(queryBuilder.toString(), Long::class.java), appUserId)
        jpaQuery.setParameter("initiatedStatus", ExchangeStatus.INITIATED)

        if (!query.isNullOrBlank())
        {
            jpaQuery.setParameter("query", "%${query.trim()}%")
            bindSearchKinds(jpaQuery)
        }

        if (!statuses.isNullOrEmpty())
        {
            jpaQuery.setParameter("statuses", statuses)
        }

        return jpaQuery.singleResult
    }

    fun getAppUserLinkedExchanges(appUserId: UUID): List<Exchange>
    {
        return bindAccessible(
            entityManager.createQuery(
                "SELECT s FROM Exchange s WHERE $ACCESSIBLE_OR_ARCHIVED AND s.isDeleted = false",
                Exchange::class.java,
            ),
            appUserId,
        ).resultList
    }

    fun hasHistoricalArchiveAccess(exchangeId: UUID, appUserId: UUID): Boolean
    {
        val count = entityManager.createQuery(
            """
            SELECT COUNT(DISTINCT s)
            FROM Exchange s
            WHERE s.id = :exchangeId
              AND s.isDeleted = false
              AND $ARCHIVED_VISIBLE
            """.trimIndent(),
            Long::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .setParameter("appUserId", appUserId)
            .setParameter("srt", ResourceType.EXCHANGE)
            .setParameter("upk", PrincipalKind.USER)
            .setParameter(
                "archivedStatuses",
                setOf(ExchangeStatus.ENDED, ExchangeStatus.REJECTED, ExchangeStatus.RESCINDED),
            )
            .singleResult ?: 0

        return count > 0
    }

    fun findByIdWithDocumentsOrderedByTitle(exchangeId: UUID): Exchange?
    {
        val session = entityManager.createQuery(
            """
                SELECT s
                FROM Exchange s
                WHERE s.id = :exchangeId
            """.trimIndent(),
            Exchange::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList
            .firstOrNull()
            ?: return null

        val orderedDocuments = entityManager.createQuery(
            """
                SELECT d
                FROM Exchange s
                JOIN s.documents d
                WHERE s.id = :exchangeId
                ORDER BY LOWER(d.title) ASC, d.title ASC
            """.trimIndent(),
            Document::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList

        session.documents = orderedDocuments.toMutableList()
        return session
    }

    fun findDocumentBySessionIdAndDocumentId(exchangeId: UUID, documentId: UUID): Document?
    {
        return entityManager.createQuery(
            """
                SELECT d
                FROM Exchange s
                JOIN s.documents d
                WHERE s.id = :exchangeId
                  AND d.id = :documentId
            """.trimIndent(),
            Document::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .setParameter("documentId", documentId)
            .resultList
            .firstOrNull()
    }

    fun findByDocumentId(documentId: UUID): Exchange?
    {
        return entityManager.createQuery(
            """
                SELECT s
                FROM Exchange s
                JOIN s.documents d
                WHERE d.id = :documentId
            """.trimIndent(),
            Exchange::class.java,
        )
            .setParameter("documentId", documentId)
            .resultList
            .firstOrNull()
    }

    /**
     * Exchanges owned personally by a user and created inside the given half-open window.
     * Organization-owned Exchanges are excluded because the organization pays for those.
     */
    fun countCreatedByOwnerUserBetween(
        ownerUserId: UUID,
        fromInclusive: Timestamp,
        toExclusive: Timestamp,
    ): Long
    {
        return entityManager.createQuery(
            """
                SELECT COUNT(s)
                FROM Exchange s
                WHERE s.ownerUserId = :ownerUserId
                  AND s.ownerOrganizationId IS NULL
                  AND s.isDeleted = false
                  AND s.createdDate >= :fromInclusive
                  AND s.createdDate < :toExclusive
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("ownerUserId", ownerUserId)
            .setParameter("fromInclusive", fromInclusive)
            .setParameter("toExclusive", toExclusive)
            .singleResult
            .toLong()
    }

    /** Exchanges owned by an organization and created inside the given half-open window. */
    fun countCreatedByOwnerOrganizationBetween(
        ownerOrganizationId: UUID,
        fromInclusive: Timestamp,
        toExclusive: Timestamp,
    ): Long
    {
        return entityManager.createQuery(
            """
                SELECT COUNT(s)
                FROM Exchange s
                WHERE s.ownerOrganizationId = :ownerOrganizationId
                  AND s.isDeleted = false
                  AND s.createdDate >= :fromInclusive
                  AND s.createdDate < :toExclusive
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("ownerOrganizationId", ownerOrganizationId)
            .setParameter("fromInclusive", fromInclusive)
            .setParameter("toExclusive", toExclusive)
            .singleResult
            .toLong()
    }

    /** Exchanges owned personally by a user that currently hold one of the given statuses. */
    fun countByOwnerUserAndStatuses(
        ownerUserId: UUID,
        statuses: Collection<ExchangeStatus>,
    ): Long
    {
        if (statuses.isEmpty())
        {
            return 0
        }

        return entityManager.createQuery(
            """
                SELECT COUNT(s)
                FROM Exchange s
                WHERE s.ownerUserId = :ownerUserId
                  AND s.ownerOrganizationId IS NULL
                  AND s.isDeleted = false
                  AND s.status IN :statuses
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("ownerUserId", ownerUserId)
            .setParameter("statuses", statuses)
            .singleResult
            .toLong()
    }

    /** Exchanges owned by an organization that currently hold one of the given statuses. */
    fun countByOwnerOrganizationAndStatuses(
        ownerOrganizationId: UUID,
        statuses: Collection<ExchangeStatus>,
    ): Long
    {
        if (statuses.isEmpty())
        {
            return 0
        }

        return entityManager.createQuery(
            """
                SELECT COUNT(s)
                FROM Exchange s
                WHERE s.ownerOrganizationId = :ownerOrganizationId
                  AND s.isDeleted = false
                  AND s.status IN :statuses
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("ownerOrganizationId", ownerOrganizationId)
            .setParameter("statuses", statuses)
            .singleResult
            .toLong()
    }
}
