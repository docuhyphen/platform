package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ServiceActionAuthorizationService @Inject constructor(
    private val userRoleService: UserRoleService,
)
{
    fun validateAppUserPhoneNumberModification(appUser: AppUser)
    {
        //ToDO: admins must be able to update org phone numbers
    }

    fun validateAppUserEmailModification(appUser: AppUser)
    {
        if (userRoleService.isOrgMember(appUser.id)) return
        throw UnauthorizedException("User cannot modify email")
    }

    fun validateUpdateNotifyLoginSettings(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update login notification settings")
    }

    fun validateUpdateAppUserSettings(appUser: AppUser, targetUser: AppUser)
    {
    }

    fun validateUpdateNotificationSettings(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update notification settings")
    }

    fun validateUpdateAutoPreviewSetting(appUser: AppUser, targetUser: AppUser)
    {
        if (appUser.id == targetUser.id && userRoleService.isOrgMember(appUser.id)) return
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("User cannot update document preview settings")
    }

    fun validateUpdateOrganizationSettings(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update organization settings")
    }

    fun validateUpdateTrustedOrganizationB2bSetting(appUser: AppUser)
    {
        if (userRoleService.isOrgAdmin(appUser.id)) return
        throw UnauthorizedException("Only organization admins can update Exchange settings")
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

    fun validateUserProfileUpdate(appUser: AppUser, organization: Organization)
    {
        if (userRoleService.isOrgAdminIn(appUser.id, organization.id)) return
        if (userRoleService.orgRolesIn(appUser.id, organization.id).isNotEmpty() &&
            organization.settings?.allowProfileUpdate == true
        )
        {
            return
        }
        throw UnauthorizedException("Profile updates are not allowed for this user")
    }

    fun validateUserEmailUpdate(appUser: AppUser, organization: Organization)
    {
        if (userRoleService.isOrgAdminIn(appUser.id, organization.id)) return
        if (userRoleService.orgRolesIn(appUser.id, organization.id).isNotEmpty() &&
            organization.settings?.allowEmailUpdate == true
        )
        {
            return
        }
        throw UnauthorizedException("Email updates are not allowed for this user")
    }
}
