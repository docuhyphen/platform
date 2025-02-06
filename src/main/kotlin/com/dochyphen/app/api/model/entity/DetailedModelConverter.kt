package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.model.dto.AppUserDetailedDto
import com.dochyphen.app.api.model.dto.ContactDetailsDetailedDto
import com.dochyphen.app.api.model.dto.PersonDetailedDto
import com.dochyphen.app.api.model.dto.SharingSessionDetailedDto

class DetailedModelConverter
{
    companion object
    {
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
                        toDto(receiver),
                        status.toString()
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

        /*
        @Serializable
data class ContactDetailsDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val email: String?,
    val phoneNumber: String?
)

         */

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
    }


}