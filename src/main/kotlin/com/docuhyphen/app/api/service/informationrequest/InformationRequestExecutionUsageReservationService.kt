package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservationStatus
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionGrantRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionUsageReservationRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The capacity ledger against a single [RequestExecutionGrant]: how much of its frozen recipient,
 * upload, or storage allowance is actually spoken for right now.
 *
 * A reservation is provisional until [consume] finalizes it, or [release] gives it back before
 * that happens. [rollback] gives capacity back after it was already consumed, for the rarer case
 * where finalized usage is later undone (a party removed, an upload deleted) without disturbing
 * anyone else's reservation. Concurrent [reserve] calls against the same grant are serialized by
 * locking the grant row itself, the same pattern [com.docuhyphen.app.api.service.organization.OrganizationSeatGuard]
 * uses to serialize seat activations, so two callers racing for the last unit of capacity cannot
 * both succeed.
 */
@ApplicationScoped
class InformationRequestExecutionUsageReservationService @Inject constructor(
    private val grantRepository: RequestExecutionGrantRepository,
    private val reservationRepository: RequestExecutionUsageReservationRepository,
)
{
    /**
     * Idempotent by [reservationKey]: a retry of the same logical reservation returns the row
     * already created for it, whatever its current status, rather than reserving twice or
     * re-checking capacity that was already granted.
     */
    @Transactional
    fun reserve(
        grantId: UUID,
        usageKind: RequestExecutionUsageKind,
        reservationKey: String,
        quantity: Long,
    ): RequestExecutionUsageReservation
    {
        require(quantity > 0) { "Reservation quantity must be positive" }

        reservationRepository.findByGrantIdAndUsageKindAndKey(grantId, usageKind, reservationKey)
            ?.let { return it }

        val grant = grantRepository.findByIdForUpdate(grantId)
            ?: throw IllegalStateException("Execution grant $grantId not found")

        val cap = capacityFor(grant, usageKind)
        if (cap != null)
        {
            val activeUsage = reservationRepository.sumActiveQuantity(grantId, usageKind)
            if (activeUsage + quantity > cap)
            {
                throw RequestExecutionUsageExhaustedException(
                    grantId = grantId,
                    usageKind = usageKind,
                    cap = cap,
                    activeUsage = activeUsage,
                    requested = quantity,
                )
            }
        }

        val now = Timestamp.from(Instant.now())
        val reservation = RequestExecutionUsageReservation().apply {
            this.grantId = grantId
            this.usageKind = usageKind.name
            this.reservationKey = reservationKey
            this.quantity = quantity
            this.status = RequestExecutionUsageReservationStatus.RESERVED.name
            this.reservedAt = now
            this.createdAt = now
        }
        return reservationRepository.save(reservation)
    }

    /** Idempotent: consuming an already-consumed reservation returns it unchanged. */
    @Transactional
    fun consume(reservationId: UUID): RequestExecutionUsageReservation
    {
        val reservation = requireReservation(reservationId)
        return when (statusOf(reservation))
        {
            RequestExecutionUsageReservationStatus.CONSUMED -> reservation
            RequestExecutionUsageReservationStatus.RESERVED ->
            {
                reservation.status = RequestExecutionUsageReservationStatus.CONSUMED.name
                reservation.consumedAt = Timestamp.from(Instant.now())
                reservationRepository.update(reservation)
            }
            else -> throw invalidTransition(reservationId, reservation, "consumed")
        }
    }

    /** Idempotent: releasing an already-released reservation returns it unchanged. */
    @Transactional
    fun release(reservationId: UUID): RequestExecutionUsageReservation
    {
        val reservation = requireReservation(reservationId)
        return when (statusOf(reservation))
        {
            RequestExecutionUsageReservationStatus.RELEASED -> reservation
            RequestExecutionUsageReservationStatus.RESERVED ->
            {
                reservation.status = RequestExecutionUsageReservationStatus.RELEASED.name
                reservation.releasedAt = Timestamp.from(Instant.now())
                reservationRepository.update(reservation)
            }
            else -> throw invalidTransition(reservationId, reservation, "released")
        }
    }

    /** Idempotent: rolling back an already-rolled-back reservation returns it unchanged. */
    @Transactional
    fun rollback(reservationId: UUID): RequestExecutionUsageReservation
    {
        val reservation = requireReservation(reservationId)
        return when (statusOf(reservation))
        {
            RequestExecutionUsageReservationStatus.ROLLED_BACK -> reservation
            RequestExecutionUsageReservationStatus.CONSUMED ->
            {
                reservation.status = RequestExecutionUsageReservationStatus.ROLLED_BACK.name
                reservation.rolledBackAt = Timestamp.from(Instant.now())
                reservationRepository.update(reservation)
            }
            else -> throw invalidTransition(reservationId, reservation, "rolled back")
        }
    }

    private fun requireReservation(reservationId: UUID): RequestExecutionUsageReservation =
        reservationRepository.findByIdForUpdate(reservationId)
            ?: throw IllegalStateException("Usage reservation $reservationId not found")

    private fun invalidTransition(
        reservationId: UUID,
        reservation: RequestExecutionUsageReservation,
        attempted: String,
    ) = IllegalStateException(
        "Usage reservation $reservationId cannot be $attempted from ${reservation.status}",
    )

    private fun statusOf(reservation: RequestExecutionUsageReservation) =
        RequestExecutionUsageReservationStatus.valueOf(reservation.status)

    private fun capacityFor(grant: RequestExecutionGrant, usageKind: RequestExecutionUsageKind): Long? =
        when (usageKind)
        {
            RequestExecutionUsageKind.ADDITIONAL_RECIPIENT -> grant.additionalRecipientCap
        }
}
