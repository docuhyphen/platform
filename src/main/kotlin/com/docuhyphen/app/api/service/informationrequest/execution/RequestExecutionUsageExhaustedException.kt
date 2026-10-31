package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import java.util.*

class RequestExecutionUsageExhaustedException(
    val grantId: UUID,
    val usageKind: RequestExecutionUsageKind,
    val cap: Long,
    val activeUsage: Long,
    val requested: Long,
) : RuntimeException(
    "Execution grant $grantId has no remaining $usageKind capacity: " +
            "cap=$cap, active=$activeUsage, requested=$requested",
) {
    val refusalMessage: String
        get() = "This request's plan allows $cap acting ${if (cap == 1L) "party" else "parties"}, and every Decision " +
                "Maker, Contributor, Preparer, Reviewer and Attestor counts. Remove a party to stay within it."
}
