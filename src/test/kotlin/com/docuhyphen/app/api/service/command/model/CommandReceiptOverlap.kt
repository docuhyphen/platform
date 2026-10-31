package com.docuhyphen.app.api.service.command.model

import com.docuhyphen.app.api.model.command.CommandReceiptDecision
import com.docuhyphen.app.api.model.command.CommandResultReference

internal data class CommandReceiptOverlap(
    val first: Result<CommandReceiptDecision<String>>,
    val second: Result<CommandReceiptDecision<String>>,
    val firstResult: CommandResultReference,
    val secondFinishedFirst: Boolean,
    val mutations: Int,
)
