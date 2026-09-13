package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

sealed class ExternalParticipantOwner
{
    data class Organization(val organizationId: UUID) : ExternalParticipantOwner()
    data class Personal(val userId: UUID) : ExternalParticipantOwner()
}

@ApplicationScoped
class ExternalParticipantService @Inject constructor(
    private val repository: ExternalParticipantRepository,
)
{
    @Transactional
    fun findOrCreate(
        owner: ExternalParticipantOwner,
        email: String,
        displayName: String?,
    ): ExternalParticipant
    {
        val normalized = normalizeEmail(email)
        repository.findByOwnerAndEmail(
            ownerOrganizationId = owner.organizationIdOrNull(),
            ownerAppUserId = owner.userIdOrNull(),
            email = normalized,
        )?.let { existing ->
            if (!existing.isActive)
            {
                existing.isActive = true
                return repository.update(existing)
            }
            return existing
        }

        return repository.save(
            ExternalParticipant().apply {
                id = UUID.randomUUID()
                ownerOrganizationId = owner.organizationIdOrNull()
                ownerAppUserId = owner.userIdOrNull()
                this.email = normalized
                emailLower = normalized.lowercase()
                this.displayName = displayName?.trim()?.takeIf { it.isNotBlank() }
                isActive = true
                createdDate = Timestamp.from(Instant.now())
            },
        )
    }

    @Transactional
    fun verifyContact(
        owner: ExternalParticipantOwner,
        participantId: UUID,
        verifiedAt: Instant = Instant.now(),
    ): ExternalParticipant
    {
        val participant = repository.findById(participantId)
            ?: throw IllegalArgumentException("External participant not found")
        requireOwnedBy(participant, owner)
        val timestamp = Timestamp.from(verifiedAt)
        participant.emailVerifiedAt = timestamp
        participant.lastSeenAt = timestamp
        return repository.update(participant)
    }

    fun findOwned(participantId: UUID, owner: ExternalParticipantOwner): ExternalParticipant?
    {
        val participant = repository.findById(participantId) ?: return null
        return participant.takeIf { isOwnedBy(it, owner) }
    }

    private fun normalizeEmail(email: String): String
    {
        val normalized = email.trim()
        require(normalized.isNotBlank()) { "External participant email is required" }
        require(normalized.contains("@")) { "External participant email must be an email address" }
        return normalized
    }

    private fun requireOwnedBy(participant: ExternalParticipant, owner: ExternalParticipantOwner)
    {
        require(isOwnedBy(participant, owner)) { "External participant belongs to a different owner" }
    }

    private fun isOwnedBy(participant: ExternalParticipant, owner: ExternalParticipantOwner): Boolean =
        participant.ownerOrganizationId == owner.organizationIdOrNull() &&
            participant.ownerAppUserId == owner.userIdOrNull()

    private fun ExternalParticipantOwner.organizationIdOrNull(): UUID? = when (this)
    {
        is ExternalParticipantOwner.Organization -> organizationId
        is ExternalParticipantOwner.Personal -> null
    }

    private fun ExternalParticipantOwner.userIdOrNull(): UUID? = when (this)
    {
        is ExternalParticipantOwner.Organization -> null
        is ExternalParticipantOwner.Personal -> userId
    }
}
