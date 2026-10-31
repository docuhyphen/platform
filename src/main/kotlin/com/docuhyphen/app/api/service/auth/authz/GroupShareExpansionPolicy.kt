package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.Share

fun interface GroupShareExpansionPolicy {
    fun expandsToNewMembers(groupShare: Share): Boolean

    companion object {
        val UNRESTRICTED = GroupShareExpansionPolicy { true }
    }
}
