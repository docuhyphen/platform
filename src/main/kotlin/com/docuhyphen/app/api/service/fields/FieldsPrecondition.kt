package com.docuhyphen.app.api.service.fields

/**
 * The state of the stored data a mutation believes it is changing.
 *
 * A caller reads a state, decides what to change about it, and sends the change back. Between those
 * two moments another caller may have changed the same data, and the second change would silently
 * overwrite the first. Naming the state closes that window: a mutation proceeds only while the data
 * still stands where the caller left it.
 *
 * The two ways a mutation can fail this are different problems for a client and are kept apart. A
 * client that never names a state has to start sending one; a client whose state has moved on has
 * to read again before saving. [FieldsPreconditionException.reasonCode] is what tells them apart.
 */
sealed interface FieldsPrecondition
{
    /**
     * Refuses the mutation unless the state this precondition names is the one [currentETag]
     * identifies. A null [currentETag] means nothing is stored to be stale about.
     */
    fun requireSatisfiedBy(currentETag: String?)

    /** The caller names no state and is not required to, which is the unconditioned legacy save. */
    data object Unconditioned : FieldsPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?) = Unit
    }

    /** The caller had to name the state it read and named none. */
    data object Absent : FieldsPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?): Unit =
            throw FieldsPreconditionException.required(currentETag)
    }

    /**
     * The caller names the states it would accept, as the strong validators those states were served
     * with. A caller that read one state names one; a caller holding several representations of the
     * same data may name each of them, and the mutation proceeds while the stored data still stands
     * in one of them.
     *
     * Comparison is verbatim equality, which is the strong comparison a conditional write requires:
     * a validator marked weak names a state that may differ in ways the tag does not capture, so it
     * can never satisfy a write.
     */
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

/**
 * Thrown when a mutation cannot proceed because its caller did not name which state it was changing,
 * or named a state the stored data has already moved past. [currentETag] is the state that is
 * current, so a client can recover without guessing.
 */
class FieldsPreconditionException private constructor(
    val kind: Kind,
    override val message: String,
    val currentETag: String?,
) : RuntimeException(message)
{
    /** Stable machine codes, so a client can tell the two refusals apart without reading prose. */
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
