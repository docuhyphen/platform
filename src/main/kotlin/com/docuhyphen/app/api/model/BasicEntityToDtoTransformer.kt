package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*

class BasicEntityToDtoTransformer
{
    companion object
    {
        fun toDto(exchange: Exchange?): ExchangeBasicDto?
        {
            return exchange?.let {
                with(exchange)
                {
                    ExchangeBasicDto(
                        id,
                        createdDate,
                        lastActivity,
                        name,
                        initialShareMessage,
                        description,
                        initiator?.id,
                        status.toString(),
                    )
                }
            }
        }

        fun toNoAuthDto(exchange: Exchange?): NoAuthExchangeBasicDto?
        {
            return exchange?.let {
                with(exchange)
                {
                    NoAuthExchangeBasicDto(
                        id,
                        createdDate,
                        lastActivity,
                        name,
                        initialShareMessage,
                        status.toString(),
                        // Recipient email lives on the recipient's Share now; the no-auth viewer
                        // is the recipient themselves, so it is not echoed back here.
                        null,
                        initiator?.person?.firstName,
                        initiator?.person?.lastName,
                        noAuthAccessValidityDays,
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
                        personIDType?.toString(),
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
                    ContactDetailsBasicDto(
                        id,
                        createdDate,
                        isPhoneVerified,
                        email,
                        phoneNumber
                    )
                }
            }
        }

        fun toDto(document: Document?): DocumentBasicDto?
        {
            return document?.let {
                with(document)
                {
                    DocumentBasicDto(
                        id,
                        createdDate,
                        uploadDate,
                        title,
                        type.toString(),
                        restrictedType.toString(),
                        hash
                    )
                }
            }
        }

        fun toDto(organization: Organization?): OrganizationBasicDto?
        {
            return organization?.let {
                with(organization)
                {
                    OrganizationBasicDto(id, createdDate, name, registrationNumber)
                }
            }
        }

    }
}
