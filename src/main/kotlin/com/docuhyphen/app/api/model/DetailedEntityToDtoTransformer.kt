package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.SettingsService
import org.hibernate.Hibernate
import org.hibernate.LazyInitializationException

class DetailedEntityToDtoTransformer
{
    companion object
    {
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
                        "TODO"
//                        createdBy!!.id.toString()
                    )
                }
            }
        }

        fun toDto(sharingSession: SharingSession?): SharingSessionDetailedDto?
        {
            return sharingSession?.let {
                with(sharingSession)
                {
                    SharingSessionDetailedDto(
                        id = id,
                        createdDate = createdDate,
                        endDate = endDate,
                        endNote = endNote,
                        lastActivity = lastActivity,
                        sessionName = sessionName,
                        initialShareMessage = initialShareMessage,
                        description = description,
                        initiator = toDto(initiator),
                        status = status.toString(),
                        requestRecipientSignIn = requireRecipientSignIn,
                        noAuthAccessValidityDays = noAuthAccessValidityDays,
                        documents = documents.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(appUser: AppUser?): AppUserDetailedDto? = toDto(appUser, null)

        /**
         * [role] is the user's effective role within the organization in whose context this
         * DTO is being produced (per-org now, via organization_membership), callers that have
         * an org context (e.g. the org-members listing) resolve and pass it; context-free
         * callers pass null.
         */
        fun toDto(appUser: AppUser?, role: String?): AppUserDetailedDto?
        {
            return appUser?.let {

                with(appUser)
                {
                    AppUserDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        email,
                        role,
                        toDto(person),
                        toDto(settings)
                    )
                }
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

        fun toDto(documentAuditLog: DocumentAuditLog?): DocumentAuditDetailedDto?
        {
            return documentAuditLog?.let {
                with(documentAuditLog)
                {
                    DocumentAuditDetailedDto(
                        id,
                        timestamp,
                        action.toString(),
                        toDto(performedBy),
                        performedByEmail
                    )
                }
            }
        }

        fun toDto(comment: SharingSessionDocumentComment?): DocumentCommentDetailedDto?
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
                        commentedBy.email
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
                        theme = theme,
                        tourCompleted = tourCompleted,
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
                        allowShareWithoutPairing = allowShareWithoutPairing,
                        allowProfileUpdate = allowProfileUpdate,
                        allowEmailUpdate = allowEmailUpdate
                    )
                }
            } ?: SettingsService.getDefaultOrganizationSettings()
        }
    }
}