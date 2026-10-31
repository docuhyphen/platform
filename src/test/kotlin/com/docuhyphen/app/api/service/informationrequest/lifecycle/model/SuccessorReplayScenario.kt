package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateNextInformationRequestOccurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestSuccessorResult
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class SuccessorReplayScenario(
    val fixture: SubmissionRuntimeSqlFixture,
    val services: InformationRequestRuntimeServices,
    val manager: PrincipalRef,
) {
    val sourceId: UUID get() = fixture.requestId
    val exchangeId: UUID get() = fixture.exchangeId
    val contributor: PrincipalRef get() = PrincipalRef.user(fixture.contributorUserId)
}

internal data class ReplayedOccurrenceSeries(
    val recurrence: InformationRequestRecurrence,
    val firstCommand: CreateNextInformationRequestOccurrenceCommand,
    val first: InformationRequestSuccessorResult,
    val secondCommand: CreateNextInformationRequestOccurrenceCommand,
    val second: InformationRequestSuccessorResult,
)

internal data class SuccessorReplayWrites(
    val requests: Int,
    val lineages: Int,
    val carryForwards: Int,
    val transitions: Int,
    val receipts: Int,
)
