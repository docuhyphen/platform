package com.docuhyphen.app.api.service.fields

class FieldsPreconditionException private constructor(
    val kind: Kind,
    override val message: String,
    val currentETag: String?,
) : RuntimeException(message)
{
    enum class Kind(val reasonCode: String)
    {
        REQUIRED("FIELDS_PRECONDITION_REQUIRED"),
        STALE("FIELDS_PRECONDITION_STALE"),
    }

    val reasonCode: String get() = kind.reasonCode

    companion object
    {
        fun required(currentETag: String?) = FieldsPreconditionException(
            Kind.REQUIRED,
            "This change must state which version of the data it is changing",
            currentETag,
        )

        fun stale(currentETag: String?) = FieldsPreconditionException(
            Kind.STALE,
            "The data changed after it was read; reload it and try again",
            currentETag,
        )
    }
}
