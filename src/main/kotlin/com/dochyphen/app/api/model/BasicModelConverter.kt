package com.dochyphen.app.api.model

import com.dochyphen.app.api.model.dto.*
import com.dochyphen.app.api.model.entity.*

class BasicModelConverter
{
    companion object
    {
        fun toDto(sharingSession: SharingSession?): SharingSessionBasicDto?
        {
            return sharingSession?.let {
                with(sharingSession)
                {
                    SharingSessionBasicDto(
                        id,
                        createdDate,
                        lastActivity,
                        sessionName,
                        initialShareMessage,
                        description,
                        initiator?.id,
                        recipient?.id,
                        status.toString(),
                        recipient?.email,
                        recipient?.person?.firstName,
                        recipient?.person?.lastName,
                        "Company Name",
                    )
                }
            }
        }

        fun toNoAuthDto(sharingSession: SharingSession?): NoAuthSharingSessionBasicDto?
        {
            return sharingSession?.let {
                with(sharingSession)
                {
                    NoAuthSharingSessionBasicDto(
                        id,
                        createdDate,
                        lastActivity,
                        sessionName,
                        initialShareMessage,
                        status.toString(),
                        recipient?.email,
                        initiator?.person?.firstName,
                        initiator?.person?.lastName,
                        documents.map { toDto(it) }
                    )
                }
            }
        }

        fun toDto(appUser: AppUser): AppUserBasicDto?
        {
            return appUser.person?.let {
                with(appUser)
                {
                    AppUserBasicDto(id, createdDate, isActive, email, person?.id)
                }
            }

        }

        fun toDto(person: Person?): PersonBasicDto?
        {
            return person?.let {

                with(person) {

                    PersonBasicDto(
                        id,
                        createdDate,
                        firstName,
                        lastName,
                        identificationNumber,
                        personIDType.toString(),
                        person.contactDetails?.id
                    )
                }
            }
        }

        fun toDto(contactDetails: ContactDetails?): ContactDetailsBasicDto?
        {
            return contactDetails?.let {

                with(contactDetails)
                {
                    ContactDetailsBasicDto(id, createdDate, email, phoneNumber)
                }
            }
        }

        fun toDto(document: Document?): DocumentBasicDto?
        {
            return document?.let {
                with(document)
                {
                    DocumentBasicDto(id, createdDate, title, type.toString(), restrictedType.toString(), hash)
                }
            }
        }
    }
}