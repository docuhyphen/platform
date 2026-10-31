package com.docuhyphen.app.api.service.informationrequest.clock.model

import java.time.Instant
import java.util.*

internal data class ScheduledStandingScenario(
    val requestId: UUID,
    val organizationId: UUID,
    val clockId: UUID,
    val start: Instant,
    val contributorPartyId: UUID,
)
