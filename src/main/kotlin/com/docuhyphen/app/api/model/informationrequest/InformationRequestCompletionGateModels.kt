package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.util.UUID

data class ChangeInformationRequestCompletionGateCommand(
    val requestId: UUID,
    val gatesExchangeClosure: Boolean,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)
