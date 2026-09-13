package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import java.util.UUID

/**
 * Thrown when reserving capacity would push a grant's active usage for one
 * [RequestExecutionUsageKind] past the cap that was frozen into it at issuance.
 */
class RequestExecutionUsageExhaustedException(
    val grantId: UUID,
    val usageKind: RequestExecutionUsageKind,
    val cap: Long,
    val activeUsage: Long,
    val requested: Long,
) : RuntimeException(
    "Execution grant $grantId has no remaining $usageKind capacity: " +
        "cap=$cap, active=$activeUsage, requested=$requested",
)
