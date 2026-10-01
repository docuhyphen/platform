package com.docuhyphen.app.api.model.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.entity.RecordRetentionSchedule
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalObjectCandidate
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import java.time.Instant
import java.util.*

sealed interface InformationRequestDisposalAssessment
{
    val owner: RecordOwnerRef
    val schedule: RecordRetentionSchedule?
    val holds: List<RecordPreservationHold>

    data class Eligible(
        override val owner: RecordOwnerRef,
        override val schedule: RecordRetentionSchedule?,
        val scopeKeys: List<RecordPreservationKey>,
        val objects: List<RecordDisposalObjectCandidate>,
    ) : InformationRequestDisposalAssessment
    {
        override val holds: List<RecordPreservationHold> = emptyList()
    }

    data class Refused(
        override val owner: RecordOwnerRef,
        override val schedule: RecordRetentionSchedule?,
        override val holds: List<RecordPreservationHold>,
        val reasonCode: String,
        val detail: String,
        val eligibleFrom: Instant? = null,
    ) : InformationRequestDisposalAssessment
}

sealed interface InformationRequestDisposalOutcome
{
    data class Claimed(val view: RecordDisposalView) : InformationRequestDisposalOutcome

    data class Refused(val reasonCode: String, val detail: String) : InformationRequestDisposalOutcome
}

data class InformationRequestDisposalStanding(
    val requestId: UUID,
    val assessment: InformationRequestDisposalAssessment?,
    val disposal: RecordDisposalView?,
)
{
    val state: RecordDisposalState? get() = disposal?.claim?.state
}

data class InformationRequestDisposalRun(
    val finalized: Int,
    val pending: Int,
    val refused: Int,
)
