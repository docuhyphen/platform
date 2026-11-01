package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.ScimEmail
import com.docuhyphen.app.api.model.dto.ScimError
import com.docuhyphen.app.api.model.dto.ScimMeta
import com.docuhyphen.app.api.model.dto.ScimName
import com.docuhyphen.app.api.model.dto.ScimUser
import com.docuhyphen.app.api.model.entity.AppUser

object ScimDtoMapper
{
    fun toScim(appUser: AppUser): ScimUser
    {
        val firstName = appUser.person?.firstName
        val lastName = appUser.person?.lastName
        return ScimUser(
            id = appUser.id.toString(),
            userName = appUser.email,
            name = ScimName(
                givenName = firstName,
                familyName = lastName,
                formatted = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null },
            ),
            emails = listOf(ScimEmail(value = appUser.email, primary = true, type = "work")),
            active = appUser.isActive,
            meta = ScimMeta(
                created = appUser.createdDate.toInstant().toString(),
                lastModified = appUser.createdDate.toInstant().toString(),
                location = "/scim/v2/Users/${appUser.id}",
            ),
        )
    }

    fun toError(statusCode: Int, detail: String, scimType: String?): ScimError
    {
        return ScimError(status = statusCode.toString(), detail = detail, scimType = scimType)
    }
}
