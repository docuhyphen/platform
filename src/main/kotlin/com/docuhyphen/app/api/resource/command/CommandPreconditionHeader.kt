package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.model.command.CommandPrecondition

object CommandPreconditionHeader
{
    private const val ANY_VERSION = "*"

    fun requiredExact(header: String?): CommandPrecondition =
        stated(header)?.takeUnless { it == CommandPrecondition.Unconditioned } ?: CommandPrecondition.Absent

    fun stated(header: String?): CommandPrecondition?
    {
        val supplied = header?.trim().orEmpty()
        if (supplied.isEmpty()) return null
        if (supplied == ANY_VERSION) return CommandPrecondition.Unconditioned

        val validators = supplied.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return if (validators.isEmpty()) null else CommandPrecondition.ExpectedRevision(validators)
    }
}
