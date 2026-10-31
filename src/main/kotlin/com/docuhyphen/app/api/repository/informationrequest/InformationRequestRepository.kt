package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsCriteria
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestRepository :
    BaseRepository<InformationRequest>(InformationRequest::class.java)
{
    fun flushPendingChanges() = entityManager.flush()

    fun findRequestByIdForUpdate(id: UUID): InformationRequest?
    {
        val request = findById(id) ?: return null
        if (!sharesRow(LOCK_EXCHANGE, request.exchangeId) || !sharesRow(LOCK_REQUEST, id)) return null
        val parent = entityManager.find(com.docuhyphen.app.api.model.entity.Exchange::class.java, request.exchangeId)
            ?: return null
        entityManager.refresh(parent)
        entityManager.refresh(request)
        return request
    }

    fun findRequestByIdForShare(id: UUID): InformationRequest? {
        val request = findById(id) ?: return null
        if (!sharesRow(SHARE_EXCHANGE, request.exchangeId) || !sharesRow(SHARE_REQUEST, id)) return null
        val parent = entityManager.find(com.docuhyphen.app.api.model.entity.Exchange::class.java, request.exchangeId)
            ?: return null
        entityManager.refresh(parent)
        entityManager.refresh(request)
        return request
    }

    private fun sharesRow(sql: String, id: UUID): Boolean =
        entityManager.createNativeQuery(sql)
            .setParameter("id", id)
            .resultList
            .isNotEmpty()

    fun findParentExchangeIds(requestIds: Collection<UUID>): Map<UUID, UUID> =
        if (requestIds.isEmpty()) emptyMap()
        else entityManager.createQuery(
            "SELECT request.id, request.exchangeId FROM InformationRequest request WHERE request.id IN :requestIds",
            Array<Any>::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
            .associate { (it[0] as UUID) to (it[1] as UUID) }

    fun findForExchange(exchangeId: UUID): List<InformationRequest> =
        entityManager.createQuery(
            """
            SELECT request
            FROM InformationRequest request
            WHERE request.exchangeId = :exchangeId
            ORDER BY request.createdAt, request.id
            """.trimIndent(),
            InformationRequest::class.java,
        )
            .setParameter("exchangeId", exchangeId)
            .resultList

    fun findForIds(requestIds: Collection<UUID>): List<InformationRequest>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT request FROM InformationRequest request WHERE request.id IN :requestIds",
            InformationRequest::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findOperationsQueueIds(criteria: InformationRequestOperationsCriteria, limit: Int?, offset: Int): List<UUID> {
        val query = entityManager.createNativeQuery(
            """
            SELECT request.id
            ${operationsQueueScope(criteria)}
            ORDER BY (SELECT MIN(clock.due_at)
                      FROM information_request_clock clock
                      WHERE clock.information_request_id = request.id AND clock.state = 'RUNNING') NULLS LAST,
                     request.created_at,
                     request.id
            """.trimIndent(),
        )
        bindOperationsQueue(query, criteria)
        query.firstResult = offset
        limit?.let { query.maxResults = it }
        return query.resultList.map { it as UUID }
    }

    fun countOperationsQueue(criteria: InformationRequestOperationsCriteria): Int {
        val query = entityManager.createNativeQuery("SELECT count(*) ${operationsQueueScope(criteria)}")
        bindOperationsQueue(query, criteria)
        return (query.singleResult as Number).toInt()
    }

    private fun operationsQueueScope(criteria: InformationRequestOperationsCriteria): String =
        listOfNotNull(
            """
            FROM information_request request
                     JOIN information_request_template_version template_version
                          ON template_version.id = request.template_version_id
                     JOIN information_request_template_definition definition
                          ON definition.id = template_version.template_definition_id
            WHERE request.owner_type = :ownerType
            """.trimIndent(),
            when (criteria.ownerType) {
                InformationRequestOwnerType.ORGANIZATION -> "AND request.owner_organization_id = :ownerId"
                InformationRequestOwnerType.USER -> "AND request.owner_user_id = :ownerId"
            },
            "AND request.state IN (:states)".takeIf { criteria.states.isNotEmpty() },
            "AND request.exchange_id = :exchangeId".takeIf { criteria.exchangeId != null },
            """
            AND (LOWER(definition.display_name) LIKE :titlePattern ESCAPE '\'
                OR CAST(request.id AS text) LIKE :idPattern ESCAPE '\')
            """.trimIndent().takeIf { searchTerm(criteria) != null },
            """
            AND EXISTS (SELECT 1
                        FROM information_request_party party
                        WHERE party.information_request_id = request.id
                          AND party.active = TRUE
                          AND party.principal_kind IS NOT NULL
                          AND party.principal_id = :assigneeId)
            """.trimIndent().takeIf { criteria.assigneeId != null },
        ).joinToString("\n")

    private fun bindOperationsQueue(query: jakarta.persistence.Query, criteria: InformationRequestOperationsCriteria) {
        query.setParameter("ownerType", criteria.ownerType.name)
        query.setParameter("ownerId", criteria.ownerId)
        if (criteria.states.isNotEmpty()) query.setParameter("states", criteria.states.map { it.name })
        criteria.exchangeId?.let { query.setParameter("exchangeId", it) }
        searchTerm(criteria)?.let { term ->
            val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            query.setParameter("titlePattern", "%$escaped%")
            query.setParameter("idPattern", "$escaped%")
        }
        criteria.assigneeId?.let { query.setParameter("assigneeId", it) }
    }

    private fun searchTerm(criteria: InformationRequestOperationsCriteria): String? =
        criteria.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun existsForOwner(ownerType: InformationRequestOwnerType, ownerId: UUID): Boolean
    {
        val ownerClause = when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> "request.ownerOrganizationId = :ownerId"
            InformationRequestOwnerType.USER -> "request.ownerUserId = :ownerId"
        }
        return entityManager.createQuery(
            "SELECT COUNT(request) FROM InformationRequest request WHERE request.ownerType = :ownerType AND $ownerClause",
            java.lang.Long::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .singleResult
            .toLong() > 0
    }

    fun countOpenForOwner(ownerType: InformationRequestOwnerType, ownerId: UUID): Long
    {
        val ownerClause = when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> "request.ownerOrganizationId = :ownerId"
            InformationRequestOwnerType.USER -> "request.ownerUserId = :ownerId"
        }
        return entityManager.createQuery(
            "SELECT COUNT(request) FROM InformationRequest request " +
                    "WHERE request.ownerType = :ownerType AND $ownerClause AND request.state IN :openStates",
            java.lang.Long::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setParameter("openStates", InformationRequestState.entries.filterNot { it.isTerminal })
            .singleResult
            .toLong()
    }

    fun findDisposalCandidateIds(
        ownerType: InformationRequestOwnerType,
        ownerId: UUID,
        finishedBefore: Timestamp,
        after: UUID?,
        limit: Int
    ): List<UUID>
    {
        val ownerColumn = when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> "request.owner_organization_id"
            InformationRequestOwnerType.USER -> "request.owner_user_id"
        }
        val cursorClause = if (after != null) "AND request.id > :after" else ""
        val query = entityManager.createNativeQuery(
            """
            SELECT request.id
            FROM information_request request
            WHERE request.owner_type = :ownerType
              AND $ownerColumn = :ownerId
              AND request.state IN (:finished)
              AND COALESCE(request.closed_at, request.cancelled_at, request.superseded_at, request.expired_at) < :finishedBefore
              AND NOT EXISTS (SELECT 1
                              FROM record_disposal_claim claim
                              WHERE claim.resource_type = 'INFORMATION_REQUEST'
                                AND claim.resource_id = request.id)
              $cursorClause
            ORDER BY request.id
            LIMIT :limit
            """.trimIndent(),
        )
            .setParameter("ownerType", ownerType.name)
            .setParameter("ownerId", ownerId)
            .setParameter("finished", InformationRequestState.entries.filter { it.isTerminal }.map { it.name })
            .setParameter("finishedBefore", finishedBefore)
            .setParameter("limit", limit)
        after?.let { query.setParameter("after", it) }
        return query.resultList.map { it as UUID }
    }

    private companion object {
        const val SHARE_EXCHANGE = "SELECT id FROM exchange WHERE id = :id FOR SHARE"
        const val SHARE_REQUEST = "SELECT id FROM information_request WHERE id = :id FOR SHARE"
        const val LOCK_EXCHANGE = "SELECT id FROM exchange WHERE id = :id FOR UPDATE"
        const val LOCK_REQUEST = "SELECT id FROM information_request WHERE id = :id FOR UPDATE"
    }
}
