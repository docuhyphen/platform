package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.RevisionETag
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class CommandPreconditionHttpTest
{
    private val resourceId = UUID.randomUUID()
    private val current = RevisionETag.of(resourceId, 14)

    @Test
    fun `a required If-Match header with no stated validator becomes a missing precondition`()
    {
        val precondition = CommandPreconditionHeader.requiredExact(null)
        val refusal = assertThrows<CommandPreconditionException> {
            precondition.requireSatisfiedBy(current)
        }

        assertEquals(CommandPreconditionException.Kind.REQUIRED, refusal.kind)
    }

    @Test
    fun `a stale If-Match validator is refused separately from a missing one`()
    {
        val stale = RevisionETag.of(resourceId, 13)
        val precondition = CommandPreconditionHeader.requiredExact(stale)
        val refusal = assertThrows<CommandPreconditionException> {
            precondition.requireSatisfiedBy(current)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, refusal.kind)
    }

    @Test
    fun `the wildcard is stated but never stands in for the exact version a command requires`()
    {
        val precondition = CommandPreconditionHeader.requiredExact("*")

        assertEquals(CommandPrecondition.Unconditioned, CommandPreconditionHeader.stated("*"))
        val refusal = assertThrows<CommandPreconditionException> { precondition.requireSatisfiedBy(current) }
        assertEquals(CommandPreconditionException.Kind.REQUIRED, refusal.kind)
    }

    @Test
    fun `precondition refusals map to the shared HTTP status and ETag contract`()
    {
        val missing = CommandPreconditionResponse.refused(CommandPreconditionException.required(current))
        val stale = CommandPreconditionResponse.refused(CommandPreconditionException.stale(current))

        assertEquals(428, missing.status)
        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, stale.status)
        assertEquals("COMMAND_PRECONDITION_REQUIRED", (missing.entity as ResponseError).reasonCode)
        assertEquals("COMMAND_PRECONDITION_STALE", (stale.entity as ResponseError).reasonCode)
        assertEquals(current, stale.getHeaderString("ETag"))
    }
}
