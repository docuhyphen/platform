package com.docuhyphen.app.api.service.informationrequest.clock.model

import java.util.*

internal data class ClockSuspensionScenario(
    val requestId: UUID,
    val organizationId: UUID,
    val ownerUserId: UUID,
    val clockId: UUID,
)
