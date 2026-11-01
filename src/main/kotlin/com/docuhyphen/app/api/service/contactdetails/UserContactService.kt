package com.docuhyphen.app.api.service.contactdetails

import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserContact
import com.docuhyphen.app.api.repository.contactdetails.UserContactRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class UserContactService @Inject constructor(
    private val userContactRepository: UserContactRepository,
    private val appUserRepository: AppUserRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(UserContactService::class.java)
    }

    @Transactional
    fun recordMutualOnAccept(initiator: AppUser, recipient: AppUser, exchangeId: UUID)
    {
        val effectiveRecipient = if (!recipient.isTemporary) recipient
        else recipient.email.normalizeEmailOrNull()
            ?.let { appUserRepository.findActiveByEmail(it) }
            ?: recipient

        runCatching { recordOneWay(initiator, effectiveRecipient, exchangeId) }
            .onFailure { logger.warn("Failed to record initiator-side contact for session={}", exchangeId, it) }

        if (effectiveRecipient.isTemporary)
        {
            return
        }

        runCatching { recordOneWay(effectiveRecipient, initiator, exchangeId) }
            .onFailure { logger.warn("Failed to record recipient-side contact for session={}", exchangeId, it) }
    }

    @Transactional
    fun recordOneWayFromSignupMerge(owner: AppUser, contact: AppUser, exchangeId: UUID)
    {
        runCatching { recordOneWay(owner, contact, exchangeId) }
            .onFailure { logger.warn("Failed to seed contact on signup merge for session={}", exchangeId, it) }
    }

    @Transactional
    internal fun recordOneWay(owner: AppUser, contact: AppUser, exchangeId: UUID)
    {
        val email = contact.email.normalizeEmailOrNull()
            ?: throw IllegalArgumentException("Contact has no email")
        val now = Timestamp.from(Instant.now())

        val existing = userContactRepository.findByOwnerAndEmail(owner.id, email)
        if (existing == null)
        {
            val row = UserContact().apply {
                this.ownerAppUserId = owner.id
                this.contactAppUserId = if (contact.isTemporary) null else contact.id
                this.contactEmail = email
                this.contactFirstName = contact.person?.firstName
                this.contactLastName = contact.person?.lastName
                this.firstSharedAt = now
                this.lastSharedAt = now
                this.shareCount = 1
                this.lastSessionId = exchangeId
            }
            userContactRepository.save(row)
        }
        else
        {
            existing.lastSharedAt = now
            existing.shareCount = existing.shareCount + 1
            existing.lastSessionId = exchangeId
            if (existing.contactAppUserId == null && !contact.isTemporary)
            {
                existing.contactAppUserId = contact.id
            }
            contact.person?.firstName?.let { existing.contactFirstName = it }
            contact.person?.lastName?.let { existing.contactLastName = it }
            userContactRepository.update(existing)
        }
    }

    fun searchContacts(ownerAppUserId: UUID, query: String, limit: Int): List<UserContact>
    {
        return userContactRepository.searchByOwner(ownerAppUserId, query, limit)
    }

    fun recentContacts(ownerAppUserId: UUID, limit: Int): List<UserContact>
    {
        return userContactRepository.findRecentByOwner(ownerAppUserId, limit)
    }

    fun findContactIdsWithAvatar(contactAppUserIds: Collection<UUID>): Set<UUID>
    {
        return appUserRepository.findIdsWithAvatar(contactAppUserIds)
    }

    fun backfillContactAppUserIdForEmail(contactEmail: String, newContactAppUserId: UUID): Int
    {
        val normalized = contactEmail.normalizeEmailOrNull() ?: return 0
        return userContactRepository.backfillContactAppUserId(normalized, newContactAppUserId)
    }
}
