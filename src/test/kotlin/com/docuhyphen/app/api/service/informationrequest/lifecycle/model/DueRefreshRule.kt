package com.docuhyphen.app.api.service.informationrequest.lifecycle.model

import java.util.*

internal data class DueRefreshRule(
    val requestId: UUID,
    val exchangeId: UUID,
    val organizationId: UUID,
    val ruleId: UUID
)
