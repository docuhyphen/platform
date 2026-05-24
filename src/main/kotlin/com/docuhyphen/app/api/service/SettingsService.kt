package com.docuhyphen.app.api.service

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.OrganizationSettingsDto
import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.AppUserRole.ORG_ADMIN
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.docuhyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SettingsService @Inject constructor(
    var authorizationService: ServiceActionAuthorizationService,
    var authTokenContext: AuthTokenContext,
    var appUserService: AppUserService,
    var organizationService: OrganizationService,
    var adminActionGuardService: AdminActionGuardService,
)
{
    @Transactional
    fun updateAppUserSettings(
        targetUserId: String?,
        settingsDto: AppUserSettingsDto
    ): AppUserSettings
    {
        val currentUser = authTokenContext.authToken.appUser!!

        val targetUser = if (targetUserId.isNullOrBlank())
        {
            currentUser
        }
        else
        {
            try
            {
                val uuid = UUID.fromString(targetUserId)
                appUserService.getById(uuid)
                    ?: throw AppUserNotFoundException("User with ID $targetUserId not found")
            }
            catch (e: IllegalArgumentException)
            {
                throw DataIntegrityException("Invalid user ID format: $targetUserId")
            }
        }

        authorizationService.validateUpdateAppUserSettings(currentUser, targetUser)

        val settings = targetUser.settings ?: AppUserSettings().also {
            targetUser.settings = it
            it.appUser = targetUser
        }

        settings.updatedDate = Timestamp.from(Instant.now())

        if (settings.notifyLogin != settingsDto.notifyLogin)
        {
            authorizationService.validateUpdateNotifyLoginSettings(currentUser, targetUser)
            settings.notifyLogin = settingsDto.notifyLogin
        }

        if (settings.autoPreviewDocuments != settingsDto.autoPreviewDocuments)
        {
            authorizationService.validateUpdateAutoPreviewSetting(currentUser, targetUser)
            settings.autoPreviewDocuments = settingsDto.autoPreviewDocuments
        }

        if (settings.notifyShareStart != settingsDto.notifyShareStart ||
            settings.notifyShareAccept != settingsDto.notifyShareAccept ||
            settings.notifyShareDecline != settingsDto.notifyShareDecline ||
            settings.notifyShareEnd != settingsDto.notifyShareEnd ||
            settings.notifyDocComment != settingsDto.notifyDocComment ||
            settings.notifyDocDelete != settingsDto.notifyDocDelete ||
            settings.notifyDocAdd != settingsDto.notifyDocAdd ||
            settings.notifyDocUpload != settingsDto.notifyDocUpload
        )
        {
            authorizationService.validateUpdateNotificationSettings(currentUser, targetUser)

            settings.notifyShareStart = settingsDto.notifyShareStart
            settings.notifyShareAccept = settingsDto.notifyShareAccept
            settings.notifyShareDecline = settingsDto.notifyShareDecline
            settings.notifyShareEnd = settingsDto.notifyShareEnd
            settings.notifyDocComment = settingsDto.notifyDocComment
            settings.notifyDocDelete = settingsDto.notifyDocDelete
            settings.notifyDocAdd = settingsDto.notifyDocAdd
            settings.notifyDocUpload = settingsDto.notifyDocUpload
        }

        targetUser.settings = settings
        appUserService.update(targetUser)

        return settings
    }

    @Transactional
    fun updateOrganizationSettings(
        organizationId: String,
        settingsDto: OrganizationSettingsDto,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationSettings
    {
        val currentUser = authTokenContext.authToken.appUser!!

        if (currentUser.role != ORG_ADMIN)
        {
            throw UnauthorizedException("User does not have permission to update organization settings")
        }

        adminActionGuardService.enforce(
            action = "ORG_SETTINGS_UPDATE",
            actorId = currentUser.id,
            context = adminApprovalContext,
            requireDualApproval = true,
        )

        // Get the organization
        val organization = try
        {
            val uuid = UUID.fromString(organizationId)
            organizationService.getOrganizationById(uuid)
        }
        catch (e: IllegalArgumentException)
        {
            throw DataIntegrityException("Invalid organization ID format: $organizationId")
        }

        // Validate the user has permission to update organization settings
        authorizationService.validateUpdateOrganizationSettings(currentUser)

        // Get existing settings or create new ones if null
        val settings = organization.settings ?: OrganizationSettings().also {
            organization.settings = it
            it.organization = organization
        }

        // Update timestamp
        settings.updatedDate = Timestamp.from(Instant.now())

        // Apply updates with proper validations
        if (settings.allowShareWithoutPairing != settingsDto.allowShareWithoutPairing)
        {
            authorizationService.validateUpdateShareWithoutPairingSetting(currentUser)
            settings.allowShareWithoutPairing = settingsDto.allowShareWithoutPairing
        }

        if (settings.allowProfileUpdate != settingsDto.allowProfileUpdate)
        {
            authorizationService.validateUpdateProfileUpdatePermission(currentUser)
            settings.allowProfileUpdate = settingsDto.allowProfileUpdate
        }

        if (settings.allowEmailUpdate != settingsDto.allowEmailUpdate)
        {
            authorizationService.validateUpdateEmailUpdatePermission(currentUser)
            settings.allowEmailUpdate = settingsDto.allowEmailUpdate
        }

        organization.settings = settings
        organizationService.update(organization)

        return settings
    }

    companion object
    {
        fun getDefaultAppUserSettings(): AppUserSettingsDto
        {
            return AppUserSettingsDto(
                notifyLogin = false,
                autoPreviewDocuments = true,
                notifyShareStart = true,
                notifyShareAccept = true,
                notifyShareDecline = true,
                notifyShareEnd = false,
                notifyDocComment = true,
                notifyDocDelete = true,
                notifyDocAdd = true,
                notifyDocUpload = true
            )
        }

        fun getDefaultOrganizationSettings(): OrganizationSettingsDto
        {
            return OrganizationSettingsDto(
                allowShareWithoutPairing = false,
                allowProfileUpdate = false,
                allowEmailUpdate = false
            )
        }
    }
}