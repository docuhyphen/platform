package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.UserContactDto
import com.docuhyphen.app.api.model.entity.UserContact
import java.util.*

object UserContactDtoMapper
{
    fun toDto(contact: UserContact, idsWithAvatar: Set<UUID>): UserContactDto
    {
        return UserContactDto(
            contactAppUserId = contact.contactAppUserId,
            email = contact.contactEmail,
            firstName = contact.contactFirstName,
            lastName = contact.contactLastName,
            lastSharedAt = contact.lastSharedAt,
            shareCount = contact.shareCount,
            avatarUrl = contact.contactAppUserId
                ?.takeIf { it in idsWithAvatar }
                ?.let { AvatarUrls.forUser(it) },
        )
    }
}
