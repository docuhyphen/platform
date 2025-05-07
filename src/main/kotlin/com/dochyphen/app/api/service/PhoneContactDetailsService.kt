package com.dochyphen.app.api.service

import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.repository.ContactDetailsRepository
import com.dochyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.dochyphen.app.api.service.communication.OtpService
import com.dochyphen.app.api.service.communication.PhoneService
import com.dochyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

@RequestScoped
class PhoneContactDetailsService @Inject constructor(
    private val serviceActionAuthorizationService: ServiceActionAuthorizationService,
    private val contactDetailsRepo: ContactDetailsRepository,
    private val phoneCommunicationService: PhoneService,
    private val configurationService: ConfigurationService,
    private val authTokenContext: AuthTokenContext,
    private val otpService: OtpService
)
{
    fun initiatePhoneNumberAddition(
        contactDetailsId: String?,
        phoneNumber: String?
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (phoneNumber.isNullOrBlank())
        {
            throw IllegalArgumentException("Phone number cannot be null or blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.phoneNumber == phoneNumber)
        {
            throw IllegalArgumentException("Phone number is already associated with the contact details")
        }

        val duplicateContactDetails = contactDetailsRepo.findByPhoneNumber(phoneNumber)

        if (duplicateContactDetails != null && duplicateContactDetails.id != contactDetails.id)
        {
            throw IllegalArgumentException("Phone number is already associated with another contact details")
        }

        val verificationCode = otpService.generatePhoneVerificationCode()

        contactDetails.phoneNumber = phoneNumber
        contactDetails.phoneVerificationCode = verificationCode
        contactDetails.isPhoneVerified = false

        contactDetailsRepo.update(contactDetails)

        phoneCommunicationService.sendSms(
            phoneNumber,
            "${configurationService.getAppPhoneSubjectTitle()} Phone Number Verification",
            "Your verification code is: $verificationCode"
        )
    }

    fun completePhoneNumberAddition(
        contactDetailsId: String?,
        phoneNumber: String?,
        verificationCode: String?
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (phoneNumber.isNullOrBlank())
        {
            throw IllegalArgumentException("Phone number cannot be null or blank")
        }

        if (verificationCode.isNullOrBlank())
        {
            throw IllegalArgumentException("Verification code cannot be null or blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.phoneNumber != phoneNumber)
        {
            throw IllegalArgumentException("Phone number does not match the contact details")
        }

        if (contactDetails.phoneVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        contactDetails.phoneVerificationCode = null
        contactDetails.isPhoneVerified = true
        contactDetailsRepo.update(contactDetails)
    }

    fun initiatePhoneNumberUpdate(
        contactDetailsId: String?,
        phoneNumber: String
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (phoneNumber.isBlank())
        {
            throw IllegalArgumentException("Phone number cannot be blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.phoneNumber == phoneNumber)
        {
            throw IllegalArgumentException("New phone number is the same as the current one")
        }

        val duplicateContactDetails = contactDetailsRepo.findByPhoneNumber(phoneNumber)

        if (duplicateContactDetails != null && duplicateContactDetails.id != contactDetails.id)
        {
            throw IllegalArgumentException("Phone number is already associated with another contact details")
        }

        val verificationCode = otpService.generatePhoneVerificationCode()

        contactDetails.pendingPhoneNumber = phoneNumber
        contactDetails.phoneVerificationCode = verificationCode
        contactDetailsRepo.update(contactDetails)

        phoneCommunicationService.sendSms(
            phoneNumber,
            "${configurationService.getAppPhoneSubjectTitle()} Phone Number Verification",
            "Your verification code is: $verificationCode"
        )
    }

    fun completePhoneNumberUpdate(
        contactDetailsId: String?,
        phoneNumber: String,
        verificationCode: String
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (phoneNumber.isBlank())
        {
            throw IllegalArgumentException("Phone number cannot be blank")
        }

        if (verificationCode.isBlank())
        {
            throw IllegalArgumentException("Verification code cannot be blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.pendingPhoneNumber != phoneNumber)
        {
            throw IllegalArgumentException("Phone number does not match the pending phone number")
        }

        if (contactDetails.phoneVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        contactDetails.phoneNumber = phoneNumber
        contactDetails.pendingPhoneNumber = null
        contactDetails.phoneVerificationCode = null
        contactDetails.isPhoneVerified = true
        contactDetailsRepo.update(contactDetails)
    }
}