package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppUser
import java.util.*

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
 * - [sessionRef]          : non-secret identifier of the access session the request arrived on,
 *                           recorded as provenance on the records a request changes. It names the
 *                           session row, never the credential that opened it, so it is safe to
 *                           persist and read back. Null when the caller has no session record.
 */
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

