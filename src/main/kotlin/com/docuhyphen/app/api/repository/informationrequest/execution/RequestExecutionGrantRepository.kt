package com.docuhyphen.app.api.repository.informationrequest.execution

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class RequestExecutionGrantRepository :
    BaseRepository<RequestExecutionGrant>(RequestExecutionGrant::class.java)
{
    fun findByRequestId(requestId: UUID): RequestExecutionGrant? =
        entityManager.createQuery(
            """
            SELECT grant_
            FROM RequestExecutionGrant grant_
            WHERE grant_.requestId = :requestId
            """.trimIndent(),
            RequestExecutionGrant::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
            .firstOrNull()

    fun insertNow(grant: RequestExecutionGrant): RequestExecutionGrant
    {
        entityManager.persist(grant)
        entityManager.flush()
        return grant
    }

    fun committedEvidenceBytes(ownerType: SubscriptionOwnerType, ownerId: UUID): Long =
        (
                entityManager.createNativeQuery(
                    """
                SELECT COALESCE(SUM(
                    CASE WHEN request.state IN ($OPEN_STATES)
                        THEN COALESCE(grant_row.evidence_byte_allowance, stored.bytes)
                        ELSE stored.bytes
                    END), 0)
                FROM request_execution_grant grant_row
                JOIN information_request request ON request.id = grant_row.request_id
                CROSS JOIN LATERAL (
                    SELECT COALESCE(SUM(content.content_length), 0) AS bytes
                    FROM information_request_evidence_version version
                    JOIN document_version content ON content.id = version.document_version_id
                    WHERE version.information_request_id = grant_row.request_id
                ) stored
                WHERE grant_row.owner_type = :ownerType
                  AND COALESCE(grant_row.owner_organization_id, grant_row.owner_user_id) = :ownerId
                """.trimIndent(),
                )
                    .setParameter("ownerType", ownerType.name)
                    .setParameter("ownerId", ownerId)
                    .singleResult as Number
                ).toLong()

    private companion object
    {
        val OPEN_STATES = InformationRequestState.entries
            .filterNot { it.isTerminal }
            .joinToString(", ") { "'${it.name}'" }
    }
}
