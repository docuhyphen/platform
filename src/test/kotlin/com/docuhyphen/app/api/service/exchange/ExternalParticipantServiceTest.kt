package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class ExternalParticipantServiceTest
{
    @Test
    fun `first organization participant creation normalizes email and records owner`()
    {
        val repository = mock<ExternalParticipantRepository>()
        whenever(repository.findByOwnerAndEmail(anyOrNull(), anyOrNull(), any())).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }
        val service = ExternalParticipantService(repository)
        val owner = ExternalParticipantOwner.Organization(UUID.randomUUID())

        val participant = service.findOrCreate(owner, " Respondent@Example.TEST ", "Respondent")

        assertEquals(owner.organizationId, participant.ownerOrganizationId)
        assertEquals(null, participant.ownerAppUserId)
        assertEquals("respondent@example.test", participant.emailLower)
        assertEquals("Respondent@Example.TEST", participant.email)
        verify(repository).save(participant)
    }

    @Test
    fun `same email can exist separately for organization and personal owners`()
    {
        val repository = mock<ExternalParticipantRepository>()
        whenever(repository.findByOwnerAndEmail(anyOrNull(), anyOrNull(), any())).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }
        val service = ExternalParticipantService(repository)
        val organizationOwner = ExternalParticipantOwner.Organization(UUID.randomUUID())
        val personalOwner = ExternalParticipantOwner.Personal(UUID.randomUUID())

        val organizationParticipant = service.findOrCreate(organizationOwner, "actor@example.test", null)
        val personalParticipant = service.findOrCreate(personalOwner, "actor@example.test", null)

        assertNotEquals(organizationParticipant.id, personalParticipant.id)
        assertEquals(organizationOwner.organizationId, organizationParticipant.ownerOrganizationId)
        assertEquals(personalOwner.userId, personalParticipant.ownerAppUserId)
    }

    @Test
    fun `same tenant collision returns the existing active participant`()
    {
        val owner = ExternalParticipantOwner.Personal(UUID.randomUUID())
        val existing = ExternalParticipant().apply {
            ownerAppUserId = owner.userId
            email = "actor@example.test"
            emailLower = "actor@example.test"
        }
        val repository = mock<ExternalParticipantRepository>()
        whenever(repository.findByOwnerAndEmail(null, owner.userId, "actor@example.test")).thenReturn(existing)
        val service = ExternalParticipantService(repository)

        val participant = service.findOrCreate(owner, "actor@example.test", "Ignored")

        assertSame(existing, participant)
    }

    @Test
    fun `contact verification is owner scoped and records the verification instant`()
    {
        val owner = ExternalParticipantOwner.Organization(UUID.randomUUID())
        val participant = ExternalParticipant().apply {
            ownerOrganizationId = owner.organizationId
            email = "actor@example.test"
            emailLower = "actor@example.test"
        }
        val verifiedAt = Instant.parse("2026-09-03T10:15:30Z")
        val repository = mock<ExternalParticipantRepository>()
        whenever(repository.findById(participant.id)).thenReturn(participant)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
        val service = ExternalParticipantService(repository)

        val verified = service.verifyContact(owner, participant.id, verifiedAt)

        assertNotNull(verified.emailVerifiedAt)
        assertEquals(verifiedAt, verified.emailVerifiedAt!!.toInstant())
        verify(repository).update(participant)
    }
}
