package com.docuhyphen.app.api.service.command

import java.util.UUID

sealed interface CommandPrecondition
{
    fun requireSatisfiedBy(currentETag: String?)

    data object Unconditioned : CommandPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?) = Unit
    }

    data object Absent : CommandPrecondition
    {
        override fun requireSatisfiedBy(currentETag: String?): Unit =
            throw CommandPreconditionException.required(currentETag)
    }

    data class ExpectedRevision(val etags: Set<String>) : CommandPrecondition
    {
        constructor(etag: String) : this(setOf(etag))

        override fun requireSatisfiedBy(currentETag: String?)
        {
            if (currentETag == null || currentETag !in etags)
                throw CommandPreconditionException.stale(currentETag)
        }
    }
}

class CommandPreconditionException private constructor(
    val kind: Kind,
    override val message: String,
    val currentETag: String?,
) : RuntimeException(message)
{
    enum class Kind(val reasonCode: String)
    {
        REQUIRED("COMMAND_PRECONDITION_REQUIRED"),
        STALE("COMMAND_PRECONDITION_STALE"),
    }

    val reasonCode: String get() = kind.reasonCode

    companion object
    {
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

data class ResourceRevision(val resourceId: UUID, val revision: Long)

object RevisionETag
{
    private val STRONG_REVISION = Regex("^\"([0-9a-fA-F-]{36}):(\\d+)\"$")

    fun of(resourceId: UUID, revision: Long): String = "\"$resourceId:$revision\""

    fun parse(etag: String?): ResourceRevision?
    {
        val match = STRONG_REVISION.matchEntire(etag?.trim().orEmpty()) ?: return null
        val resourceId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull() ?: return null
        val revision = match.groupValues[2].toLongOrNull() ?: return null
        return ResourceRevision(resourceId, revision)
    }
}
