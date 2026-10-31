package com.docuhyphen.app.api.model.command

import com.docuhyphen.app.api.service.command.CommandPreconditionException

sealed interface CommandPrecondition {
    fun requireSatisfiedBy(currentETag: String?)

    data object Unconditioned : CommandPrecondition {
        override fun requireSatisfiedBy(currentETag: String?) = Unit
    }

    data object Absent : CommandPrecondition {
        override fun requireSatisfiedBy(currentETag: String?): Unit =
            throw CommandPreconditionException.required(currentETag)
    }

    data class ExpectedRevision(val etags: Set<String>) : CommandPrecondition {
        constructor(etag: String) : this(setOf(etag))

        override fun requireSatisfiedBy(currentETag: String?) {
            if (currentETag == null || currentETag !in etags)
                throw CommandPreconditionException.stale(currentETag)
        }
    }
}
