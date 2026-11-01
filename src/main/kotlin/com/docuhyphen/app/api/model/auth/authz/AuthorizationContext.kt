package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.model.entity.AppUser
import java.util.*

data class AuthorizationContext(
    val actingUser: AppUser? = null,
    val applicationId: UUID? = null,
    val activeMembershipId: UUID? = null,
    val activeOrgId: UUID? = null,
    val mfaSatisfied: Boolean = false,
    val clientIp: String? = null,
    val shareLinkTokenHash: String? = null,
    val sessionRef: String? = null,
)
{
    companion object
    {
        val ANONYMOUS = AuthorizationContext()
    }
}

