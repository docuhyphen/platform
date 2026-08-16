package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.application.SettingsService
import org.hibernate.Hibernate
import org.hibernate.LazyInitializationException

class DetailedEntityToDtoTransformer
{
    companion object
    {
        // Relative API path the frontend uses to load the authenticated user's avatar
        // binary. Present only when the user has a stored profile picture.
        private const val AVATAR_ENDPOINT_PATH = "/app-user/avatar"

        fun toDto(document: Document?): DocumentDetailedDto?
        {
            return document?.let {
                with(document)
                {
                    val lastUploadedByFirstName = safeLastUploadedByName(document, true)
                    val lastUploadedByLastName = safeLastUploadedByName(document, false)

                    DocumentDetailedDto(
                        id,
                        createdDate,
                        uploadDate,
                        title,
                        type.toString(),
                        restrictedType.toString(),
                        restrictedType != null,
                        hash,
                        mutableListOf(),
                        null,
                        lastUploadedByFirstName,
                        lastUploadedByLastName,
                    )
                }
            }
        }

        private fun safeLastUploadedByName(document: Document, isFirstName: Boolean): String?
        {
            return try
            {
                if (!Hibernate.isPropertyInitialized(document, "lastUpdatedBy")) return null
                val uploader = document.lastUpdatedBy ?: return null
                if (!Hibernate.isPropertyInitialized(uploader, "person")) return null
                val person = uploader.person ?: return null

                if (isFirstName) person.firstName else person.lastName
            }
            catch (_: LazyInitializationException)
            {
                null
            }
        }

        fun toDto(documentVersion: DocumentVersion?): DocumentVersionDetailedDto?
        {
            return documentVersion?.let {
                with(documentVersion)
                {
                    DocumentVersionDetailedDto(
                        id,
                        document.id.toString(),
                        createdDate,
                        version,
                        storagePath,
                        createdByEmail,
                        createdBy?.person?.let {
                            listOfNotNull(it.firstName, it.lastName)
                                .joinToString(" ")
                                .takeIf { name -> name.isNotBlank() }
                        } ?: createdByEmail
                    )
                }
            }
        }

        fun toDto(exchange: Exchange?): ExchangeDetailedDto?
        {
            return exchange?.let {
                with(exchange)
                {
                    ExchangeDetailedDto(
                        id = id,
                        createdDate = createdDate,
                        endDate = endDate,
                        endNote = endNote,
                        lastActivity = lastActivity,
                        name = name,
                        initialShareMessage = initialShareMessage,
                        description = description,
                        initiator = toPublicDto(initiator),
                        status = status.toString(),
                        requestRecipientSignIn = requireRecipientSignIn,
                        noAuthAccessValidityDays = noAuthAccessValidityDays,
                        documents = documents.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(appUser: AppUser?): AppUserDetailedDto? = toDto(appUser, emptySet(), emptySet())

        /**
         * [organizationRoles] contains every role within the organization in whose context this
         * DTO is being produced (per-org now, via organization_membership), callers that have
         * an org context (e.g. the org-members listing) resolve and pass it; context-free
         * callers pass null.
         */
        fun toDto(appUser: AppUser?, organizationRoles: Set<OrganizationRoleName>): AppUserDetailedDto? =
            toDto(appUser, emptySet(), organizationRoles)

        fun toDto(
            appUser: AppUser?,
            appRoles: Set<AppRoleName>,
            organizationRoles: Set<OrganizationRoleName>,
        ): AppUserDetailedDto?
        {
            return appUser?.let {

                with(appUser)
                {
                    AppUserDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        email,
                        appRoles.map { it.name }.sorted(),
                        organizationRoles.map { it.name }.sorted(),
                        toDto(person),
                        toDto(settings),
                        mfaType.name,
                        emailMfaFallbackEnabled,
                        avatarUrl = if (avatarStorageKey != null) AVATAR_ENDPOINT_PATH else null,
                    )
                }
            }
        }

        /**
         * Builds a safe public view of another user. Excludes private settings, notification
         * preferences, identification numbers, and contact details.
         */
        fun toPublicDto(appUser: AppUser?): AppUserPublicDto? = toPublicDto(appUser, emptySet(), emptySet())

        fun toPublicDto(
            appUser: AppUser?,
            organizationRoles: Set<OrganizationRoleName>,
        ): AppUserPublicDto? = toPublicDto(appUser, emptySet(), organizationRoles)

        fun toPublicDto(
            appUser: AppUser?,
            appRoles: Set<AppRoleName>,
            organizationRoles: Set<OrganizationRoleName>,
        ): AppUserPublicDto?
        {
            return appUser?.let {
                AppUserPublicDto(
                    id = it.id,
                    email = it.email,
                    person = it.person?.let { p -> PersonPublicDto(firstName = p.firstName, lastName = p.lastName) },
                    avatarUrl = if (it.avatarStorageKey != null) AvatarUrls.forUser(it.id) else null,
                    isActive = it.isActive,
                    appRoles = appRoles.map { r -> r.name }.sorted(),
                    organizationRoles = organizationRoles.map { r -> r.name }.sorted(),
                )
            }
        }

        fun toDto(person: Person?): PersonDetailedDto?
        {
            return person?.let {
                with(person)
                {
                    PersonDetailedDto(
                        id,
                        createdDate,
                        firstName,
                        lastName,
                        identificationNumber,
                        personIDType?.toString(),
                        toDto(contactDetails)
                    )
                }
            }
        }

        fun toDto(contactDetails: ContactDetails?): ContactDetailsDetailedDto?
        {
            return contactDetails?.let {
                with(contactDetails)
                {
                    ContactDetailsDetailedDto(
                        id,
                        createdDate,
                        isPhoneVerified,
                        email,
                        phoneNumber
                    )
                }
            }
        }

        fun toDto(comment: ExchangeDocumentComment?): DocumentCommentDetailedDto?
        {
            return comment?.let {
                with(comment)
                {
                    DocumentCommentDetailedDto(
                        id,
                        createdDate,
                        commentText,
                        commentedBy.person?.firstName,
                        commentedBy.person?.lastName,
                        internalOrganizationId != null,
                        pageNumber,
                        documentVersion?.id?.toString(),
                        documentVersion?.version,
                        commentedByAvatarUrl = if (commentedBy.avatarStorageKey != null) AvatarUrls.forUser(commentedBy.id) else null,
                    )
                }
            }
        }

        fun toDto(organization: Organization?): OrganizationDetailedDto?
        {
            return organization?.let {
                with(organization)
                {
                    OrganizationDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        verificationComplete,
                        name,
                        registrationNumber,
                        toDto(contactDetails),
                        toDto(settings)
                    )
                }
            }
        }


        fun toDto(appUserSettings: AppUserSettings?): AppUserSettingsDto
        {
            return appUserSettings?.let {
                with(it) {
                    AppUserSettingsDto(
                        id = id,
                        notifyLogin = notifyLogin,
                        autoPreviewDocuments = autoPreviewDocuments,
                        notifyShareStart = notifyShareStart,
                        notifyShareAccept = notifyShareAccept,
                        notifyShareDecline = notifyShareDecline,
                        notifyShareEnd = notifyShareEnd,
                        notifyDocComment = notifyDocComment,
                        notifyDocDelete = notifyDocDelete,
                        notifyDocAdd = notifyDocAdd,
                        notifyDocUpload = notifyDocUpload,
                        notifyShareStartChannels = SettingsService.parseNotificationChannels(notifyShareStartChannels),
                        notifyShareAcceptChannels = SettingsService.parseNotificationChannels(notifyShareAcceptChannels),
                        notifyShareDeclineChannels = SettingsService.parseNotificationChannels(notifyShareDeclineChannels),
                        notifyShareEndChannels = SettingsService.parseNotificationChannels(notifyShareEndChannels),
                        notifyDocCommentChannels = SettingsService.parseNotificationChannels(notifyDocCommentChannels),
                        notifyDocDeleteChannels = SettingsService.parseNotificationChannels(notifyDocDeleteChannels),
                        notifyDocAddChannels = SettingsService.parseNotificationChannels(notifyDocAddChannels),
                        notifyDocUploadChannels = SettingsService.parseNotificationChannels(notifyDocUploadChannels),
                        theme = theme,
                        tourCompleted = tourCompleted,
                        documentLibraryView = documentLibraryView,
                        blueprintsView = blueprintsView,
                        workflowsView = workflowsView,
                        sequencesView = sequencesView,
                        variablesView = variablesView,
                        communicationsView = communicationsView,
                    )
                }
            } ?: SettingsService.getDefaultAppUserSettings()
        }

        fun toDto(organizationSettings: OrganizationSettings?): OrganizationSettingsDto
        {
            return organizationSettings?.let {
                with(it) {
                    OrganizationSettingsDto(
                        id = id,
                        requireTrustedOrganizationForB2b = requireTrustedOrganizationForB2b,
                        discoverableForTrustRequests = discoverableForTrustRequests,
                        allowExternalCustomerSharing = allowExternalCustomerSharing,
                        allowProfileUpdate = allowProfileUpdate,
                        allowEmailUpdate = allowEmailUpdate,
                        requireRecipientAcceptance = requireRecipientAcceptance,
                    )
                }
            } ?: SettingsService.getDefaultOrganizationSettings()
        }
    }
}
