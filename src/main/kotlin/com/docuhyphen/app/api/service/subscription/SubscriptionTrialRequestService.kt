package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequest
import com.docuhyphen.app.api.model.entity.SubscriptionTrialRequestStatus
import com.docuhyphen.app.api.repository.subscription.SubscriptionTrialRequestRepository
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.AppAdminNotificationService
import com.docuhyphen.app.api.service.organization.OrganizationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.PersistenceException
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SubscriptionTrialRequestService @Inject constructor(
    private val repository: SubscriptionTrialRequestRepository,
    private val eligibilityService: SubscriptionTrialRequestEligibilityService,
    private val userRoleService: UserRoleService,
    private val notificationService: InAppNotificationService,
    private val appUserService: AppUserService,
    private val organizationService: OrganizationService,
    private val appAdminNotificationService: AppAdminNotificationService,
)
{
    @Transactional
    fun create(
        ownerType: SubscriptionOwnerType,
        ownerId: UUID,
        requesterId: UUID,
        note: String?,
    ): SubscriptionTrialRequestView
    {
        val eligibility = eligibilityService.evaluateForRequest(ownerType, ownerId)
        require(eligibility.eligible) { eligibility.reason ?: "This subscription is not eligible for a trial" }
        if (repository.findPending(ownerType.name, ownerId) != null)
        {
            throw SubscriptionTrialRequestConflictException("A trial request is already pending")
        }
        val now = Timestamp.from(Instant.now())
        val request = try
        {
            repository.insertAndFlush(
                SubscriptionTrialRequest().apply {
                    this.ownerType = ownerType.name
                    this.ownerId = ownerId
                    requestedByAppUserId = requesterId
                    planCode = targetPlan(ownerType).name
                    status = SubscriptionTrialRequestStatus.PENDING.name
                    requestNote = normalizeOptionalNote(note)
                    requestedAt = now
                    updatedAt = now
                },
            )
        }
        catch (exception: PersistenceException)
        {
            if (hasUniqueViolation(exception))
            {
                throw SubscriptionTrialRequestConflictException("A trial request is already pending", exception)
            }
            throw exception
        }
        val view = view(request)
        notifyAppAdministrators(view)
        runCatching {
            appAdminNotificationService.notifySubscriptionTrialRequest(
                requesterName = view.requesterName,
                requesterEmail = view.requesterEmail,
                ownerName = view.ownerName,
                planCode = view.request.planCode,
                requestId = view.request.id.toString(),
            )
        }.onFailure {
            logger.warn("Failed to notify an App Administrator about trial request {}", view.request.id, it)
        }
        return view
    }

    @Transactional
    fun current(ownerType: SubscriptionOwnerType, ownerId: UUID): CurrentSubscriptionTrialRequest
    {
        val latest = repository.findLatest(ownerType.name, ownerId)?.let(::view)
        if (latest?.request?.status == SubscriptionTrialRequestStatus.PENDING.name)
        {
            return CurrentSubscriptionTrialRequest(
                SubscriptionTrialRequestEligibility(false, "A trial request is awaiting review"),
                latest,
            )
        }
        return CurrentSubscriptionTrialRequest(eligibilityService.evaluateForRequest(ownerType, ownerId), latest)
    }

    fun list(status: SubscriptionTrialRequestStatus?, limit: Int, offset: Int): SubscriptionTrialRequestPage
    {
        require(limit in 1..100) { "Limit must be between 1 and 100" }
        require(offset >= 0) { "Offset cannot be negative" }
        return SubscriptionTrialRequestPage(
            total = repository.count(status),
            limit = limit,
            offset = offset,
            items = repository.list(status, limit, offset).map(::view),
        )
    }

    fun view(request: SubscriptionTrialRequest): SubscriptionTrialRequestView
    {
        val requester = appUserService.getByIdWithPerson(request.requestedByAppUserId)
            ?: throw IllegalStateException("Trial requester no longer exists")
        val requesterName = listOfNotNull(requester.person?.firstName, requester.person?.lastName)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { requester.email }
        val ownerName = if (request.ownerType == SubscriptionOwnerType.ORGANIZATION.name)
        {
            organizationService.getOrganizationById(request.ownerId).name
        }
        else requesterName
        return SubscriptionTrialRequestView(request, ownerName, requesterName, requester.email)
    }

    fun notifyDecision(view: SubscriptionTrialRequestView)
    {
        val approved = view.request.status == SubscriptionTrialRequestStatus.APPROVED.name
        notificationService.publishAdministrative(
            appUserId = view.request.requestedByAppUserId,
            type = if (approved) "subscription_trial_request.approved" else "subscription_trial_request.rejected",
            title = if (approved) "Trial request approved" else "Trial request declined",
            message = if (approved)
                "Your ${view.request.planCode.lowercase().replaceFirstChar(Char::uppercase)} trial is ready."
            else "Your trial request was declined. Review the decision in Billing.",
            data = mapOf(
                "trialRequestId" to view.request.id.toString(),
                "status" to view.request.status,
                "ownerType" to view.request.ownerType,
                "ownerId" to view.request.ownerId.toString(),
            ),
        )
    }

    private fun notifyAppAdministrators(view: SubscriptionTrialRequestView)
    {
        userRoleService.activeAppAdminIds().forEach { adminId ->
            notificationService.publishAdministrative(
                appUserId = adminId,
                type = "subscription_trial_request.created",
                title = "New trial request",
                message = "${view.requesterName} requested a ${view.request.planCode.lowercase().replaceFirstChar(Char::uppercase)} trial for ${view.ownerName}.",
                data = mapOf(
                    "trialRequestId" to view.request.id.toString(),
                    "ownerType" to view.request.ownerType,
                    "ownerId" to view.request.ownerId.toString(),
                ),
            )
        }
    }

    private fun targetPlan(ownerType: SubscriptionOwnerType): PlanCode =
        if (ownerType == SubscriptionOwnerType.USER) PlanCode.PERSONAL else PlanCode.BUSINESS

    private fun hasUniqueViolation(exception: PersistenceException): Boolean
    {
        var cause: Throwable? = exception
        while (cause != null)
        {
            if (cause is SQLException && cause.sqlState == UNIQUE_VIOLATION_SQL_STATE)
            {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun normalizeOptionalNote(note: String?): String?
    {
        val normalized = note?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length <= 1024) { "Trial request note must be at most 1024 characters" }
        return normalized
    }

    companion object
    {
        private val logger = LoggerFactory.getLogger(SubscriptionTrialRequestService::class.java)
        private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}
