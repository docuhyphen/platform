package com.dochyphen.app.api.service

import com.dochyphen.app.api.exception.InvalidOrganizationRegistrationException
import com.dochyphen.app.api.exception.InvalidPersonRegistrationException
import com.dochyphen.app.api.exception.OrganizationAlreadyExistsException
import com.dochyphen.app.api.exception.PersonAlreadyExistsException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.messaging.OrganizationVerificationProducer
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.repository.OrganizationRepository
import com.dochyphen.app.api.repository.PersonRepositoryRepository
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.config.ConfigurationService
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
    private val organizationVerificationProducer: OrganizationVerificationProducer
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
        entityManager.detach(appUser)
        val managedAppUser = entityManager.merge(appUser)

        managedAppUser.role = AppUserRole.ADMIN
        entityManager.merge(managedAppUser)

        val organization = Organization().apply {
            this.name = organizationName
            this.registrationNumber = registrationNumber
            this.isActive = false
            this.verificationComplete = false
            this.contactDetails = ContactDetails().apply {
                this.email = email
                this.phoneNumber = phoneNumber
            }
            this.appUsers = mutableListOf(managedAppUser)
        }

        organizationRepository.save(organization)
        logger.info("Organization registration successful for registration number $registrationNumber.")

        emailService.sendEmail(
            appUser.email,
            "${configurationService.getAppEmailSubjectTitle()} | Organization registration",
            "Hi ${appUser.person!!.firstName} ${appUser.person!!.lastName},\n\n" +
                    "Your Organization registration request has been received. " +
                    "Please wait for the administrator to approve your request.\n\n" +
                    "Thank you for using ${configurationService.getAppEmailSubjectTitle()}!"
        )

        organizationVerificationProducer.sendToQueue(organization)

        return organization
    }
}
