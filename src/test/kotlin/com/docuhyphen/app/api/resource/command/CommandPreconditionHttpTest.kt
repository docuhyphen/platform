package com.docuhyphen.app.api.resource.command

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.RevisionETag
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CommandPreconditionHttpTest
{
    private val resourceId = UUID.randomUUID()
    private val current = RevisionETag.of(resourceId, 14)

    @Test
    fun `revision etags parse only strong uuid revision validators`()
    {
        val parsed = RevisionETag.parse(current)

        assertEquals(resourceId, parsed?.resourceId)
        assertEquals(14, parsed?.revision)
        assertNull(RevisionETag.parse("W/$current"))
        assertNull(RevisionETag.parse("\"not-a-uuid:14\""))
        assertNull(RevisionETag.parse("\"$resourceId:not-a-number\""))
    }

    @Test
    fun `a required If-Match header with no stated validator becomes a missing precondition`()
    {
        val precondition = CommandPreconditionHeader.required(null)
        val refusal = assertThrows<CommandPreconditionException> {
            precondition.requireSatisfiedBy(current)
        }

        assertEquals(CommandPreconditionException.Kind.REQUIRED, refusal.kind)
    }

    @Test
    fun `a stale If-Match validator is refused separately from a missing one`()
    {
        val stale = RevisionETag.of(resourceId, 13)
        val precondition = CommandPreconditionHeader.required(stale)
        val refusal = assertThrows<CommandPreconditionException> {
            precondition.requireSatisfiedBy(current)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, refusal.kind)
    }

    @Test
    fun `the wildcard states a condition without rejecting the current revision`()
    {
        val precondition = CommandPreconditionHeader.required("*")

        assertTrue(CommandPreconditionHeader.isStated("*"))
        assertDoesNotThrow { precondition.requireSatisfiedBy(current) }
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

    @Test
    fun `optional If-Match leaves an unstated header unconditioned`()
    {
        assertEquals(CommandPrecondition.Unconditioned, CommandPreconditionHeader.optional(""))
    }
}
