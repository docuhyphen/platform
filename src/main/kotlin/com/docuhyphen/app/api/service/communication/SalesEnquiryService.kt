package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.exception.SalesEnquiryRateLimitedException
import com.docuhyphen.app.api.exception.SalesEnquiryRecipientUnavailableException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.resource.model.SalesEnquiryRequest
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Handles public "Speak to Sales" enquiries submitted from the marketing site. It applies spam
 * protection and validation, then routes a valid enquiry by email to the first active platform
 * administrator so a human can follow up.
 */
@ApplicationScoped
class SalesEnquiryService @Inject constructor(
    private val appRoleAssignmentRepository: AppRoleAssignmentRepository,
    private val appUserService: AppUserService,
    private val emailService: EmailService,
    private val rateLimiter: SalesEnquiryRateLimiter,
    private val authTokenContext: AuthTokenContext,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SalesEnquiryService::class.java)

        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        private const val MAX_SHORT_FIELD = 150
        private const val MAX_EMAIL = 254
        private const val MAX_MESSAGE = 2000
    }

    fun submitEnquiry(request: SalesEnquiryRequest)
    {
        // Honeypot: real users never see or fill this field. A filled value is automated spam,
        // so accept the request silently without routing or notifying anyone.
        if (!request.website.isNullOrBlank())
        {
            logger.info("Discarding sales enquiry that tripped the honeypot")
            return
        }

        enforceRateLimit()

        val sanitised = validateAndSanitise(request)

        val recipient = findFirstAppAdminEmail()
            ?: throw SalesEnquiryRecipientUnavailableException(
                "No administrator is currently available to receive this enquiry",
            )

        emailService.sendEmail(
            to = recipient,
            subject = "New sales enquiry from ${sanitised.organizationName}",
            body = buildEmailBody(sanitised),
            useHtml = true,
        )

        logger.info("Routed sales enquiry from {} to a platform administrator", sanitised.workEmail)
    }

    private fun enforceRateLimit()
    {
        val sourceKey = authTokenContext.clientIp?.takeIf { it.isNotBlank() } ?: "unknown"
        rateLimiter.registerAndCheck(sourceKey)?.let { retryAfterSeconds ->
            throw SalesEnquiryRateLimitedException(
                "Too many enquiries have been submitted. Please try again later.",
                retryAfterSeconds = retryAfterSeconds,
            )
        }
    }

    private fun validateAndSanitise(request: SalesEnquiryRequest): SalesEnquiryRequest
    {
        val firstName = requireShortText(request.firstName, "First name")
        val lastName = requireShortText(request.lastName, "Last name")
        val organizationName = requireShortText(request.organizationName, "Organization name")
        val organizationType = requireShortText(request.organizationType, "Organization type")
        val companySize = requireShortText(request.companySize, "Company size")

        val workEmail = request.workEmail?.trim().orEmpty()
        if (workEmail.isBlank())
        {
            throw IllegalArgumentException("Work email is required")
        }
        if (workEmail.length > MAX_EMAIL || !EMAIL_PATTERN.matches(workEmail))
        {
            throw IllegalArgumentException("A valid work email is required")
        }

        val phone = request.phone?.trim()?.take(MAX_SHORT_FIELD)
        val country = request.country?.trim()?.take(MAX_SHORT_FIELD)

        val message = request.message?.trim()
        if (message != null && message.length > MAX_MESSAGE)
        {
            throw IllegalArgumentException("Message is too long")
        }

        return SalesEnquiryRequest(
            firstName = firstName,
            lastName = lastName,
            workEmail = workEmail,
            phone = phone?.takeIf { it.isNotBlank() },
            organizationName = organizationName,
            organizationType = organizationType,
            companySize = companySize,
            country = country?.takeIf { it.isNotBlank() },
            message = message?.takeIf { it.isNotBlank() },
        )
    }

    private fun requireShortText(value: String?, label: String): String
    {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank())
        {
            throw IllegalArgumentException("$label is required")
        }
        if (trimmed.length > MAX_SHORT_FIELD)
        {
            throw IllegalArgumentException("$label is too long")
        }
        return trimmed
    }

    private fun findFirstAppAdminEmail(): String?
    {
        return appRoleAssignmentRepository.findActiveAppAdmins()
            .sortedBy { it.grantedAt }
            .asSequence()
            .mapNotNull { appUserService.getById(it.appUserId) }
            .firstOrNull { it.email.isNotBlank() }
            ?.email
    }

    private fun buildEmailBody(enquiry: SalesEnquiryRequest): String
    {
        val rows = buildList {
            add("Name" to "${enquiry.firstName} ${enquiry.lastName}")
            add("Work email" to enquiry.workEmail)
            enquiry.phone?.let { add("Phone" to it) }
            add("Organization" to enquiry.organizationName)
            add("Organization type" to enquiry.organizationType)
            add("Company size" to enquiry.companySize)
            enquiry.country?.let { add("Country / Region" to it) }
        }

        val detailRows = rows.joinToString("\n") { (label, value) ->
            "<tr><td style=\"padding:4px 12px 4px 0;font-weight:600;\">${escapeHtml(label)}</td>" +
                "<td style=\"padding:4px 0;\">${escapeHtml(value.orEmpty())}</td></tr>"
        }

        val messageBlock = enquiry.message
            ?.let { "<p style=\"margin-top:16px;\"><strong>Message</strong><br/>${escapeHtml(it)}</p>" }
            .orEmpty()

        return """
            <div>
              <p>A new sales enquiry has been submitted from the website.</p>
              <table style="border-collapse:collapse;">$detailRows</table>
              $messageBlock
            </div>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

