package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Action-level authorization checks for user/org settings flows. Roles are resolved through
 * [UserRoleService] (organization_membership / role_assignment) — the legacy `AppUser.role`
 * enum is gone.
 */
@ApplicationScoped
class ServiceActionAuthorizationService @Inject constructor(
    private val userRoleService: UserRoleService,
)
{
    fun validateAppUserPhoneNumberModification(appUser: AppUser)
    {
        // Normal users may manage their phone number; application admins operate as users and
        // are not the target of this self-service flow.
        if (userRoleService.isAppAdmin(appUser.id))
        {
            throw UnauthorizedException("Application administrators cannot add a phone number here")
        }
    }

    fun validateAppUserEmailModification(appUser: AppUser)
    {
        if (userRoleService.isOrgMember(appUser.id)) return
        throw UnauthorizedException("User cannot modify email")
    }

    fun validateUpdateNotifyLoginSettings(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id && userRoleService.isOrgMember(appUser.id)) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update login notification settings")
    }

    fun validateUpdateAppUserSettings(appUser: AppUser, targetUser: AppUser)
    {
        // (Intentionally permissive — retained from the legacy implementation.)
    }

    fun validateUpdateNotificationSettings(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id && userRoleService.isOrgMember(appUser.id)) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update notification settings")
    }

    fun validateUpdateAutoPreviewSetting(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id && userRoleService.isOrgMember(appUser.id)) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update document preview settings")
    }

    // Organization Settings validation methods

    fun validateUpdateOrganizationSettings(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update organization settings")
    }

    fun validateUpdateShareWithoutPairingSetting(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update sharing session settings")
    }

    fun validateUpdateProfileUpdatePermission(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update profile update permissions")
    }

    fun validateUpdateEmailUpdatePermission(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update email update permissions")
    }

    // Validation for user profile/email updates based on org settings

    fun validateUserProfileUpdate(appUser: AppUser, organization: Organization)
    {
        if (userRoleService.isOrgAdminIn(appUser.id, organization.id)) return
        if (userRoleService.orgRoleIn(appUser.id, organization.id) != null &&
            organization.settings?.allowProfileUpdate == true)
        {
            return
        }
        throw UnauthorizedException("Profile updates are not allowed for this user")
    }

    fun validateUserEmailUpdate(appUser: AppUser, organization: Organization)
    {
        if (userRoleService.isOrgAdminIn(appUser.id, organization.id)) return
        if (userRoleService.orgRoleIn(appUser.id, organization.id) != null &&
            organization.settings?.allowEmailUpdate == true)
        {
            return
        }
        throw UnauthorizedException("Email updates are not allowed for this user")
    }
}
