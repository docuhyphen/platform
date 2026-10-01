package com.docuhyphen.app.api.model.informationrequest.oversight

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.notification.DomainEventDeliveryStanding
import java.time.Instant
import java.util.*

enum class InformationRequestSlaStatus
{
    NO_CLOCK,
    ON_TRACK,
    DUE_SOON,
    OVERDUE,
    PAUSED,
    MET,
}

enum class InformationRequestOperationsException
{
    NOTICE_UNDELIVERABLE,
    NOTICE_FAILED,
    CLOCK_ESCALATED,
    AUTOMATION_SKIPPED,
    EVENT_DELIVERY_FAILING,
}

data class InformationRequestSlaClock(
    val clock: InformationRequestClock,
    val events: List<InformationRequestClockEvent>,
    val dueSoonFrom: Instant?,
)

data class InformationRequestSlaStanding(
    val status: InformationRequestSlaStatus,
    val nearestDueAt: Instant?,
    val reminderCount: Int,
    val openEscalationCount: Int,
)

data class InformationRequestOperationsFilter(
    val states: Set<InformationRequestState> = emptySet(),
    val exchangeId: UUID? = null,
    val search: String? = null,
    val assigneeId: UUID? = null,
    val slaStatuses: Set<InformationRequestSlaStatus> = emptySet(),
    val exceptions: Set<InformationRequestOperationsException> = emptySet(),
    val exceptionsOnly: Boolean = false,
    val limit: Int = DEFAULT_OPERATIONS_LIMIT,
    val offset: Int = 0,
)

data class InformationRequestOperationsAssignee(
    val roleKey: InformationRequestShareRoleKey,
    val principalKind: PrincipalKind,
    val principalId: UUID,
    val label: String?,
)

data class InformationRequestOperationsRow(
    val request: InformationRequest,
    val title: String,
    val assignees: List<InformationRequestOperationsAssignee>,
    val ageSeconds: Long,
    val clockCount: Int,
    val standing: InformationRequestSlaStanding,
    val noticeCounts: Map<InformationRequestNoticeDeliveryState, Int>,
    val exceptionCounts: Map<InformationRequestOperationsException, Int>,
)

data class InformationRequestOperationsInputs(
    val clocks: Map<UUID, List<InformationRequestClock>>,
    val events: Map<UUID, List<InformationRequestClockEvent>>,
    val versions: Map<UUID, InformationRequestClockPolicyVersionView?>,
    val notices: Map<UUID, List<InformationRequestNoticeDeliveryState>>,
    val deliveries: Map<String, DomainEventDeliveryStanding>,
)

data class InformationRequestOperationsPage(
    val rows: List<InformationRequestOperationsRow>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

const val DEFAULT_OPERATIONS_LIMIT = 50
const val MAXIMUM_OPERATIONS_LIMIT = 200
