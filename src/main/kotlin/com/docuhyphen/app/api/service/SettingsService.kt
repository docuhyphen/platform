package com.docuhyphen.app.api.service

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.OrganizationSettingsDto
import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
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
    var userRoleService: UserRoleService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
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

        val notifyShareStartChannels = normalizeNotificationChannels(settingsDto.notifyShareStartChannels, settingsDto.notifyShareStart)
        val notifyShareAcceptChannels = normalizeNotificationChannels(settingsDto.notifyShareAcceptChannels, settingsDto.notifyShareAccept)
        val notifyShareDeclineChannels = normalizeNotificationChannels(settingsDto.notifyShareDeclineChannels, settingsDto.notifyShareDecline)
        val notifyShareEndChannels = normalizeNotificationChannels(settingsDto.notifyShareEndChannels, settingsDto.notifyShareEnd)
        val notifyDocCommentChannels = normalizeNotificationChannels(settingsDto.notifyDocCommentChannels, settingsDto.notifyDocComment)
        val notifyDocDeleteChannels = normalizeNotificationChannels(settingsDto.notifyDocDeleteChannels, settingsDto.notifyDocDelete)
        val notifyDocAddChannels = normalizeNotificationChannels(settingsDto.notifyDocAddChannels, settingsDto.notifyDocAdd)
        val notifyDocUploadChannels = normalizeNotificationChannels(settingsDto.notifyDocUploadChannels, settingsDto.notifyDocUpload)

        val notificationChannelsChanged =
            settings.notifyShareStartChannels != serializeNotificationChannels(notifyShareStartChannels) ||
            settings.notifyShareAcceptChannels != serializeNotificationChannels(notifyShareAcceptChannels) ||
            settings.notifyShareDeclineChannels != serializeNotificationChannels(notifyShareDeclineChannels) ||
            settings.notifyShareEndChannels != serializeNotificationChannels(notifyShareEndChannels) ||
            settings.notifyDocCommentChannels != serializeNotificationChannels(notifyDocCommentChannels) ||
            settings.notifyDocDeleteChannels != serializeNotificationChannels(notifyDocDeleteChannels) ||
            settings.notifyDocAddChannels != serializeNotificationChannels(notifyDocAddChannels) ||
            settings.notifyDocUploadChannels != serializeNotificationChannels(notifyDocUploadChannels)

        if (notificationChannelsChanged)
        {
            authorizationService.validateUpdateNotificationSettings(currentUser, targetUser)

            settings.notifyShareStartChannels = serializeNotificationChannels(notifyShareStartChannels)
            settings.notifyShareAcceptChannels = serializeNotificationChannels(notifyShareAcceptChannels)
            settings.notifyShareDeclineChannels = serializeNotificationChannels(notifyShareDeclineChannels)
            settings.notifyShareEndChannels = serializeNotificationChannels(notifyShareEndChannels)
            settings.notifyDocCommentChannels = serializeNotificationChannels(notifyDocCommentChannels)
            settings.notifyDocDeleteChannels = serializeNotificationChannels(notifyDocDeleteChannels)
            settings.notifyDocAddChannels = serializeNotificationChannels(notifyDocAddChannels)
            settings.notifyDocUploadChannels = serializeNotificationChannels(notifyDocUploadChannels)

            settings.notifyShareStart = notifyShareStartChannels.isNotEmpty()
            settings.notifyShareAccept = notifyShareAcceptChannels.isNotEmpty()
            settings.notifyShareDecline = notifyShareDeclineChannels.isNotEmpty()
            settings.notifyShareEnd = notifyShareEndChannels.isNotEmpty()
            settings.notifyDocComment = notifyDocCommentChannels.isNotEmpty()
            settings.notifyDocDelete = notifyDocDeleteChannels.isNotEmpty()
            settings.notifyDocAdd = notifyDocAddChannels.isNotEmpty()
            settings.notifyDocUpload = notifyDocUploadChannels.isNotEmpty()
        }

        // Theme is a personal preference and requires no admin validation.
        val requestedTheme = settingsDto.theme.lowercase()
        if (requestedTheme !in setOf("light", "dark", "system"))
        {
            throw DataIntegrityException("Invalid theme value: ${settingsDto.theme}")
        }
        settings.theme = requestedTheme

        // Tour completion is a personal preference, no admin check needed.
        settings.tourCompleted = settingsDto.tourCompleted

        // View mode preferences are personal preferences, no admin validation needed.
        val validViewModes = setOf("cards", "table")
        if (settingsDto.documentLibraryView in validViewModes) settings.documentLibraryView = settingsDto.documentLibraryView
        if (settingsDto.blueprintsView in validViewModes) settings.blueprintsView = settingsDto.blueprintsView
        if (settingsDto.workflowsView in validViewModes) settings.workflowsView = settingsDto.workflowsView
        if (settingsDto.sequencesView in validViewModes) settings.sequencesView = settingsDto.sequencesView
        if (settingsDto.variablesView in validViewModes) settings.variablesView = settingsDto.variablesView
        if (settingsDto.communicationsView in validViewModes) settings.communicationsView = settingsDto.communicationsView

        targetUser.settings = settings
        appUserService.update(targetUser)

        return settings
    }

    @EnforceAdminAction("ORG_SETTINGS_UPDATE")
    @Transactional
    fun updateOrganizationSettings(
        organizationId: String,
        settingsDto: OrganizationSettingsDto,
        adminApprovalContext: AdminApprovalContext,
    ): OrganizationSettings
    {
        val currentUser = authTokenContext.authToken.appUser!!

        val orgUuid = try { UUID.fromString(organizationId) }
            catch (e: IllegalArgumentException) { throw DataIntegrityException("Invalid organization ID format: $organizationId") }

        if (!userRoleService.isOrgAdminIn(currentUser.id, orgUuid))
        {
            throw UnauthorizedException("User does not have permission to update organization settings")
        }

        // Get the organization
        val organization = organizationService.getOrganizationById(orgUuid)
        subscriptionGuard.requireMutation(organization.id, PlanFeature.ORGANIZATION_ADMINISTRATION)

        // Get existing settings or create new ones if null
        val settings = organization.settings ?: OrganizationSettings().also {
            organization.settings = it
            it.organization = organization
        }

        // Update timestamp
        settings.updatedDate = Timestamp.from(Instant.now())

        // Apply updates with proper validations
        if (settings.requireTrustedOrganizationForB2b != settingsDto.requireTrustedOrganizationForB2b)
        {
            authorizationService.validateUpdateTrustedOrganizationB2bSetting(currentUser)
            settings.requireTrustedOrganizationForB2b = settingsDto.requireTrustedOrganizationForB2b
        }

        settings.discoverableForTrustRequests = settingsDto.discoverableForTrustRequests
        settings.allowExternalCustomerSharing = settingsDto.allowExternalCustomerSharing

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

        settings.requireRecipientAcceptance = settingsDto.requireRecipientAcceptance

        organization.settings = settings
        organizationService.update(organization)

        return settings
    }

    companion object
    {
        private val SUPPORTED_NOTIFICATION_CHANNELS = setOf(
            NotificationChannelType.EMAIL,
            NotificationChannelType.IN_APP,
        )

        fun parseNotificationChannels(value: String): Set<NotificationChannelType>
        {
            if (value.isBlank()) return emptySet()
            return value.split(",").mapNotNull { channel ->
                runCatching { NotificationChannelType.valueOf(channel.trim()) }.getOrNull()
            }.toSet()
        }

        private fun normalizeNotificationChannels(
            requested: Set<NotificationChannelType>?,
            legacyEnabled: Boolean,
        ): Set<NotificationChannelType>
        {
            val channels = requested ?: if (legacyEnabled) SUPPORTED_NOTIFICATION_CHANNELS else emptySet()
            if (!SUPPORTED_NOTIFICATION_CHANNELS.containsAll(channels))
            {
                throw DataIntegrityException("Unsupported notification channel")
            }
            return channels
        }

        private fun serializeNotificationChannels(channels: Set<NotificationChannelType>): String =
            channels.sortedBy { it.ordinal }.joinToString(",") { it.name }

        fun getDefaultAppUserSettings(): AppUserSettingsDto
        {
            return AppUserSettingsDto(
                notifyLogin = false,
                autoPreviewDocuments = false,
                notifyShareStart = true,
                notifyShareAccept = true,
                notifyShareDecline = true,
                notifyShareEnd = false,
                notifyDocComment = true,
                notifyDocDelete = true,
                notifyDocAdd = true,
                notifyDocUpload = true,
                notifyShareStartChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyShareAcceptChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyShareDeclineChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyShareEndChannels = emptySet(),
                notifyDocCommentChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyDocDeleteChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyDocAddChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                notifyDocUploadChannels = setOf(NotificationChannelType.EMAIL, NotificationChannelType.IN_APP),
                theme = "light",
                tourCompleted = false,
                documentLibraryView = "cards",
                blueprintsView = "cards",
                workflowsView = "cards",
                sequencesView = "cards",
                variablesView = "cards",
                communicationsView = "cards",
            )
        }

        fun getDefaultOrganizationSettings(): OrganizationSettingsDto
        {
            return OrganizationSettingsDto(
                requireTrustedOrganizationForB2b = true,
                discoverableForTrustRequests = false,
                allowExternalCustomerSharing = true,
                allowProfileUpdate = false,
                allowEmailUpdate = false,
                requireRecipientAcceptance = true,
            )
        }
    }
}
