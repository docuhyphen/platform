package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestLineageRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestScheduledRefreshService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val successorService: InformationRequestSuccessorService,
    private val refreshRuleRepository: InformationRequestRefreshRuleRepository,
    private val lineageRepository: InformationRequestLineageRepository,
) {
    @Transactional
    fun refreshDue(refreshRuleId: UUID, now: Instant): Boolean {
        val rule = refreshRuleRepository.findById(refreshRuleId) ?: return false
        val locked = gate.lock(rule.informationRequestId)
        val source = refreshRuleRepository.findRefreshSource(rule.informationRequestId, rule.requirementKey)
            ?.takeIf { InformationRequestRefreshSchedule.isDue(rule, it, now) }
            ?.takeUnless { lineageRepository.existsRefresh(rule.id, it.packageId) }
            ?: return false
        successorService.follow(
            locked,
            InformationRequestRefreshSchedule.followUpOf(rule, source),
            SCHEDULED_REFRESH_ACCESS,
            "information_request.refresh|${rule.id}|${source.packageId}",
        )
        refreshRuleRepository.clearRetry(rule)
        return true
    }

    @Transactional
    fun deferRefresh(refreshRuleId: UUID, now: Instant): Instant? {
        val rule = refreshRuleRepository.findByIdForUpdate(refreshRuleId) ?: return null
        rule.attemptCount += 1
        val nextAttemptAt = InformationRequestRefreshSchedule.nextAttemptAt(now, rule.attemptCount)
        rule.nextAttemptAt = Timestamp.from(nextAttemptAt)
        refreshRuleRepository.update(rule)
        return nextAttemptAt
    }

    private companion object {
        val SCHEDULED_REFRESH_ACCESS = RequestAccessContext(
            PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)),
            AuthorizationContext(),
        )
    }
}
