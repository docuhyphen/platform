package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservationStatus
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class RequestExecutionUsageReservationRepository :
    BaseRepository<RequestExecutionUsageReservation>(RequestExecutionUsageReservation::class.java)
{
    fun findByGrantIdAndUsageKindAndKey(
        grantId: UUID,
        usageKind: RequestExecutionUsageKind,
        reservationKey: String,
    ): RequestExecutionUsageReservation? =
        entityManager.createQuery(
            """
            SELECT reservation
            FROM RequestExecutionUsageReservation reservation
            WHERE reservation.grantId = :grantId
              AND reservation.usageKind = :usageKind
              AND reservation.reservationKey = :reservationKey
            """.trimIndent(),
            RequestExecutionUsageReservation::class.java,
        )
            .setParameter("grantId", grantId)
            .setParameter("usageKind", usageKind.name)
            .setParameter("reservationKey", reservationKey)
            .resultList
            .firstOrNull()

    /**
     * Capacity currently spoken for: reservations that are either still provisional (`RESERVED`)
     * or already finalized (`CONSUMED`). `RELEASED` and `ROLLED_BACK` rows have given their
     * capacity back and are excluded.
     */
    fun sumActiveQuantity(grantId: UUID, usageKind: RequestExecutionUsageKind): Long =
        entityManager.createQuery(
            """
            SELECT COALESCE(SUM(reservation.quantity), 0)
            FROM RequestExecutionUsageReservation reservation
            WHERE reservation.grantId = :grantId
              AND reservation.usageKind = :usageKind
              AND reservation.status IN (:activeStatuses)
            """.trimIndent(),
            Long::class.java,
        )
            .setParameter("grantId", grantId)
            .setParameter("usageKind", usageKind.name)
            .setParameter(
                "activeStatuses",
                listOf(
                    RequestExecutionUsageReservationStatus.RESERVED.name,
                    RequestExecutionUsageReservationStatus.CONSUMED.name,
                ),
            )
            .singleResult
}
