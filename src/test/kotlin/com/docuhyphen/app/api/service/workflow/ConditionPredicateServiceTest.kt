package com.docuhyphen.app.api.service.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class ConditionPredicateServiceTest
{
    private val service = ConditionPredicateService()
    private val fields = listOf(
        WorkflowSubjectField("name", "STRING"),
        WorkflowSubjectField("count", "integer"),
        WorkflowSubjectField("score", "number"),
        WorkflowSubjectField("active", "boolean"),
        WorkflowSubjectField("ownerId", "UUID"),
        WorkflowSubjectField("status", "STRING", "OPEN | CLOSED"),
    )

    @ParameterizedTest
    @CsvSource(
        "'\$subject.count > 4', count, 5, true",
        "'\$subject.count > 5', count, 5, false",
        "'\$subject.count <= 5', count, 5, true",
        "'\$subject.count < -1', count, -2, true",
        "'\$subject.score >= -1.25', score, -1.25, true",
        "'\$subject.score == 0.0', score, 0, true",
        "'\$subject.name contains ''middle''', name, a-middle-z, true",
        "'\$subject.name startsWith ''Case''', name, CaseSensitive, true",
        "'\$subject.name startsWith ''case''', name, CaseSensitive, false",
        "'\$subject.active == true', active, true, true",
        "'\$subject.ownerId != ''ABC-def''', ownerId, abc-def, true",
    )
    fun `evaluates supported typed predicates`(expression: String, key: String, actual: String, expected: Boolean)
    {
        val result = service.evaluate(expression, fields, mapOf(key to actual))
        assertEquals(PredicateResult.Valid(expected), result)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "", " ", "name == 'x'", "subject.name == 'x'", "\$subject. == 'x'",
        "\$subject.name = 'x'", "\$subject.name === 'x'", "\$subject.name == 'x' trailing",
        "\$subject.name contains ''", "\$subject.count > NaN", "\$subject.count > 1.5",
        "\$subject.score > 1e3", "\$subject.score > 1,5", "\$subject.active == TRUE",
        "\$subject.ownerId contains 'abc'", "\$subject.name == ‘x’",
    ])
    fun `invalid predicates fail closed`(expression: String)
    {
        assertInstanceOf(PredicateResult.Invalid::class.java, service.evaluate(expression, fields, mapOf("name" to "x")))
    }

    @Test
    fun `missing and unknown fields fail closed including inequality`()
    {
        assertEquals(
            PredicateResult.Invalid(PredicateErrorCode.MISSING_SUBJECT_VALUE),
            service.evaluate("\$subject.name != 'x'", fields, emptyMap()),
        )
        assertEquals(
            PredicateResult.Invalid(PredicateErrorCode.UNKNOWN_FIELD),
            service.evaluate("\$subject.missing != 'x'", fields, mapOf("missing" to "y")),
        )
        assertEquals(
            PredicateResult.Invalid(PredicateErrorCode.INVALID_SUBJECT_VALUE),
            service.evaluate("\$subject.name == ''", fields, mapOf("name" to " ")),
        )
    }

    @Test
    fun `quoted values support explicit quote and backslash escapes`()
    {
        assertTrue((service.evaluate("\$subject.name == 'it\\'s \\\\ safe'", fields, mapOf("name" to "it's \\ safe")) as PredicateResult.Valid).matches)
        assertFalse((service.evaluate("\$subject.name == 'A'", fields, mapOf("name" to "a")) as PredicateResult.Valid).matches)
    }

    @ParameterizedTest
    @CsvSource(
        "'>', 4, 5, false",
        "'>', 5, 5, false",
        "'>', 6, 5, true",
        "'<', 4, 5, true",
        "'<', 5, 5, false",
        "'<', 6, 5, false",
        "'>=', 4, 5, false",
        "'>=', 5, 5, true",
        "'>=', 6, 5, true",
        "'<=', 4, 5, true",
        "'<=', 5, 5, true",
        "'<=', 6, 5, false",
    )
    fun `numeric comparison operators preserve boundary behavior`(
        operator: String,
        actual: String,
        expected: String,
        matches: Boolean,
    )
    {
        assertEquals(
            PredicateResult.Valid(matches),
            service.evaluate("\$subject.count $operator $expected", fields, mapOf("count" to actual)),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "\$subject.name == 'unterminated",
        "\$subject.name == 'bad\\nnewline'",
        "\$subject.name == ‘unsupported’",
        "\$subject.name !== 'x'",
        "\$subject.name => 'x'",
        "\$subject.name CONTAINS 'x'",
        "\$subject.name == 'x' == 'x'",
        "junk \$subject.name == 'x'",
        "\$subject.name == 'x' junk",
        "\$subject.name.extra == 'x'",
    ])
    fun `malformed grammar never produces a valid result`(expression: String)
    {
        assertInstanceOf(
            PredicateResult.Invalid::class.java,
            service.evaluate(expression, fields, mapOf("name" to "x")),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "contains, prefix-value, prefix, true",
        "contains, value-middle, middle, true",
        "contains, value-suffix, suffix, true",
        "contains, whole, whole, true",
        "contains, short, longer, false",
        "startsWith, prefix-value, prefix, true",
        "startsWith, prefix, prefix, true",
        "startsWith, value-prefix, prefix, false",
        "startsWith, short, longer, false",
    )
    fun `text operators cover positions and operand lengths`(
        operator: String,
        actual: String,
        operand: String,
        matches: Boolean,
    )
    {
        assertEquals(
            PredicateResult.Valid(matches),
            service.evaluate("\$subject.name $operator '$operand'", fields, mapOf("name" to actual)),
        )
    }

    @Test
    fun `arbitrary malformed expressions never throw or evaluate true`()
    {
        val random = Random(918_244)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789$.'\\ <>=!_"
        repeat(2_000)
        {
            val candidate = buildString {
                repeat(random.nextInt(0, 96)) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            val parsed = service.parse(candidate)
            if (parsed.isFailure)
            {
                assertInstanceOf(
                    PredicateResult.Invalid::class.java,
                    service.evaluate(candidate, fields, mapOf("name" to "safe", "count" to "1")),
                )
            }
        }
    }
}
