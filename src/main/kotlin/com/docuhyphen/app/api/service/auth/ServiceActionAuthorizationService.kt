package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserRole.*
import com.docuhyphen.app.api.model.entity.Organization
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ServiceActionAuthorizationService
{
    fun validateAppUserPhoneNumberModification(appUser: AppUser)
    {
        with(appUser) {

            when (role)
            {
                ORG_ADMIN, ORG_MEMBER, APP_USER -> return
                else ->
                    throw UnauthorizedException("User with role $role cannot add a phone number addition")
            }
        }
    }

    fun validateAppUserEmailModification(appUser: AppUser)
    {
        with(appUser) {

            if (role == ORG_ADMIN || role == ORG_MEMBER) return

            throw UnauthorizedException("User with role $role cannot add a phone number addition")
        }
    }

    fun validateUpdateNotifyLoginSettings(appUser: AppUser, targetUser: AppUser)
    {
        // Users can update their own login notification settings
        if (appUser.id == targetUser.id && (appUser.role == ORG_ADMIN || appUser.role == ORG_MEMBER))
        {
            return
        }

        // Admins can update settings for other users
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("User with role ${appUser.role} cannot update login notification settings")
    }

    fun validateUpdateAppUserSettings(appUser: AppUser, targetUser: AppUser)
    {
//        // Only admins can update settings for other users
//        if (appUser.id != targetUser.id)
//        {
//            if (appUser.role != ORG_ADMIN)
//            {
//                throw UnauthorizedException("Only organization admins can update settings for other users")
//            }
//        }
//        else
//        {
//            // Even if the user is updating their own settings, we need to check their role
//            if (appUser.role != ORG_ADMIN && appUser.role != ORG_MEMBER)
//            {
//                throw UnauthorizedException("User with role ${appUser.role} cannot update app user settings")
//            }
//        }
    }

    fun validateUpdateNotificationSettings(appUser: AppUser, targetUser: AppUser)
    {
        // All org members and admins can update their notification preferences
        if (appUser.id == targetUser.id && (appUser.role == ORG_ADMIN || appUser.role == ORG_MEMBER))
        {
            return
        }

        // Admins can update settings for other users in the organization
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("User with role ${appUser.role} cannot update notification settings")
    }

    fun validateUpdateAutoPreviewSetting(appUser: AppUser, targetUser: AppUser)
    {
        // This follows the same pattern as notification settings
        if (appUser.id == targetUser.id && (appUser.role == ORG_ADMIN || appUser.role == ORG_MEMBER))
        {
            return
        }

        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("User with role ${appUser.role} cannot update document preview settings")
    }

    // Organization Settings validation methods

    fun validateUpdateOrganizationSettings(appUser: AppUser)
    {
        // Only organization admins can modify org settings
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("Only organization admins can update organization settings")
    }

    fun validateUpdateShareWithoutPairingSetting(appUser: AppUser)
    {
        // This is a security-sensitive setting, only admins can change
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("Only organization admins can update sharing session settings")
    }

    fun validateUpdateProfileUpdatePermission(appUser: AppUser)
    {
        // Only admins can decide if members can update their profiles
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("Only organization admins can update profile update permissions")
    }

    fun validateUpdateEmailUpdatePermission(appUser: AppUser)
    {
        // Only admins can decide if members can update their email
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        throw UnauthorizedException("Only organization admins can update email update permissions")
    }

    // Validation for user profile/email updates based on org settings

    fun validateUserProfileUpdate(appUser: AppUser, organization: Organization)
    {
        // Admins can always update their profile
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        // Members can update their profile only if organization settings allow it
        if (appUser.role == ORG_MEMBER && organization.settings?.allowProfileUpdate == true)
        {
            return
        }

        throw UnauthorizedException("Profile updates are not allowed for users with role ${appUser.role}")
    }

    fun validateUserEmailUpdate(appUser: AppUser, organization: Organization)
    {
        // Admins can always update their email
        if (appUser.role == ORG_ADMIN)
        {
            return
        }

        // Members can update their email only if organization settings allow it
        if (appUser.role == ORG_MEMBER && organization.settings?.allowEmailUpdate == true)
        {
            return
        }

        throw UnauthorizedException("Email updates are not allowed for users with role ${appUser.role}")
    }
}