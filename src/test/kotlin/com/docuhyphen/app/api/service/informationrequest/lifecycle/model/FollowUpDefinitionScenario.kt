package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import java.util.*

internal data class FollowUpDefinitionScenario(
    val requestId: UUID,
    val manager: PrincipalRef,
    val secondManager: PrincipalRef,
    val contributor: PrincipalRef,
    val services: InformationRequestRuntimeServices,
)

internal data class FollowUpDefinitionWrites(
    val recurrences: Int,
    val refreshRules: Int,
    val scheduleTransitions: Int,
    val receipts: Int,
    val aggregateRevision: Int,
)
