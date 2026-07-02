package com.docuhyphen.app.api.service

import com.docuhyphen.app.api.exception.InvalidOrganizationRegistrationException
import com.docuhyphen.app.api.exception.InvalidPersonRegistrationException
import com.docuhyphen.app.api.exception.OrganizationAlreadyExistsException
import com.docuhyphen.app.api.exception.PersonAlreadyExistsException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.messaging.OrganizationVerificationProducer
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.PersonRepositoryRepository
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.auth.AppRoleAssignmentService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@RequestScoped
class EntityRegistrationService @Inject constructor(
    private val personRepository: PersonRepositoryRepository,
    private val organizationRepository: OrganizationRepository,
    private val appUserService: AppUserService,
    private var configurationService: ConfigurationService,
    private var emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val organizationVerificationProducer: OrganizationVerificationProducer,
    private val organizationMembershipService: OrganizationMembershipService,
    private val appRoleAssignmentService: AppRoleAssignmentService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        val logger = LoggerFactory.getLogger(EntityRegistrationService::class.java)
    }

    @Inject
    private lateinit var authTokenContext: AuthTokenContext

    @Transactional
    fun registerPerson(
        firstName: String?, lastName: String?, identificationNumber: String?, idType: String?
    ): Person
    {
        if (firstName.isNullOrBlank() || lastName.isNullOrBlank())
        {
            val firstNameErrorMessage = if (firstName.isNullOrBlank()) "First name is blank." else ""
            val lastNameErrorMessage = if (lastName.isNullOrBlank()) "Last name is blank." else ""

            val errorMessage =
                listOf(firstNameErrorMessage, lastNameErrorMessage).filter { it.isNotEmpty() }.joinToString(" ")

            logger.warn("Person registration failed: $errorMessage")
            throw InvalidPersonRegistrationException(errorMessage)
        }

        var personIDType: PersonIDType? = null

        if (!identificationNumber.isNullOrBlank())
        {
            if (personRepository.existsByIdentificationNumber(identificationNumber))
            {
                logger.warn("Person registration failed: Identification number $identificationNumber already exists.")
                throw PersonAlreadyExistsException()
            }

            if (idType == null)
            {
                logger.warn("Person registration failed: ID type is null.")
                throw InvalidPersonRegistrationException("ID type is null.")
            }
            personIDType = PersonIDType.valueOf(idType)
        }

        val person = Person().apply {
            this.firstName = firstName
            this.lastName = lastName
            this.personIDType = personIDType
            this.identificationNumber = identificationNumber
            this.contactDetails = ContactDetails()
        }

        personRepository.save(person)
        appUserService.updatePerson(authTokenContext.authToken.appUser!!, person)

        logger.info("Person registration successful for identification number $identificationNumber.")

        return person
    }

    @Transactional
    fun registerOrganization(
        organizationName: String?,
        registrationNumber: String?,
        phoneNumber: String?,
        email: String?
    ): Organization
    {
        if (organizationName.isNullOrBlank() || registrationNumber.isNullOrBlank())
        {
            val organizationNameErrorMessage =
                if (organizationName.isNullOrBlank()) "Organization name is blank." else ""
            val registrationNumberErrorMessage =
                if (registrationNumber.isNullOrBlank()) "Registration number is blank." else ""

            val errorMessage =
                listOf(organizationNameErrorMessage, registrationNumberErrorMessage).filter { it.isNotEmpty() }
                    .joinToString(" ")

            logger.warn("Organization registration failed: $errorMessage")
            throw InvalidOrganizationRegistrationException(errorMessage)
        }

        if (organizationRepository.existsByRegistrationNumber(registrationNumber))
        {
            logger.warn("Organization registration failed: Registration number $registrationNumber already exists.")
            throw OrganizationAlreadyExistsException()
        }

        val appUser = authTokenContext.authToken.appUser!!

        if (appUser.person != null)
        {
            val existing = organizationRepository.findByAppUserIdAndPersonId(appUser.id, appUser.person!!.id)
            if (existing != null)
            {
                logger.warn(
                    "Organization registration failed: user {} already belongs to organization {} (verificationComplete={})",
                    appUser.id, existing.id, existing.verificationComplete
                )
                throw InvalidOrganizationRegistrationException(
                    if (existing.verificationComplete)
                        "You are already part of a registered organization."
                    else
                        "You already have a registration in progress that is awaiting verification."
                )
            }
        }
        entityManager.detach(appUser)
        val managedAppUser = entityManager.merge(appUser)

        val organization = Organization().apply {
            this.name = organizationName
            this.registrationNumber = registrationNumber
            this.isActive = false
            this.verificationComplete = false
            this.contactDetails = ContactDetails().apply {
                this.email = email
                this.phoneNumber = phoneNumber
            }
            // Org→user binding is recorded via organization_membership below, not the retired
            // Organization.appUsers join. managedAppUser is already persisted (merged above).
        }

        organizationRepository.save(organization)

        // The registering user is the organization's first admin (replaces the legacy
        // AppUser.role = ORG_ADMIN assignment).
        organizationMembershipService.assignOrgRole(
            appUserId = managedAppUser.id,
            organizationId = organization.id,
            role = OrganizationRoleName.ORG_ADMIN,
            isPrimary = true,
        )
        organizationMembershipService.assignOrgRole(
            appUserId = managedAppUser.id,
            organizationId = organization.id,
            role = OrganizationRoleName.ORG_MEMBER,
        )

        // If no global App Admin exists yet, promote the org creator as the first one.
        if (appRoleAssignmentService.listAppAdmins().isEmpty())
        {
            appRoleAssignmentService.grantAppRole(
                targetAppUserId = managedAppUser.id,
                roleName = AppRoleName.APP_ADMIN,
                actorId = null,
            )
            logger.info("Bootstrapped first APP_ADMIN from organization registration for user {}", managedAppUser.id)
        }

        logger.info("Organization registration successful for registration number $registrationNumber.")

        val emailBody = emailTemplateService.renderOrganizationRegistrationEmail(
            firstName = appUser.person!!.firstName!!,
            lastName = appUser.person!!.lastName!!,
            organizationName = organizationName,
            registrationNumber = registrationNumber,
            organizationEmail = email,
            organizationPhone = phoneNumber,
        )

        emailService.sendEmail(
            appUser.email,
            "${configurationService.emailSubjectTitle} | Organization Registration",
            emailBody,
            useHtml = true,
        )

        runCatching {
            val notificationBody = emailTemplateService.renderNewOrgRegistrationNotificationEmail(
                firstName = appUser.person!!.firstName!!,
                lastName = appUser.person!!.lastName!!,
                orgName = organizationName,
                registrationNumber = registrationNumber,
                orgEmail = email,
                orgPhone = phoneNumber,
            )
            emailService.sendEmail(
                to = configurationService.getNewOrgNotificationEmail(),
                subject = "${configurationService.emailSubjectTitle} | New Organization Registration",
                body = notificationBody,
                useHtml = true,
            )
        }.onFailure { logger.warn("Failed to send new-org internal notification email for org {}", registrationNumber, it) }

        organizationVerificationProducer.sendToQueue(organization)

        return organization
    }
}
