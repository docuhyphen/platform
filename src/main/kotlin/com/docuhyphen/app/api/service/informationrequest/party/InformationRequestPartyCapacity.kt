package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestPartyCapacity @Inject constructor(
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val executionUsageReservationService: InformationRequestExecutionUsageReservationService,
) {
    fun <T> withReservedSlot(
        request: InformationRequest,
        party: InformationRequestParty,
        store: (consumeSlot: () -> Unit) -> T,
    ): T {
        val reservation = reserveIfIssued(request, party)
        var consumed = false
        try {
            return store {
                reservation?.let { executionUsageReservationService.consume(it.id) }
                consumed = reservation != null
            }
        } catch (exception: RuntimeException) {
            compensate(reservation, consumed)
            throw exception
        }
    }

    fun returnSlotIfIssued(request: InformationRequest, party: InformationRequestParty) {
        val grant = executionGrantService.findForRequest(request.id) ?: return
        executionUsageReservationService.returnCapacity(
            grant.id,
            RequestExecutionUsageKind.ACTING_PARTY,
            reservationKey(party.id),
        )
    }

    private fun reserveIfIssued(
        request: InformationRequest,
        party: InformationRequestParty,
    ): RequestExecutionUsageReservation? {
        val grant = executionGrantService.findForRequest(request.id) ?: return null
        return executionUsageReservationService.reserve(
            grant.id,
            RequestExecutionUsageKind.ACTING_PARTY,
            reservationKey(party.id),
            1L,
        )
    }

    private fun compensate(reservation: RequestExecutionUsageReservation?, consumed: Boolean) {
        if (reservation == null) {
            return
        }
        if (consumed) {
            runCatching { executionUsageReservationService.rollback(reservation.id) }
        } else {
            runCatching { executionUsageReservationService.release(reservation.id) }
        }
    }

    private fun reservationKey(partyId: UUID): String = "information_request.party|$partyId"
}
