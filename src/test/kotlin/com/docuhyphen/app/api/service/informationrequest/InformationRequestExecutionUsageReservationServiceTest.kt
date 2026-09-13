package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionGrantRepository
import com.docuhyphen.app.api.repository.informationrequest.RequestExecutionUsageReservationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestExecutionUsageReservationServiceTest
{
    private val grantRepository: RequestExecutionGrantRepository = mock()
    private val reservationRepository: RequestExecutionUsageReservationRepository = mock()
    private val service = InformationRequestExecutionUsageReservationService(grantRepository, reservationRepository)

    @Test
    fun `reserving capacity within the cap creates a reserved row`()
    {
        val grantId = UUID.randomUUID()
        val grant = grantWithCap(grantId, cap = 4L)
        whenever(reservationRepository.findByGrantIdAndUsageKindAndKey(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-1"))
            .thenReturn(null)
        whenever(grantRepository.findByIdForUpdate(grantId)).thenReturn(grant)
        whenever(reservationRepository.sumActiveQuantity(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT)).thenReturn(2L)
        whenever(reservationRepository.save(any())).thenAnswer { it.getArgument(0) }

        val reservation = service.reserve(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-1", 1L)

        assertEquals(grantId, reservation.grantId)
        assertEquals(RequestExecutionUsageKind.ADDITIONAL_RECIPIENT.name, reservation.usageKind)
        assertEquals("party-1", reservation.reservationKey)
        assertEquals(1L, reservation.quantity)
        assertEquals("RESERVED", reservation.status)
        assertNotNull(reservation.reservedAt)
        assertNull(reservation.consumedAt)
    }

    @Test
    fun `reserving capacity that would exceed the cap is refused`()
    {
        val grantId = UUID.randomUUID()
        val grant = grantWithCap(grantId, cap = 2L)
        whenever(reservationRepository.findByGrantIdAndUsageKindAndKey(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-3"))
            .thenReturn(null)
        whenever(grantRepository.findByIdForUpdate(grantId)).thenReturn(grant)
        whenever(reservationRepository.sumActiveQuantity(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT)).thenReturn(2L)

        val failure = assertThrows<RequestExecutionUsageExhaustedException> {
            service.reserve(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-3", 1L)
        }

        assertEquals(grantId, failure.grantId)
        assertEquals(2L, failure.cap)
        assertEquals(2L, failure.activeUsage)
        assertEquals(1L, failure.requested)
        verify(reservationRepository, never()).save(any())
    }

    @Test
    fun `an uncapped grant never refuses a reservation`()
    {
        val grantId = UUID.randomUUID()
        val grant = grantWithCap(grantId, cap = null)
        whenever(reservationRepository.findByGrantIdAndUsageKindAndKey(any(), any(), any())).thenReturn(null)
        whenever(grantRepository.findByIdForUpdate(grantId)).thenReturn(grant)
        whenever(reservationRepository.save(any())).thenAnswer { it.getArgument(0) }

        val reservation = service.reserve(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-9", 1000L)

        assertEquals(1000L, reservation.quantity)
        verify(reservationRepository, never()).sumActiveQuantity(any(), any())
    }

    @Test
    fun `reserving twice with the same key returns the original reservation without re-checking capacity`()
    {
        val grantId = UUID.randomUUID()
        val existing = RequestExecutionUsageReservation().apply { this.grantId = grantId }
        whenever(reservationRepository.findByGrantIdAndUsageKindAndKey(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-1"))
            .thenReturn(existing)

        val reservation = service.reserve(grantId, RequestExecutionUsageKind.ADDITIONAL_RECIPIENT, "party-1", 1L)

        assertSame(existing, reservation)
        verify(grantRepository, never()).findByIdForUpdate(any())
        verify(reservationRepository, never()).save(any())
    }

    @Test
    fun `a reserved reservation can be consumed and consuming again is a no-op`()
    {
        val reservationId = UUID.randomUUID()
        val reserved = reservationInStatus("RESERVED")
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(reserved)
        whenever(reservationRepository.update(any())).thenAnswer { it.getArgument(0) }

        val consumed = service.consume(reservationId)
        assertEquals("CONSUMED", consumed.status)
        assertNotNull(consumed.consumedAt)

        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(consumed)
        val consumedAgain = service.consume(reservationId)
        assertSame(consumed, consumedAgain)
        verify(reservationRepository, org.mockito.kotlin.times(1)).update(any())
    }

    @Test
    fun `a reserved reservation can be released and releasing again is a no-op`()
    {
        val reservationId = UUID.randomUUID()
        val reserved = reservationInStatus("RESERVED")
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(reserved)
        whenever(reservationRepository.update(any())).thenAnswer { it.getArgument(0) }

        val released = service.release(reservationId)
        assertEquals("RELEASED", released.status)
        assertNotNull(released.releasedAt)

        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(released)
        val releasedAgain = service.release(reservationId)
        assertSame(released, releasedAgain)
    }

    @Test
    fun `a consumed reservation can be rolled back and rolling back again is a no-op`()
    {
        val reservationId = UUID.randomUUID()
        val consumed = reservationInStatus("CONSUMED")
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(consumed)
        whenever(reservationRepository.update(any())).thenAnswer { it.getArgument(0) }

        val rolledBack = service.rollback(reservationId)
        assertEquals("ROLLED_BACK", rolledBack.status)
        assertNotNull(rolledBack.rolledBackAt)

        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(rolledBack)
        val rolledBackAgain = service.rollback(reservationId)
        assertSame(rolledBack, rolledBackAgain)
    }

    @Test
    fun `a released reservation cannot be consumed`()
    {
        val reservationId = UUID.randomUUID()
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(reservationInStatus("RELEASED"))

        assertThrows<IllegalStateException> { service.consume(reservationId) }
    }

    @Test
    fun `a reserved reservation cannot be rolled back directly`()
    {
        val reservationId = UUID.randomUUID()
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(reservationInStatus("RESERVED"))

        assertThrows<IllegalStateException> { service.rollback(reservationId) }
    }

    @Test
    fun `a consumed reservation cannot be released`()
    {
        val reservationId = UUID.randomUUID()
        whenever(reservationRepository.findByIdForUpdate(reservationId)).thenReturn(reservationInStatus("CONSUMED"))

        assertThrows<IllegalStateException> { service.release(reservationId) }
    }

    private fun grantWithCap(grantId: UUID, cap: Long?) = RequestExecutionGrant().apply {
        id = grantId
        additionalRecipientCap = cap
    }

    private fun reservationInStatus(status: String) = RequestExecutionUsageReservation().apply {
        this.status = status
    }
}
