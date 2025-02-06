package com.dochyphen.app.api.model

import com.dochyphen.app.api.model.dto.AppUserBasicDto
import com.dochyphen.app.api.model.dto.ContactDetailsBasicDto
import com.dochyphen.app.api.model.dto.PersonBasicDto
import com.dochyphen.app.api.model.dto.SharingSessionBasicDto

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
                        receiver?.id,
                        status.toString()
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
    }


}