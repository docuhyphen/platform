package com.docuhyphen.app.api.model.command

import java.util.*

data class CommandReceiptReference(
    val receiptId: UUID,
    val idempotencyKey: String,
)
