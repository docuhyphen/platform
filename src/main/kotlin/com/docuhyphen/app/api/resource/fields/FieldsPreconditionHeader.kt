package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.fields.FieldsPrecondition

/**
 * Turns the `If-Match` header of a request into the [FieldsPrecondition] a Fields command carries.
 *
 * The header is an HTTP spelling of one idea: the version of the data the caller believes it is
 * changing. It has more shapes than that one idea, so normalizing it in one place keeps every Fields
 * surface reading it the same way and keeps the engine free of the header's shape.
 *
 * A caller may name one validator, several it would equally accept, or the wildcard standing for
 * whichever version is current. A validator the caller marks weak is carried through unchanged and
 * therefore matches nothing, which is the strong comparison a conditional write requires. A header
 * carrying no validator at all is treated as though none was sent, so the surface's own rule decides
 * whether that is a refusal or the unconditioned write it has always allowed.
 */
object FieldsPreconditionHeader
{
    /** What a surface that requires the caller to state a version gets from [header]. */
    fun required(header: String?): FieldsPrecondition = stated(header) ?: FieldsPrecondition.Absent

    /**
     * The condition [header] states, or null where it states none. Validators are separated by
     * commas and carried verbatim, because the format of a Fields validator is known to exactly one
     * place and this is not it.
     */
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
