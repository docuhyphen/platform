package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPartyCapacityTest {
    private val grants = mock<InformationRequestExecutionGrantService>()
    private val reservations = mock<InformationRequestExecutionUsageReservationService>()
    private val capacity = InformationRequestPartyCapacity(grants, reservations)
    private val request = InformationRequest().apply { id = UUID.randomUUID() }
    private val party = InformationRequestParty().apply { informationRequestId = request.id }
    private val grant = RequestExecutionGrant().apply { requestId = request.id }
    private val reservation = RequestExecutionUsageReservation()
    private val reservationKey = "information_request.party|${party.id}"

    @Test
    fun `a request without a grant stores the party without reserving a slot`() {
        val stored = capacity.withReservedSlot(request, party) { consumeSlot ->
            consumeSlot()
            "stored"
        }

        assertEquals("stored", stored)
        verifyNoInteractions(reservations)
    }

    @Test
    fun `an issued request reserves one acting party slot and consumes it once the party is stored`() {
        issued()

        capacity.withReservedSlot(request, party) { consumeSlot -> consumeSlot() }

        val order = inOrder(reservations)
        order.verify(reservations).reserve(grant.id, RequestExecutionUsageKind.ACTING_PARTY, reservationKey, 1L)
        order.verify(reservations).consume(reservation.id)
        verify(reservations, never()).release(any())
        verify(reservations, never()).rollback(any())
    }

    @Test
    fun `a failure before the slot is consumed releases the reservation`() {
        issued()

        assertThrows<IllegalStateException> {
            capacity.withReservedSlot(request, party) { throw IllegalStateException("share refused") }
        }

        verify(reservations).release(reservation.id)
        verify(reservations, never()).rollback(any())
    }

    @Test
    fun `a failure after the slot is consumed rolls the consumption back`() {
        issued()

        assertThrows<IllegalStateException> {
            capacity.withReservedSlot(request, party) { consumeSlot ->
                consumeSlot()
                throw IllegalStateException("history refused")
            }
        }

        verify(reservations).rollback(reservation.id)
        verify(reservations, never()).release(any())
    }

    @Test
    fun `revoking a party returns its slot only on an issued request`() {
        capacity.returnSlotIfIssued(request, party)
        verifyNoInteractions(reservations)

        issued()
        capacity.returnSlotIfIssued(request, party)

        verify(reservations).returnCapacity(grant.id, RequestExecutionUsageKind.ACTING_PARTY, reservationKey)
    }

    private fun issued() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant)
        whenever(reservations.reserve(grant.id, RequestExecutionUsageKind.ACTING_PARTY, reservationKey, 1L)).thenReturn(
            reservation
        )
    }
}
