package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.service.application.SettingsService
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.PersonBasicDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AppUserSettings
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.auth.ServiceActionAuthorizationService
import com.docuhyphen.app.api.service.auth.SignOutService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@RequestScoped
class AppUserService @Inject constructor(
    val appUserRepository: AppUserRepository,
    val organizationRepository: OrganizationRepository,
    val settingsService: SettingsService,
    val authTokenContext: AuthTokenContext,
    val otpService: OtpService,
    val emailService: EmailService,
    val emailTemplateService: EmailTemplateService,
    val configurationService: ConfigurationService,
    val signOutService: SignOutService,
    val exchangeService: ExchangeRetrievalService,
    val serviceActionAuthorizationService: ServiceActionAuthorizationService,
    val subscriptionPolicyService: SubscriptionPolicyService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserService::class.java)
        private val UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    }

    fun getById(id: UUID): AppUser?
    {
        return appUserRepository.findById(id)
    }

    fun getByIdWithPerson(id: UUID): AppUser?
    {
        return appUserRepository.findByIdWithPerson(id)
    }

    fun findByEmail(email: String): AppUser?
    {
        return appUserRepository.findByEmail(email)
    }

    fun findRegisteredByEmail(email: String): AppUser?
    {
        return appUserRepository.findActiveByEmail(email)
    }

    fun searchActiveUsers(query: String, limit: Int = 20): List<AppUser>
    {
        return appUserRepository.searchActiveUsers(query, limit)
    }

    fun findForSubscriptionAdministration(query: String?, limit: Int, offset: Int): List<AppUser>
    {
        return appUserRepository.findForSubscriptionAdministration(query, limit, offset)
    }

    fun countForSubscriptionAdministration(query: String?): Long
    {
        return appUserRepository.countForSubscriptionAdministration(query)
    }

    fun create(user: AppUser): AppUser
    {
        val saved = appUserRepository.save(user)
        provisionSubscriptionIfRegistered(saved)
        return saved
    }

    /**
     * Gives a registered account its individual subscription record. Temporary recipient
     * placeholders and machine accounts are skipped: neither is a subscriber and neither
     * consumes a paid seat.
     */
    fun provisionSubscriptionIfRegistered(user: AppUser)
    {
        if (user.isTemporary || user.application != null)
        {
            return
        }

        runCatching { subscriptionPolicyService.ensureUserPolicy(user.id) }
            .onFailure { logger.warn("Failed to create subscription record for app user {}", user.id, it) }
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

        appUser.person?.id?.let { personId ->
            organizationRepository.findByAppUserIdAndPersonId(appUser.id, personId)?.let { org ->
                serviceActionAuthorizationService.validateUserProfileUpdate(appUser, org)
            }
        }

        val newFirst = personDto.firstName.trim()
        val newLast = personDto.lastName.trim()

        // Mutate the existing Person in place rather than replacing the reference.
        // Replacing would orphan the existing person row and (with cascade=ALL) make
        // Hibernate try to re-INSERT the old id, blowing up on the person_pkey unique
        // constraint. If the user somehow has no Person yet, create one for them.
        val person = appUser.person ?: Person().also { appUser.person = it }

        val changes = mutableListOf<String>()
        if (person.firstName != newFirst)
        {
            changes.add("First name changed from \"${person.firstName ?: ""}\" to \"$newFirst\"")
            person.firstName = newFirst
        }
        if (person.lastName != newLast)
        {
            changes.add("Last name changed from \"${person.lastName ?: ""}\" to \"$newLast\"")
            person.lastName = newLast
        }

        if (changes.isEmpty())
        {
            return
        }

        appUserRepository.update(appUser)

        sendProfileUpdatedEmail(appUser, changes)
    }

    fun initiateEmailUpdate(email: String?)
    {
        val appUser = authTokenContext.authToken.appUser!!

        appUser.person?.id?.let { personId ->
            organizationRepository.findByAppUserIdAndPersonId(appUser.id, personId)?.let { org ->
                serviceActionAuthorizationService.validateUserEmailUpdate(appUser, org)
            }
        }

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

        // Step 1 of two-step flow: send a code to the OLD email.
        val oldEmailCode = otpService.generateEmailOtp()
        appUser.pendingEmail = email
        appUser.pendingEmailOldVerificationCode = oldEmailCode
        appUser.pendingEmailOldVerified = false
        appUser.pendingEmailVerificationCode = null
        appUserRepository.update(appUser)

        try
        {
            val body = emailTemplateService.renderEmailUpdateOldVerificationEmail(
                newEmail = email,
                verificationCode = oldEmailCode,
                expiryMinutes = configurationService.getSignUpOtpExpiryMins(),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "Confirm email change",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send email-update OLD verification to {}", appUser.email, e)
        }
    }

    fun confirmOldEmailForUpdate(verificationCode: String?)
    {
        val appUser = authTokenContext.authToken.appUser!!

        if (verificationCode.isNullOrBlank())
        {
            throw IllegalArgumentException("Verification code cannot be null or blank")
        }

        if (appUser.pendingEmail.isNullOrBlank())
        {
            throw IllegalArgumentException("No pending email change to confirm")
        }

        if (appUser.pendingEmailOldVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        val newEmailCode = otpService.generateEmailOtp()
        appUser.pendingEmailOldVerified = true
        appUser.pendingEmailVerificationCode = newEmailCode
        appUserRepository.update(appUser)

        try
        {
            val body = emailTemplateService.renderEmailUpdateNewVerificationEmail(
                newEmail = appUser.pendingEmail!!,
                verificationCode = newEmailCode,
                expiryMinutes = configurationService.getSignUpOtpExpiryMins(),
            )
            emailService.sendEmail(
                to = appUser.pendingEmail!!,
                subject = "Verify your new email",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send email-update NEW verification to {}", appUser.pendingEmail, e)
        }
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

        if (appUser.pendingEmailOldVerified != true)
        {
            throw IllegalArgumentException("Current email must be verified before completing the change")
        }

        if (appUser.pendingEmailVerificationCode != verificationCode)
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        val previousEmail = appUser.email
        appUser.email = email
        appUser.pendingEmail = null
        appUser.pendingEmailVerificationCode = null
        appUser.pendingEmailOldVerificationCode = null
        appUser.pendingEmailOldVerified = false
        appUser.emailVerificationComplete = true
        appUserRepository.update(appUser)

        try
        {
            val body = emailTemplateService.renderEmailUpdateCompletionEmail(
                oldEmail = previousEmail,
                newEmail = email,
            )
            emailService.sendEmail(
                to = previousEmail,
                subject = "Email address changed",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send email-update completion notice to {}", previousEmail, e)
        }

        signOutService.signOut(outOfAllDevices = true)
    }

    fun hasLinkedExchanges(appUserId: UUID): Boolean
    {
        return exchangeService.getExchangesLinkedToAppUserId(appUserId).isNotEmpty()
    }

    fun delete(appUserId: String?)
    {
        val appUser = appUserRepository.findById(UUID.fromString(appUserId ?: throw IllegalArgumentException("App user ID cannot be null")))

        if (appUser == null)
        {
            throw IllegalArgumentException("App user not found")
        }

        if (hasLinkedExchanges(appUser.id))
        {
            throw IllegalArgumentException("Cannot delete app user with linked exchanges")
        }

        val deletedEmail = appUser.email
        appUserRepository.delete(appUser)

        try
        {
            val body = emailTemplateService.renderAccountDeletedEmail(
                email = deletedEmail,
                deletedAt = UTC_FORMATTER.format(Instant.now()),
            )
            emailService.sendEmail(
                to = deletedEmail,
                subject = "Account deleted",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send account-deleted email to {}", deletedEmail, e)
        }
    }

    private fun sendProfileUpdatedEmail(appUser: AppUser, updatedFields: List<String>)
    {
        try
        {
            val body = emailTemplateService.renderProfileUpdatedEmail(updatedFields)
            emailService.sendEmail(
                to = appUser.email,
                subject = "Profile updated",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send profile-updated email to {}", appUser.email, e)
        }
    }

}
