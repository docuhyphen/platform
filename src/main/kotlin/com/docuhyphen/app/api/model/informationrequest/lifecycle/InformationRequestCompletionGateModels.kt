package com.docuhyphen.app.api.model.informationrequest.lifecycle

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.*

data class ChangeInformationRequestCompletionGateCommand(
    val requestId: UUID,
    val gatesExchangeClosure: Boolean,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)
