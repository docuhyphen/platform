package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.service.fields.FieldValidationException

object FieldsPreconditionHeader
{
    fun required(header: String?): FieldsPrecondition = stated(header) ?: FieldsPrecondition.Absent

    fun assignment(ifMatch: String?, ifNoneMatch: String?): FieldsPrecondition {
        val noneMatch = ifNoneMatch?.trim().orEmpty()
        if (noneMatch.isEmpty()) return required(ifMatch)
        if (stated(ifMatch) != null)
            throw FieldValidationException("A schema change states If-Match or If-None-Match, not both")
        if (noneMatch != ANY_VERSION)
            throw FieldValidationException("If-None-Match accepts only * for a schema change")
        return FieldsPrecondition.ExpectedAbsent
    }

    private const val ANY_VERSION = "*"

    private fun stated(header: String?): FieldsPrecondition?
    {
        return when (val precondition = CommandPreconditionHeader.stated(header))
        {
            null -> null
            CommandPrecondition.Unconditioned -> FieldsPrecondition.Unconditioned
            CommandPrecondition.Absent -> FieldsPrecondition.Absent
            is CommandPrecondition.ExpectedRevision -> FieldsPrecondition.ExpectedRevision(precondition.etags)
        }
    }
}
