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
                        title,
                        type.toString(),
                        restrictedType.toString(),
                        hash,
                        mutableListOf(),
//                    comments.map { toDto(it) }
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
                        lastActivity,
                        sessionName,
                        initialShareMessage,
                        description,
                        toDto(initiator),
                        toDto(recipient),
                        status.toString(),
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
                        personIDType.toString(),
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
    }
}