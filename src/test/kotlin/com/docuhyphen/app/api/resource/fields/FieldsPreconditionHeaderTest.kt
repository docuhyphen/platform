package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * `If-Match` is a header before it is a precondition, and the header has more shapes than the
 * engine's one idea of a stated version. A caller may name one validator, several, a validator it
 * marks as weak, or the wildcard that stands for whichever version is current, and a surface that
 * requires a stated version has to tell a caller that named none from a caller that named a version
 * the data has moved past. These are the shapes the two Fields write surfaces are reached with.
 */
class FieldsPreconditionHeaderTest
{
    private val current = "\"${UUID.randomUUID()}:7\""

    @Test
    fun `a surface that requires a stated version and is sent none refuses for the missing one`()
    {
        assertEquals(FieldsPrecondition.Absent, FieldsPreconditionHeader.required(null))
    }

    @Test
    fun `a header that carries nothing states no version`()
    {
        assertEquals(FieldsPrecondition.Absent, FieldsPreconditionHeader.required("   "))
        assertFalse(FieldsPreconditionHeader.isStated("   "))
        assertFalse(FieldsPreconditionHeader.isStated(null))
    }

    @Test
    fun `a surface that only enforces a supplied version leaves a missing header unconditioned`()
    {
        assertEquals(FieldsPrecondition.Unconditioned, FieldsPreconditionHeader.optional(null))
        assertEquals(FieldsPrecondition.Unconditioned, FieldsPreconditionHeader.optional(""))
    }

    @Test
    fun `one validator is the one version the caller accepts`()
    {
        val precondition = FieldsPreconditionHeader.required(current)

        assertEquals(FieldsPrecondition.ExpectedRevision(setOf(current)), precondition)
        assertTrue(FieldsPreconditionHeader.isStated(current))
        assertDoesNotThrow { precondition.requireSatisfiedBy(current) }
    }

    @Test
    fun `a list names every version the caller would accept`()
    {
        val other = "\"${UUID.randomUUID()}:2\""

        val precondition = FieldsPreconditionHeader.required("$other , $current")

        assertEquals(FieldsPrecondition.ExpectedRevision(setOf(other, current)), precondition)
        assertDoesNotThrow("Any one of the named versions satisfies the condition") {
            precondition.requireSatisfiedBy(current)
        }
        assertThrows<FieldsPreconditionException> {
            precondition.requireSatisfiedBy("\"${UUID.randomUUID()}:1\"")
        }
    }

    @Test
    fun `the wildcard accepts whichever version is current`()
    {
        val precondition = FieldsPreconditionHeader.required("*")

        assertTrue(
            FieldsPreconditionHeader.isStated("*"),
            "The wildcard is a stated condition, even though it excludes no version",
        )
        assertDoesNotThrow { precondition.requireSatisfiedBy(current) }
    }

    @Test
    fun `a validator the caller marks as weak satisfies no write`()
    {
        val precondition = FieldsPreconditionHeader.required("W/$current")

        assertTrue(FieldsPreconditionHeader.isStated("W/$current"))
        val refusal = assertThrows<FieldsPreconditionException> {
            precondition.requireSatisfiedBy(current)
        }
        assertEquals(FieldsPreconditionException.Kind.STALE, refusal.kind)
    }

    @Test
    fun `both surfaces read a supplied version the same way`()
    {
        assertEquals(
            FieldsPreconditionHeader.required(current), FieldsPreconditionHeader.optional(current),
            "Which surface received the header cannot change which version it names",
        )
    }
}
