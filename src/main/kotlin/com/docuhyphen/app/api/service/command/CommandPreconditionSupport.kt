package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.command.CommandPrecondition
import java.util.*

fun CommandPrecondition.canonicalForm(): String = when (this) {
    CommandPrecondition.Unconditioned -> "*"
    CommandPrecondition.Absent -> ""
    is CommandPrecondition.ExpectedRevision -> etags.sorted().joinToString(",")
}

class CommandPreconditionException private constructor(
    val kind: Kind,
    override val message: String,
    val currentETag: String?,
) : RuntimeException(message) {
    enum class Kind(val reasonCode: String) {
        REQUIRED("COMMAND_PRECONDITION_REQUIRED"),
        STALE("COMMAND_PRECONDITION_STALE"),
    }

    val reasonCode: String get() = kind.reasonCode

    companion object {
        fun required(currentETag: String?) = CommandPreconditionException(
            Kind.REQUIRED,
            "This change must state which version of the data it is changing",
            currentETag,
        )

        fun stale(currentETag: String?) = CommandPreconditionException(
            Kind.STALE,
            "The data changed after it was read; reload it and try again",
            currentETag,
        )
    }
}

object RevisionETag {
    fun of(resourceId: UUID, revision: Long): String = "\"$resourceId:$revision\""
}
