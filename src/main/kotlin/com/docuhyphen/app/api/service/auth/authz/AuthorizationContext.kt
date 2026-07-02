package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppUser
import java.util.UUID

/**
 * Per-request contextual envelope passed alongside [PrincipalRef] / [ResourceRef] when
 * authorising an action. Populated upstream by the auth filter once V8/V9 land and the
 * JWT carries `active_membership_id`.
 *
 * - [actingUser]          : the [AppUser] behind the request (null for anonymous / link-only).
 * - [activeMembershipId]  : which `OrganizationMembership` is currently active (multi-org).
 * - [activeOrgId]         : derived org id corresponding to [activeMembershipId].
 * - [mfaSatisfied]        : has the current session passed step-up MFA recently?
 *                           Used to honour `Share.constraints.require_mfa`.
 * - [clientIp]            : raw client IP, evaluated against `Share.constraints.ip_allowlist`.
 * - [applicationId]       : when present, the request is being made by a registered APPLICATION
 *                           principal; mutually exclusive with [actingUser].
 * - [shareLinkTokenHash]  : when present, the request is being made via a [ShareLink] token;
 *                           authorisation treats the caller as a `PUBLIC_LINK` principal.
 */
data class AuthorizationContext(
    val actingUser: AppUser? = null,
    val applicationId: UUID? = null,
    val activeMembershipId: UUID? = null,
    val activeOrgId: UUID? = null,
    val mfaSatisfied: Boolean = false,
    val clientIp: String? = null,
    val shareLinkTokenHash: String? = null,
)
{
    companion object
    {
        val ANONYMOUS = AuthorizationContext()
    }
}

