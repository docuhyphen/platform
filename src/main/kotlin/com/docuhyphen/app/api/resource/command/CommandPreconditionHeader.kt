package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.service.command.CommandPrecondition

object CommandPreconditionHeader
{
    private const val ANY_VERSION = "*"

    fun required(header: String?): CommandPrecondition =
        stated(header) ?: CommandPrecondition.Absent

    fun optional(header: String?): CommandPrecondition =
        stated(header) ?: CommandPrecondition.Unconditioned

    fun isStated(header: String?): Boolean = stated(header) != null

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
