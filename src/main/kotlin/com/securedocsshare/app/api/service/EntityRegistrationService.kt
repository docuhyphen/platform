package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.exception.CompanyAlreadyExistsException
import com.securedocsshare.app.api.exception.InvalidCompanyRegistrationException
import com.securedocsshare.app.api.exception.InvalidPersonRegistrationException
import com.securedocsshare.app.api.exception.PersonAlreadyExistsException
import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.messaging.CompanyVerificationProducer
import com.securedocsshare.app.api.model.*
import com.securedocsshare.app.api.repository.CompanyRepository
import com.securedocsshare.app.api.repository.PersonRepositoryRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@RequestScoped
class EntityRegistrationService @Inject constructor(
    private val personRepository: PersonRepositoryRepository,
    private val companyRepository: CompanyRepository,
    private val appUserService: AppUserService,
    private var configurationService: ConfigurationService,
    private var emailService: EmailService,
    private val companyVerificationProducer: CompanyVerificationProducer
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
        firstName: String?, lastName: String?, identificationNumber: String?, idType: PersonIDType?
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
        }

        val person = Person().apply {
            this.firstName = firstName
            this.lastName = lastName
            this.personIDType = idType
            this.identificationNumber = identificationNumber
        }

        personRepository.save(person)
        appUserService.updatePerson(authTokenContext.authToken.appUser!!, person)

        logger.info("Person registration successful for identification number $identificationNumber.")

        return person
    }

    @Transactional
    fun registerCompany(companyName: String?, registrationNumber: String?): Company
    {
        if (companyName.isNullOrBlank() || registrationNumber.isNullOrBlank())
        {
            val companyNameErrorMessage = if (companyName.isNullOrBlank()) "Company name is blank." else ""
            val registrationNumberErrorMessage =
                if (registrationNumber.isNullOrBlank()) "Registration number is blank." else ""

            val errorMessage =
                listOf(companyNameErrorMessage, registrationNumberErrorMessage).filter { it.isNotEmpty() }
                    .joinToString(" ")

            logger.warn("Company registration failed: $errorMessage")
            throw InvalidCompanyRegistrationException(errorMessage)
        }

        if (companyRepository.existsByRegistrationNumber(registrationNumber))
        {
            logger.warn("Company registration failed: Registration number $registrationNumber already exists.")
            throw CompanyAlreadyExistsException()
        }

        val appUser = authTokenContext.authToken.appUser!!
        entityManager.detach(appUser)
        val managedAppUser = entityManager.merge(appUser)

        managedAppUser.role = AppUserRole.ADMIN
        entityManager.merge(managedAppUser)

        val company = Company().apply {
            this.name = companyName
            this.registrationNumber = registrationNumber
            this.isActive = false
            this.verificationComplete = false
            this.appUsers = mutableListOf(managedAppUser)
        }

        companyRepository.save(company)
        logger.info("Company registration successful for registration number $registrationNumber.")

        emailService.sendEmail(
            appUser.email,
            "${configurationService.getAppEmailSubjectTitle()} | Company registration",
            "Hi ${appUser.person!!.firstName} ${appUser.person!!.lastName},\n\n" +
                    "Your company registration request has been received. " +
                    "Please wait for the administrator to approve your request.\n\n" +
                    "Thank you for using ${configurationService.getAppEmailSubjectTitle()}!"
        )

        companyVerificationProducer.sendToQueue(company)

        return company
    }
}
