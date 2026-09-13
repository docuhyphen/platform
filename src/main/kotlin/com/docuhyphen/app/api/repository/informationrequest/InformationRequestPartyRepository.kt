package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestPartyRepository :
    BaseRepository<InformationRequestParty>(InformationRequestParty::class.java)
{
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

    fun findByShareId(shareId: UUID): InformationRequestParty? =
        entityManager.createQuery(
            """
            SELECT party
            FROM InformationRequestParty party
            WHERE party.shareId = :shareId
            """.trimIndent(),
            InformationRequestParty::class.java,
        )
            .setParameter("shareId", shareId)
            .resultList
            .firstOrNull()

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
