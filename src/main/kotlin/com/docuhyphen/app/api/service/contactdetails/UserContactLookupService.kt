package com.docuhyphen.app.api.service.contactdetails

import com.docuhyphen.app.api.model.UserContactDtoMapper
import com.docuhyphen.app.api.model.dto.UserContactDto
import com.docuhyphen.app.api.model.entity.UserContact
import com.docuhyphen.app.api.service.auth.DirectoryLookupGuardService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class UserContactLookupService @Inject constructor(
    private val userContactService: UserContactService,
    private val directoryLookupGuardService: DirectoryLookupGuardService,
)
{
    companion object
    {
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 50
    }

    fun searchContacts(ownerAppUserId: UUID, query: String, requestedLimit: Int?): List<UserContactDto>
    {
        val results = userContactService.searchContacts(ownerAppUserId, query, effectiveLimit(requestedLimit))
        return contactViews(results)
    }

    fun recentContacts(ownerAppUserId: UUID, requestedLimit: Int?): List<UserContactDto>
    {
        val results = userContactService.recentContacts(ownerAppUserId, effectiveLimit(requestedLimit))
        return contactViews(results)
    }

    private fun effectiveLimit(requestedLimit: Int?): Int = (requestedLimit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

    private fun contactViews(results: List<UserContact>): List<UserContactDto>
    {
        val capped = directoryLookupGuardService.capResults(results)
        val idsWithAvatar = userContactService.findContactIdsWithAvatar(capped.mapNotNull { it.contactAppUserId })
        return capped.map { UserContactDtoMapper.toDto(it, idsWithAvatar) }
    }
}
