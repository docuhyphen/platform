package com.dochyphen.app.api.service

import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.dto.AppUserSettingsDto
import com.dochyphen.app.api.model.dto.PersonBasicDto
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.AppUserSettings
import com.dochyphen.app.api.model.entity.Person
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.service.auth.SignOutService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.communication.OtpService
import com.dochyphen.app.api.service.config.ConfigurationService
import com.dochyphen.app.api.service.sharingsession.SharingSessionRetrievalService
import com.yubico.webauthn.data.UserIdentity
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

@RequestScoped
class AppUserService @Inject constructor(
    val appUserRepository: AppUserRepository,
    val settingsService: SettingsService,
    val authTokenContext: AuthTokenContext,
    val otpService: OtpService,
    val emailService: EmailService,
    val configurationService: ConfigurationService,
    val signOutService: SignOutService,
    val sharingSessionService: SharingSessionRetrievalService
)
{
    fun getById(id: UUID): AppUser?
    {
        return appUserRepository.findById(id)
    }

    fun findByEmail(email: String): AppUser?
    {
        return appUserRepository.findByEmail(email)
    }

    fun create(user: AppUser): AppUser
    {
        return appUserRepository.save(user)
    }

    fun update(user: AppUser)
    {
        appUserRepository.update(user)
    }

    fun updatePerson(appUser: AppUser, person: Person)
    {
        appUser.person = person
        appUserRepository.update(appUser)
    }

    fun getAppUserByEmail(email: String): AppUser?
    {
        return appUserRepository.findByEmail(email)
    }

    fun updateSettings(targetUserId: String?, settingsDto: AppUserSettingsDto): AppUserSettings
    {
        return settingsService.updateAppUserSettings(targetUserId, settingsDto)
    }

    fun updatePerson(appUser: AppUser, personDto: PersonBasicDto)
    {
        if(personDto.firstName.isNullOrBlank())
        {
            throw IllegalArgumentException("First name cannot be null or blank")
        }

        if(personDto.lastName.isNullOrBlank())
        {
            throw IllegalArgumentException("Last name cannot be null or blank")
        }

        appUser.person = Person().apply {
            this.firstName = personDto.firstName
            this.lastName = personDto.lastName
        }

        appUserRepository.update(appUser)
    }

    fun initiateEmailUpdate(email: String?)
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (email.isNullOrBlank())
        {
            throw IllegalArgumentException("Email cannot be null or blank")
        }

        if (email == appUser.email)
        {
            throw IllegalArgumentException("New email is the same as the current one")
        }

        appUserRepository.findByEmail(email)?.let {
            if (it.id != appUser.id)
            {
                throw IllegalArgumentException("Email already exists")
            }
        }

        val verificationCode = otpService.generateEmailOtp()
        appUser.pendingEmail = email
        appUser.pendingEmailVerificationCode = verificationCode
        appUserRepository.update(appUser)

        emailService.sendEmail(
            email,
            "${configurationService.getAppEmailSubjectTitle()} Email Verification",
            "Your verification code is: $verificationCode"
        )
    }

    fun completeEmailUpdate(email: String?, verificationCode: String?)
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (email.isNullOrBlank())
        {
            throw IllegalArgumentException("Email cannot be null or blank")
        }

        if (verificationCode.isNullOrBlank())
        {
            throw IllegalArgumentException("Verification code cannot be null or blank")
        }

        if (appUser.pendingEmail != email)
        {
            throw IllegalArgumentException("Email does not match the pending email")
        }

        if (appUser.pendingEmailVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        appUser.email = email
        appUser.pendingEmail = null
        appUser.pendingEmailVerificationCode = null
        appUser.emailVerificationComplete = true
        appUserRepository.update(appUser)

        signOutService.signOut(outOfAllDevices = true)
    }

    fun hasLinkedSharingSessions(appUserId: UUID): Boolean
    {
        return sharingSessionService.getSharingSessionsLinkedToAppUserId(appUserId).isNotEmpty()
    }

    fun delete(appUserId: String?)
    {
        val appUser = appUserRepository.findById(UUID.fromString(appUserId ?: throw IllegalArgumentException("App user ID cannot be null")))

        if (appUser == null)
        {
            throw IllegalArgumentException("App user not found")
        }

        if (hasLinkedSharingSessions(appUser.id))
        {
            throw IllegalArgumentException("Cannot delete app user with linked sharing sessions")
        }

        appUserRepository.delete(appUser)
    }
}
