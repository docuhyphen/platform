package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.repository.OrganizationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

/**
 * Outcome of the per-request "is this org user still allowed in" check.
 *
 * `valid` is the only field consulted by the auth filter and the refresh
 * endpoint — both treat `valid = false` as a hard session block. So this
 * check must only flip `valid` for situations that genuinely mean
 * "you no longer belong here" (link revoked, deprovisioned), NOT for
 * operational org states like "org not yet activated".
 *
 * `orgActive` is informational. It is `true` when the user has no org role
 * at all (i.e. the question doesn't apply), and otherwise mirrors the
 * organization's `isActive` flag. Downstream feature gates can use it to
 * hide org-only UI / endpoints without us tearing the user's session down.
 */
data class OrganizationMembershipValidationResult(
    val valid: Boolean,
    val reasonCode: RevocationReasonCode? = null,
    val message: String? = null,
    val orgActive: Boolean = true,
)

@RequestScoped
class OrganizationMembershipValidationService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
)
{
    /**
     * Decide whether [appUser] should be allowed to hold an authenticated
     * session right now. Returns `valid = true` for:
     *   - non-org users (the question doesn't apply), and
     *   - org users whose membership link still resolves to an organization,
     *     **regardless of that organization's `isActive` flag**.
     *
     * An inactive organization is an operational/admin state — possibly the
     * org owner hasn't finished onboarding, possibly billing lapsed — and a
     * user belonging to one must still be able to sign in and use the app in
     * a degraded "no org features" mode. Treating org-inactive as an auth
     * block was the bug: the frontend told users to "check their status",
     * but every API call (including the status check itself) was rejected
     * upstream by the auth filter, so the message was a dead-end.
     *
     * Org-feature gating belongs at the feature layer (per-endpoint, per-UI
     * surface) and can read [OrganizationMembershipValidationResult.orgActive]
     * if it needs the same signal.
     */
    fun validateForSessionAccess(appUser: AppUser): OrganizationMembershipValidationResult
    {
        val isOrgRole = appUser.role == AppUserRole.ORG_ADMIN ||
                appUser.role == AppUserRole.ORG_GROUP_ADMIN ||
                appUser.role == AppUserRole.ORG_MEMBER

        if (!isOrgRole)
        {
            return OrganizationMembershipValidationResult(valid = true)
        }

        val personId = appUser.person?.id
            ?: return OrganizationMembershipValidationResult(
                valid = false,
                reasonCode = RevocationReasonCode.MEMBERSHIP_INACTIVE,
                message = "Organization membership is inactive",
                orgActive = false,
            )

        val organization = organizationRepository.findByAppUserIdAndPersonId(appUser.id, personId)
            ?: return OrganizationMembershipValidationResult(
                valid = false,
                reasonCode = RevocationReasonCode.MEMBERSHIP_INACTIVE,
                message = "Organization membership is inactive",
                orgActive = false,
            )

        // Intentionally do NOT block on `!organization.isActive` here. An
        // inactive org is a feature-gating concern, surfaced via `orgActive`
        // below — not an authentication concern. See KDoc above for the
        // rationale and the bug this avoids.
        return OrganizationMembershipValidationResult(
            valid = true,
            orgActive = organization.isActive,
        )
    }
}

