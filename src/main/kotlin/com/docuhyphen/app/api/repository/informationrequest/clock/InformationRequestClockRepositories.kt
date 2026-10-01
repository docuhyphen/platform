package com.docuhyphen.app.api.repository.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestClockPolicyRepository :
    BaseRepository<InformationRequestClockPolicy>(InformationRequestClockPolicy::class.java)
{
    fun findForOwner(ownerType: InformationRequestOwnerType, ownerId: UUID): List<InformationRequestClockPolicy>
    {
        val ownerColumn =
            if (ownerType == InformationRequestOwnerType.ORGANIZATION) "ownerOrganizationId" else "ownerUserId"
        return entityManager.createQuery(
            "SELECT policy FROM InformationRequestClockPolicy policy WHERE policy.ownerType = :ownerType AND policy.$ownerColumn = :ownerId ORDER BY policy.policyKey",
            InformationRequestClockPolicy::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .resultList
    }

    fun findByKey(
        ownerType: InformationRequestOwnerType,
        ownerId: UUID,
        policyKey: String
    ): InformationRequestClockPolicy? =
        findForOwner(ownerType, ownerId).firstOrNull { it.policyKey == policyKey }
}

@ApplicationScoped
class InformationRequestClockPolicyVersionRepository :
    BaseRepository<InformationRequestClockPolicyVersion>(InformationRequestClockPolicyVersion::class.java)
{
    fun findForPolicy(policyId: UUID): List<InformationRequestClockPolicyVersion> =
        entityManager.createQuery(
            "SELECT version FROM InformationRequestClockPolicyVersion version WHERE version.policyId = :policyId ORDER BY version.versionNumber",
            InformationRequestClockPolicyVersion::class.java,
        )
            .setParameter("policyId", policyId)
            .resultList

    fun nextVersionNumber(policyId: UUID): Int = (findForPolicy(policyId).maxOfOrNull { it.versionNumber } ?: 0) + 1
}

@ApplicationScoped
class InformationRequestClockPolicyPeriodRepository :
    BaseRepository<InformationRequestClockPolicyPeriod>(InformationRequestClockPolicyPeriod::class.java)
{
    fun findForVersion(versionId: UUID): List<InformationRequestClockPolicyPeriod> =
        entityManager.createQuery(
            """
            SELECT period FROM InformationRequestClockPolicyPeriod period
            WHERE period.policyVersionId = :versionId
            ORDER BY period.dayOfWeek, period.startMinute
            """.trimIndent(),
            InformationRequestClockPolicyPeriod::class.java,
        )
            .setParameter("versionId", versionId)
            .resultList
}

@ApplicationScoped
class InformationRequestClockPolicyHolidayRepository :
    BaseRepository<InformationRequestClockPolicyHoliday>(InformationRequestClockPolicyHoliday::class.java)
{
    fun findForVersion(versionId: UUID): List<InformationRequestClockPolicyHoliday> =
        entityManager.createQuery(
            "SELECT holiday FROM InformationRequestClockPolicyHoliday holiday WHERE holiday.policyVersionId = :versionId ORDER BY holiday.holidayDate",
            InformationRequestClockPolicyHoliday::class.java,
        )
            .setParameter("versionId", versionId)
            .resultList
}

@ApplicationScoped
class InformationRequestClockPolicyReminderRepository :
    BaseRepository<InformationRequestClockPolicyReminder>(InformationRequestClockPolicyReminder::class.java)
{
    fun findForVersion(versionId: UUID): List<InformationRequestClockPolicyReminder> =
        entityManager.createQuery(
            """
            SELECT reminder FROM InformationRequestClockPolicyReminder reminder
            WHERE reminder.policyVersionId = :versionId
            ORDER BY reminder.reminderOrdinal
            """.trimIndent(),
            InformationRequestClockPolicyReminder::class.java,
        )
            .setParameter("versionId", versionId)
            .resultList
}

@ApplicationScoped
class InformationRequestClockRepository :
    BaseRepository<InformationRequestClock>(InformationRequestClock::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestClock> =
        entityManager.createQuery(
            "SELECT clock FROM InformationRequestClock clock WHERE clock.informationRequestId = :requestId ORDER BY clock.clockKey",
            InformationRequestClock::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequests(requestIds: Collection<UUID>): List<InformationRequestClock>
    {
        if (requestIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT clock FROM InformationRequestClock clock WHERE clock.informationRequestId IN :requestIds",
            InformationRequestClock::class.java,
        )
            .setParameter("requestIds", requestIds)
            .resultList
    }

    fun findDuePointClockIds(now: Timestamp, limit: Int): List<UUID> =
        entityManager.createQuery(
            """
            SELECT clock.id FROM InformationRequestClock clock
            WHERE clock.state = :running AND clock.nextPointAt IS NOT NULL AND clock.nextPointAt <= :now
            ORDER BY clock.nextPointAt
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("running", InformationRequestClockState.RUNNING)
            .setParameter("now", now)
            .setMaxResults(limit)
            .resultList

    fun findUnstoppedOfFinishedIds(limit: Int): List<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT clock.id
            FROM information_request_clock clock
                     JOIN information_request request ON request.id = clock.information_request_id
                     JOIN exchange parent ON parent.id = request.exchange_id
            WHERE clock.state <> 'STOPPED'
              AND (request.state IN ('CLOSED', 'CANCELLED', 'SUPERSEDED', 'EXPIRED')
                   OR parent.is_deleted
                   OR parent.status NOT IN ('INITIATED', 'ACCEPTED_STARTED'))
            ORDER BY clock.started_at, clock.id
            LIMIT :limit
            """.trimIndent(),
        )
            .setParameter("limit", limit)
            .resultList
            .map { it as UUID }

    fun findForUpdate(clockId: UUID): InformationRequestClock?
    {
        val clock = entityManager.find(InformationRequestClock::class.java, clockId) ?: return null
        entityManager.refresh(clock, LockModeType.PESSIMISTIC_WRITE)
        return clock
    }
}

@ApplicationScoped
class InformationRequestClockEventRepository :
    BaseRepository<InformationRequestClockEvent>(InformationRequestClockEvent::class.java)
{
    fun findForClock(clockId: UUID): List<InformationRequestClockEvent> =
        entityManager.createQuery(
            "SELECT event FROM InformationRequestClockEvent event WHERE event.clockId = :clockId ORDER BY event.eventNumber",
            InformationRequestClockEvent::class.java,
        )
            .setParameter("clockId", clockId)
            .resultList

    fun findForClocks(clockIds: Collection<UUID>): List<InformationRequestClockEvent>
    {
        if (clockIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT event FROM InformationRequestClockEvent event WHERE event.clockId IN :clockIds ORDER BY event.clockId, event.eventNumber",
            InformationRequestClockEvent::class.java,
        )
            .setParameter("clockIds", clockIds)
            .resultList
    }

    fun nextEventNumber(clockId: UUID): Int = (findForClock(clockId).maxOfOrNull { it.eventNumber } ?: 0) + 1
}
