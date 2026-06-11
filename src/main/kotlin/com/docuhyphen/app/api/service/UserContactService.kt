package com.docuhyphen.app.api.service

import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserContact
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.UserContactRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Personal contacts list per AppUser.
 *
 * Writes are reciprocity-gated: a contact entry is only created when a exchange
 * recipient *accepts*. Initiation alone never produces a contact entry, that's the spam-
 * resistance invariant. Call [recordMutualOnAccept] from the accept path only.
 *
 * Reads are exposed read-only via [searchContacts] and [recentContacts] and used by the
 * recipient picker. Read endpoints are guarded by DirectoryLookupGuardService.
 */
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

    /**
     * Records the mutual contact relationship between [initiator] and [recipient] after
     * the recipient accepts a exchange.
     *
     * - The initiator-side entry (owner=initiator, contact=recipient) is always written.
     * - The recipient-side entry is **skipped when the recipient is still a temp AppUser**
     *   ([AppUser.isTemporary] == true). It will be seeded by the signup-merge step when
     *   that user creates a real account.
     *
     * If [recipient] is a temp placeholder but a real (non-temporary) account exists with
     * the same email (i.e. they accepted via the no-auth flow while already registered),
     * the real account is used so that [contactAppUserId] is populated immediately and
     * the contact becomes usable for group membership without waiting for a signup-merge.
     *
     * Wrapped in runCatching at each side so a contact-write failure can never roll back
     * the accept transaction. Idempotent: a repeated call bumps shareCount and lastSharedAt.
     */
    @Transactional
    fun recordMutualOnAccept(initiator: AppUser, recipient: AppUser, exchangeId: UUID)
    {
        // If the recipient accepted via the no-auth flow they may be a temp placeholder even
        // when a real registered account exists for that email.  Resolve to the real account
        // so contactAppUserId is set correctly from the start.
        val effectiveRecipient = if (!recipient.isTemporary) recipient
        else recipient.email.normalizeEmailOrNull()
            ?.let { appUserRepository.findActiveByEmail(it) }
            ?: recipient

        runCatching { recordOneWay(initiator, effectiveRecipient, exchangeId) }
            .onFailure { logger.warn("Failed to record initiator-side contact for session={}", exchangeId, it) }

        if (effectiveRecipient.isTemporary)
        {
            // Still a temp user (no real account found yet) — recipient-side entry
            // deferred until they sign up via seedContactsAfterSignup.
            return
        }

        runCatching { recordOneWay(effectiveRecipient, initiator, exchangeId) }
            .onFailure { logger.warn("Failed to record recipient-side contact for session={}", exchangeId, it) }
    }

    /**
     * Used by the signup-merge step to seed the recipient-side contact entry for past
     * accepted sessions where the recipient was a temp AppUser at the time of accept.
     */
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
            // Bump usage stats and refresh the cached name/id in case they've changed since.
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

    /**
     * Backfills `contactAppUserId` on every row across the platform whose email matches
     * [contactEmail]. Used during the temp-user signup-merge so the picker can join to
     * the real account afterward.
     */
    fun backfillContactAppUserIdForEmail(contactEmail: String, newContactAppUserId: UUID): Int
    {
        val normalized = contactEmail.normalizeEmailOrNull() ?: return 0
        return userContactRepository.backfillContactAppUserId(normalized, newContactAppUserId)
    }
}
