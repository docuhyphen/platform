package com.docuhyphen.app.api.model.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import java.util.*

data class ChangeInformationRequestCompletionGateCommand(
    val requestId: UUID,
    val gatesExchangeClosure: Boolean,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)
