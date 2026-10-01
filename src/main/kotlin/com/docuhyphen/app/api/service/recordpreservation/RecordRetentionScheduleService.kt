package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordRetentionScheduleView
import com.docuhyphen.app.api.repository.recordpreservation.RecordRetentionScheduleRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class RecordRetentionScheduleService @Inject constructor(
    private val scheduleRepository: RecordRetentionScheduleRepository,
    private val audit: RecordPreservationAudit,
    private val clock: Clock,
)
{
    @Transactional
    fun publish(command: PublishRecordRetentionScheduleCommand): RecordRetentionScheduleView
    {
        val resourceType = resourceTypeOf(command.resourceType)
        if (command.owner.kind == RecordOwnerKind.PLATFORM)
        {
            throw RecordPreservationRequestException("A retention schedule belongs to an organization or a personal owner")
        }
        if (command.minimumRetentionDays < 0)
        {
            throw RecordPreservationRequestException("A minimum retention is zero or more days")
        }
        if (command.disposalAfterDays != null && command.disposalAfterDays < command.minimumRetentionDays)
        {
            throw RecordPreservationRequestException("A disposal age never precedes the minimum retention")
        }
        val versions = scheduleRepository.findVersions(command.owner, resourceType)
        val schedule = scheduleRepository.save(
            RecordRetentionSchedule().apply {
                ownerKind = command.owner.kind
                ownerId = requireNotNull(command.owner.id)
                this.resourceType = resourceType
                versionNumber = (versions.maxOfOrNull { it.versionNumber } ?: 0) + 1
                minimumRetentionDays = command.minimumRetentionDays
                disposalAfterDays = command.disposalAfterDays
                recordedByPrincipalKind = command.principal.kind
                recordedByPrincipalId = command.principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        audit.schedule(schedule, command.principal)
        return RecordRetentionScheduleView(schedule, listOf(schedule) + versions)
    }

    fun schedule(owner: RecordOwnerRef, resourceType: String): RecordRetentionScheduleView
    {
        if (owner.kind == RecordOwnerKind.PLATFORM) return RecordRetentionScheduleView(null, emptyList())
        val versions = scheduleRepository.findVersions(owner, resourceTypeOf(resourceType))
        return RecordRetentionScheduleView(versions.firstOrNull(), versions)
    }

    fun current(owner: RecordOwnerRef, resourceType: String): RecordRetentionSchedule? =
        schedule(owner, resourceType).current

    fun currentSchedules(resourceType: String): List<RecordRetentionSchedule> =
        scheduleRepository.findAllCurrent(resourceTypeOf(resourceType))

    private fun resourceTypeOf(raw: String): String
    {
        val normalized = raw.trim().uppercase().replace('-', '_')
        if (normalized !in SCHEDULED_RESOURCE_TYPES)
        {
            throw RecordPreservationRequestException("Retention schedules apply to: ${SCHEDULED_RESOURCE_TYPES.joinToString()}")
        }
        return normalized
    }

    companion object
    {
        const val INFORMATION_REQUEST = "INFORMATION_REQUEST"
        val SCHEDULED_RESOURCE_TYPES = setOf(INFORMATION_REQUEST)
    }
}
