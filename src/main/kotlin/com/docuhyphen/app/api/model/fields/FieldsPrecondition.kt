package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.service.fields.FieldsPreconditionException

sealed interface FieldsPrecondition
{
    fun requireSatisfiedBy(currentETag: String?)

    data object Unconditioned : FieldsPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?) = Unit
    }

    data object ExpectedAbsent : FieldsPrecondition {
        override fun requireSatisfiedBy(currentETag: String?) {
            if (currentETag != null) throw FieldsPreconditionException.stale(currentETag)
        }
    }

    data object Absent : FieldsPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?): Unit =
            throw FieldsPreconditionException.required(currentETag)
    }

    data class ExpectedRevision(val etags: Set<String>) : FieldsPrecondition
    {
        constructor(etag: String) : this(setOf(etag))

        override fun requireSatisfiedBy(currentETag: String?)
        {
            if (currentETag == null || currentETag !in etags)
                throw FieldsPreconditionException.stale(currentETag)
        }
    }
}
