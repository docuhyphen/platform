package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Sends operational emails that require App Administrator awareness to the earliest effective
 * APP_ADMIN account in the database. Recipient resolution is centralized here so registration,
 * organization, subscription, and public enquiry flows use one deterministic delivery policy.
 */
@ApplicationScoped
class AppAdminNotificationService @Inject constructor(
    private val userRoleService: UserRoleService,
    private val appUserService: AppUserService,
    private val emailService: EmailService,
)
{
    fun notifyNewUserRegistration(email: String, registrationMethod: String = "Email"): Boolean =
        send(
            subject = "New User Registration",
            body = """
                A new user has registered on DocuHyphen.

                Email: $email
                Registration method: $registrationMethod
            """.trimIndent(),
        )

    fun notifyNewOrganizationRegistration(
        contactName: String,
        organizationName: String,
        registrationNumber: String,
        organizationEmail: String?,
        organizationPhone: String?,
    ): Boolean = send(
        subject = "New Organization Registration",
        body = buildList {
            add("A new organization has registered on DocuHyphen and is awaiting verification.")
            add("")
            add("Organization: $organizationName")
            add("Registration number: $registrationNumber")
            add("Contact name: $contactName")
            organizationEmail?.takeIf(String::isNotBlank)?.let { add("Organization email: $it") }
            organizationPhone?.takeIf(String::isNotBlank)?.let { add("Phone: $it") }
        }.joinToString("\n"),
    )

    fun notifyOrganizationMemberAdded(
        memberEmail: String,
        organizationName: String,
        roles: Collection<String>,
        createdPlatformAccount: Boolean,
    ): Boolean = send(
        subject = "Organization Member Added",
        body = """
            An organization member was added on DocuHyphen.

            Member email: $memberEmail
            Organization: $organizationName
            Roles: ${roles.sorted().joinToString(", ")}
            New platform account: ${if (createdPlatformAccount) "Yes" else "No"}
        """.trimIndent(),
    )

    fun notifySubscriptionTrialRequest(
        requesterName: String,
        requesterEmail: String,
        ownerName: String,
        planCode: String,
        requestId: String,
    ): Boolean = send(
        subject = "New Subscription Trial Request",
        body = """
            A subscription trial request is awaiting App Administrator review.

            Request ID: $requestId
            Requester: $requesterName
            Requester email: $requesterEmail
            Owner: $ownerName
            Requested plan: $planCode
        """.trimIndent(),
    )

    fun send(subject: String, body: String, useHtml: Boolean = false): Boolean
    {
        val recipient = firstAppAdminEmail()
        if (recipient == null)
        {
            logger.warn("App Administrator notification skipped because no effective APP_ADMIN email is available")
            return false
        }
        emailService.sendEmail(
            to = recipient,
            subject = subject,
            body = body,
            useHtml = useHtml,
        )
        return true
    }

    private fun firstAppAdminEmail(): String? =
        userRoleService.firstActiveAppAdminId()
            ?.let(appUserService::getById)
            ?.email
            ?.trim()
            ?.takeIf(String::isNotBlank)

    companion object
    {
        private val logger = LoggerFactory.getLogger(AppAdminNotificationService::class.java)
    }
}
