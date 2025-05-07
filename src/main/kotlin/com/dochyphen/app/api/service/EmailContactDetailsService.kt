package com.dochyphen.app.api.service

import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.repository.ContactDetailsRepository
import com.dochyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.communication.OtpService
import com.dochyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

@RequestScoped
class EmailContactDetailsService @Inject constructor(
    private val serviceActionAuthorizationService: ServiceActionAuthorizationService,
    private val contactDetailsRepo: ContactDetailsRepository,
    private val emailService: EmailService,
    private val configurationService: ConfigurationService,
    private val authTokenContext: AuthTokenContext,
    private val otpService: OtpService
)
{
    fun initiateEmailAddition(
        contactDetailsId: String?,
        email: String?
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (email.isNullOrBlank())
        {
            throw IllegalArgumentException("Email cannot be null or blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.email == email)
        {
            throw IllegalArgumentException("Email is already associated with the contact details")
        }

        val duplicateContactDetails = contactDetailsRepo.findByEmail(email)

        if (duplicateContactDetails != null && duplicateContactDetails.id != contactDetails.id)
        {
            throw IllegalArgumentException("Email is already associated with another contact details")
        }

        val verificationCode = otpService.generateEmailOtp()

        contactDetails.email = email
        contactDetails.emailVerificationCode = verificationCode
        contactDetails.isEmailVerified = false

        contactDetailsRepo.update(contactDetails)

        emailService.sendEmail(
            email,
            "${configurationService.getAppEmailSubjectTitle()} Email Verification",
            "Your verification code is: $verificationCode"
        )
    }

    fun completeEmailAddition(
        contactDetailsId: String?,
        email: String?,
        verificationCode: String?
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (email.isNullOrBlank())
        {
            throw IllegalArgumentException("Email cannot be null or blank")
        }

        if (verificationCode.isNullOrBlank())
        {
            throw IllegalArgumentException("Verification code cannot be null or blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.email != email)
        {
            throw IllegalArgumentException("Email does not match the contact details")
        }

        if (contactDetails.emailVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        contactDetails.emailVerificationCode = null
        contactDetails.isEmailVerified = true
        contactDetailsRepo.update(contactDetails)
    }

    fun initiateEmailUpdate(
        contactDetailsId: String?,
        email: String
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserEmailModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (email.isBlank())
        {
            throw IllegalArgumentException("Email cannot be blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.email == email)
        {
            throw IllegalArgumentException("New email is the same as the current one")
        }

        val duplicateContactDetails = contactDetailsRepo.findByEmail(email)

        if (duplicateContactDetails != null && duplicateContactDetails.id != contactDetails.id)
        {
            throw IllegalArgumentException("Email is already associated with another contact details")
        }

        val verificationCode = otpService.generateEmailOtp()

        contactDetails.pendingEmail = email
        contactDetails.emailVerificationCode = verificationCode
        contactDetailsRepo.update(contactDetails)

        emailService.sendEmail(
            email,
            "${configurationService.getAppEmailSubjectTitle()} Email Verification",
            "Your verification code is: $verificationCode"
        )
    }

    fun completeEmailUpdate(
        contactDetailsId: String?,
        email: String,
        verificationCode: String
    )
    {
        val appUser = authTokenContext.authToken.appUser!!
        serviceActionAuthorizationService.validateAppUserPhoneNumberModification(appUser)

        if (contactDetailsId.isNullOrBlank())
        {
            throw IllegalArgumentException("Contact details ID cannot be null or blank")
        }

        if (email.isBlank())
        {
            throw IllegalArgumentException("Email cannot be blank")
        }

        if (verificationCode.isBlank())
        {
            throw IllegalArgumentException("Verification code cannot be blank")
        }

        val contactDetails = contactDetailsRepo.findById(UUID.fromString(contactDetailsId))
            ?: throw IllegalArgumentException("Contact details not found for ID: $contactDetailsId")

        if (contactDetails.pendingEmail != email)
        {
            throw IllegalArgumentException("Email does not match the pending email")
        }

        if (contactDetails.emailVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        contactDetails.email = email
        contactDetails.pendingEmail = null
        contactDetails.emailVerificationCode = null
        contactDetails.isEmailVerified = true
        contactDetailsRepo.update(contactDetails)
    }
}