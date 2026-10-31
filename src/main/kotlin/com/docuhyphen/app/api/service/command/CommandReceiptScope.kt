package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.command.CommandReceiptReference
import jakarta.transaction.TransactionScoped
import java.io.Serializable

@TransactionScoped
class CommandReceiptScope : Serializable {
    private var active: CommandReceiptReference? = null

    fun current(): CommandReceiptReference? = active

    fun <T> within(reference: CommandReceiptReference, block: () -> T): T {
        val enclosing = active
        active = reference
        try {
            return block()
        } finally {
            active = enclosing
        }
    }
}
