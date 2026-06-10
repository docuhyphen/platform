package com.docuhyphen.app.api.model.resourceservice

import com.docuhyphen.app.api.model.entity.GroupRole

/**
 * A desired group member in a create/update request, in the new role-based model.
 * Replaces the legacy per-member permission bag, capabilities derive from the [GroupRole].
 */
data class OrganizationGroupMemberModel(
    val appUserId: String,
    val groupRole: GroupRole,
)
