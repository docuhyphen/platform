package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.model.dto.*

class DetailedModelConverter
{
    companion object
    {
        fun toDto(document: Document?): DocumentDetailedDto?
        {
            return document?.let {
                with(document)
                {
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
                    )
                }
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

        fun toDo(sharingSession: SharingSession?): SharingSessionDetailedDto?
        {
            return sharingSession?.let {
                with(sharingSession)
                {
                    SharingSessionDetailedDto(
                        id,
                        createdDate,
                        endDate,
                        endNote,
                        lastActivity,
                        sessionName,
                        initialShareMessage,
                        description,
                        toDto(initiator),
                        toDto(recipient),
                        status.toString(),
                        requireRecipientSignIn,
                        allowDocumentAddition,
                        allowDocumentDeletion,
                        allowDocumentDownload,
                        allowDocumentUpdate,
                        allowDocumentUpload,
                        documents.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(appUser: AppUser?): AppUserDetailedDto?
        {
            return appUser?.let {
                with(appUser)
                {
                    AppUserDetailedDto(
                        id,
                        createdDate,
                        isActive,
                        email,
                        toDto(person)
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
    }
}