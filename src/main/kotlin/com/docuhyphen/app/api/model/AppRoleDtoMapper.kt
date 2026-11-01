package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.resource.model.AppAdminDto
import com.docuhyphen.app.api.resource.model.AppUserSearchResultDto

object AppRoleDtoMapper
{
    fun toAppAdminDto(assignment: AppRoleAssignment, appUser: AppUser?): AppAdminDto = AppAdminDto(
        assignmentId = assignment.id.toString(),
        appUserId = assignment.appUserId.toString(),
        email = appUser?.email,
        firstName = appUser?.person?.firstName,
        lastName = appUser?.person?.lastName,
        grantedByAppUserId = assignment.grantedByAppUserId?.toString(),
        grantedAt = assignment.grantedAt.toInstant().toString(),
    )

    fun toGrantedAppAdminDto(assignment: AppRoleAssignment): AppAdminDto = AppAdminDto(
        assignmentId = assignment.id.toString(),
        appUserId = assignment.appUserId.toString(),
    )

    fun toCandidateDto(user: AppUser): AppUserSearchResultDto = AppUserSearchResultDto(
        id = user.id.toString(),
        email = user.email,
        firstName = user.person?.firstName,
        lastName = user.person?.lastName,
    )
}
