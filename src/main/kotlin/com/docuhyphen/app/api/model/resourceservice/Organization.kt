package com.docuhyphen.app.api.model.resourceservice

import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName

data class OrganizationGroupMemberModel(
    val appUserId: String,
    val groupRole: PrincipalGroupRoleName,
)
