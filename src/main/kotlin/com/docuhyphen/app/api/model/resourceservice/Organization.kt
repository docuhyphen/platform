package com.docuhyphen.app.api.model.resourceservice

import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName

/**
 * A desired group member in a create/update request, in the new role-based model.
 * Replaces the legacy per-member permission bag, capabilities derive from [PrincipalGroupRoleName].
 */
data class OrganizationGroupMemberModel(
    val appUserId: String,
    val groupRole: PrincipalGroupRoleName,
)
