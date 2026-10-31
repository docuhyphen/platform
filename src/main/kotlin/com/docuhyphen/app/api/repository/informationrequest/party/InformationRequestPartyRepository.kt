package com.docuhyphen.app.api.repository.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.*

@ApplicationScoped
class InformationRequestPartyRepository :
    BaseRepository<InformationRequestParty>(InformationRequestParty::class.java)
{
    fun findByIdForShare(id: UUID): InformationRequestParty? {
        val party = entityManager.find(InformationRequestParty::class.java, id) ?: return null
        entityManager.refresh(party, LockModeType.PESSIMISTIC_READ)
        return party
    }

    fun findForRequest(requestId: UUID): List<InformationRequestParty> =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
            ORDER BY party.assignedAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findActiveForRequests(requestIds: Collection<UUID>): List<InformationRequestParty>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.informationRequestId IN :requestIds
              AND party.active = TRUE
            ORDER BY party.roleKey, party.createdAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findActiveForRequest(requestId: UUID): List<InformationRequestParty> =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
              AND party.active = TRUE
            ORDER BY party.roleKey, party.createdAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findActiveForRequestRole(
        requestId: UUID,
        roleKey: InformationRequestShareRoleKey,
    ): List<InformationRequestParty> =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
              AND party.roleKey = :roleKey
              AND party.active = TRUE
            ORDER BY party.createdAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("roleKey", roleKey)
            .resultList

    fun findActiveForExchangeRecipient(exchangeRecipientId: UUID): List<InformationRequestParty> =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.exchangeRecipientId = :exchangeRecipientId
              AND party.active = TRUE
            ORDER BY party.createdAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("exchangeRecipientId", exchangeRecipientId)
            .resultList

    fun existsForExchangeRecipient(exchangeRecipientId: UUID): Boolean =
        entityManager.createQuery(
            """
            SELECT COUNT(party)
            FROM InformationRequestParty party
            WHERE party.exchangeRecipientId = :exchangeRecipientId
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("exchangeRecipientId", exchangeRecipientId)
            .singleResult
            .toLong() > 0

    fun findByShareId(shareId: UUID): InformationRequestParty? =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.shareId = :shareId
            ORDER BY CASE WHEN party.active = TRUE THEN 0 ELSE 1 END, party.assignedAt DESC, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("shareId", shareId)
            .resultList
            .firstOrNull()

    fun existsOtherActivePartyHoldingShare(requestId: UUID, shareId: UUID, partyId: UUID): Boolean =
        entityManager.createQuery(
            """
            SELECT COUNT(party)
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
              AND party.active = TRUE
              AND party.id <> :partyId
              AND (party.shareId = :shareId OR party.linkedUserShareId = :shareId)
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("shareId", shareId)
            .setParameter("partyId", partyId)
            .singleResult
            .toLong() > 0

    fun existsActiveAssignment(
        requestId: UUID,
        roleKey: InformationRequestShareRoleKey,
        principalKind: PrincipalKind,
        principalId: UUID,
        excludingPartyId: UUID,
    ): Boolean =
        entityManager.createQuery(
            """
            SELECT COUNT(party)
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
              AND party.roleKey = :roleKey
              AND party.principalKind = :principalKind
              AND party.principalId = :principalId
              AND party.active = TRUE
              AND party.id <> :excludingPartyId
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("roleKey", roleKey)
            .setParameter("principalKind", principalKind)
            .setParameter("principalId", principalId)
            .setParameter("excludingPartyId", excludingPartyId)
            .singleResult
            .toLong() > 0

    fun existsActiveSubject(requestId: UUID, subjectIdentityRefId: UUID): Boolean =
        entityManager.createQuery(
            """
            SELECT COUNT(party)
            FROM InformationRequestParty party
            WHERE party.informationRequestId = :requestId
              AND party.roleKey = :roleKey
              AND party.subjectIdentityRefId = :subjectIdentityRefId
              AND party.active = TRUE
            """.trimIndent(),
            java.lang.Long::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("roleKey", InformationRequestShareRoleKey.SUBJECT)
            .setParameter("subjectIdentityRefId", subjectIdentityRefId)
            .singleResult
            .toLong() > 0

    fun findIdsHeldByGroups(requestIds: Collection<UUID>): Set<UUID> {
        if (requestIds.isEmpty()) return emptySet()
        return entityManager.createQuery(
            """
            SELECT party.id
            FROM InformationRequestParty party
            WHERE party.informationRequestId IN :requestIds
              AND party.principalKind = :groupKind
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("requestIds", requestIds)
            .setParameter("groupKind", PrincipalKind.PRINCIPAL_GROUP)
            .resultList
            .toSet()
    }

    fun findActiveForPrincipal(
        principalKind: PrincipalKind,
        principalId: UUID,
    ): List<InformationRequestParty> =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.principalKind = :principalKind
              AND party.principalId = :principalId
              AND party.active = TRUE
            ORDER BY party.createdAt, party.id
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("principalKind", principalKind)
            .setParameter("principalId", principalId)
            .resultList
}
