package com.docuhyphen.app.api.service.workflow

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class WorkflowSubjectField(
    val name: String,
    val type: String,
    val description: String? = null,
    val lookupType: String? = null,
)

enum class PredicateErrorCode
{
    BLANK_EXPRESSION,
    INVALID_SYNTAX,
    INVALID_ESCAPE,
    UNKNOWN_FIELD,
    INCOMPATIBLE_OPERATOR,
    INVALID_OPERAND,
    MISSING_SUBJECT_VALUE,
    INVALID_SUBJECT_VALUE,
}

sealed interface PredicateResult
{
    data class Valid(val matches: Boolean) : PredicateResult
    data class Invalid(val code: PredicateErrorCode) : PredicateResult
}

data class ParsedPredicate(
    val fieldName: String,
    val operator: String,
    val operand: String,
    val quoted: Boolean,
)

@ApplicationScoped
class ConditionPredicateService
{
    fun parse(expression: String?): Result<ParsedPredicate>
    {
        if (expression.isNullOrBlank()) return Result.failure(PredicateParseException(PredicateErrorCode.BLANK_EXPRESSION))
        val match = EXPRESSION.matchEntire(expression.trim())
            ?: return Result.failure(PredicateParseException(PredicateErrorCode.INVALID_SYNTAX))
        val rawOperand = match.groupValues[3]
        val quoted = rawOperand.startsWith("'")
        val operand = if (quoted) decodeQuoted(rawOperand).getOrElse { return Result.failure(it) } else rawOperand
        return Result.success(ParsedPredicate(match.groupValues[1], match.groupValues[2], operand, quoted))
    }

    fun validate(expression: String?, fields: List<WorkflowSubjectField>): PredicateResult =
        evaluateInternal(expression, fields, emptyMap(), false)

    fun evaluate(
        expression: String?,
        fields: List<WorkflowSubjectField>,
        subjectData: Map<String, String>,
    ): PredicateResult = evaluateInternal(expression, fields, subjectData, true)

    private fun evaluateInternal(
        expression: String?,
        fields: List<WorkflowSubjectField>,
        subjectData: Map<String, String>,
        compare: Boolean,
    ): PredicateResult
    {
        val parsed = parse(expression).getOrElse {
            return PredicateResult.Invalid((it as? PredicateParseException)?.code ?: PredicateErrorCode.INVALID_SYNTAX)
        }
        val field = fields.singleOrNull { it.name == parsed.fieldName }
            ?: return PredicateResult.Invalid(PredicateErrorCode.UNKNOWN_FIELD)
        val kind = fieldKind(field)
        if (parsed.operator !in operatorsFor(kind))
        {
            return PredicateResult.Invalid(PredicateErrorCode.INCOMPATIBLE_OPERATOR)
        }
        val expected = normalizeOperand(parsed, kind)
            ?: return PredicateResult.Invalid(PredicateErrorCode.INVALID_OPERAND)
        if (!compare) return PredicateResult.Valid(false)
        val actual = subjectData[parsed.fieldName]
            ?: return PredicateResult.Invalid(PredicateErrorCode.MISSING_SUBJECT_VALUE)
        if (actual.isBlank() || (kind == FieldKind.BOOLEAN && actual !in BOOLEAN_VALUES) ||
            (kind.isNumeric && numeric(actual, kind) == null))
        {
            return PredicateResult.Invalid(PredicateErrorCode.INVALID_SUBJECT_VALUE)
        }
        return PredicateResult.Valid(compare(actual, expected, parsed.operator, kind))
    }

    private fun normalizeOperand(parsed: ParsedPredicate, kind: FieldKind): String?
    {
        return when (kind)
        {
            FieldKind.TEXT, FieldKind.IDENTITY -> if (parsed.quoted) parsed.operand else null
            FieldKind.BOOLEAN -> if (!parsed.quoted && parsed.operand in BOOLEAN_VALUES) parsed.operand else null
            FieldKind.INTEGER -> if (!parsed.quoted && INTEGER.matches(parsed.operand) && parsed.operand.toBigDecimalOrNull() != null) parsed.operand else null
            FieldKind.NUMBER -> if (!parsed.quoted && NUMBER.matches(parsed.operand) && parsed.operand.toBigDecimalOrNull() != null) parsed.operand else null
        }?.takeUnless { it.isEmpty() && parsed.operator in NON_EMPTY_TEXT_OPERATORS }
    }

    private fun compare(actual: String, expected: String, operator: String, kind: FieldKind): Boolean =
        when (operator)
        {
            "contains" -> actual.contains(expected)
            "startsWith" -> actual.startsWith(expected)
            "==" -> if (kind.isNumeric) numeric(actual, kind)?.compareTo(BigDecimal(expected)) == 0 else actual == expected
            "!=" -> if (kind.isNumeric) numeric(actual, kind)?.compareTo(BigDecimal(expected))?.let { it != 0 } ?: false else actual != expected
            ">" -> numeric(actual, kind)?.let { it > BigDecimal(expected) } ?: false
            "<" -> numeric(actual, kind)?.let { it < BigDecimal(expected) } ?: false
            ">=" -> numeric(actual, kind)?.let { it >= BigDecimal(expected) } ?: false
            "<=" -> numeric(actual, kind)?.let { it <= BigDecimal(expected) } ?: false
            else -> false
        }

    private fun numeric(value: String, kind: FieldKind): BigDecimal? = when (kind)
    {
        FieldKind.INTEGER -> value.takeIf(INTEGER::matches)?.toBigDecimalOrNull()
        FieldKind.NUMBER -> value.takeIf(NUMBER::matches)?.toBigDecimalOrNull()
        else -> null
    }

    private fun decodeQuoted(raw: String): Result<String>
    {
        if (raw.length < 2 || !raw.endsWith("'"))
        {
            return Result.failure(PredicateParseException(PredicateErrorCode.INVALID_SYNTAX))
        }
        val value = StringBuilder()
        var index = 1
        while (index < raw.lastIndex)
        {
            val character = raw[index]
            if (character != '\\')
            {
                if (character == '\'') return Result.failure(PredicateParseException(PredicateErrorCode.INVALID_SYNTAX))
                value.append(character)
                index++
                continue
            }
            if (index + 1 >= raw.lastIndex || raw[index + 1] !in charArrayOf('\\', '\''))
            {
                return Result.failure(PredicateParseException(PredicateErrorCode.INVALID_ESCAPE))
            }
            value.append(raw[index + 1])
            index += 2
        }
        return Result.success(value.toString())
    }

    private fun fieldKind(field: WorkflowSubjectField): FieldKind
    {
        val enumLike = field.description?.split('|')?.let { it.size > 1 } == true
        return when (field.type.lowercase())
        {
            "number" -> FieldKind.NUMBER
            "integer" -> FieldKind.INTEGER
            "boolean" -> FieldKind.BOOLEAN
            "uuid" -> FieldKind.IDENTITY
            "string" -> if (enumLike) FieldKind.IDENTITY else FieldKind.TEXT
            else -> FieldKind.TEXT
        }
    }

    private fun operatorsFor(kind: FieldKind): Set<String> = when (kind)
    {
        FieldKind.TEXT -> setOf("==", "!=", "contains", "startsWith")
        FieldKind.NUMBER, FieldKind.INTEGER -> setOf("==", "!=", ">", "<", ">=", "<=")
        FieldKind.IDENTITY, FieldKind.BOOLEAN -> setOf("==", "!=")
    }

    private enum class FieldKind(val isNumeric: Boolean = false)
    {
        TEXT,
        IDENTITY,
        BOOLEAN,
        INTEGER(true),
        NUMBER(true),
    }

    private class PredicateParseException(val code: PredicateErrorCode) : IllegalArgumentException(code.name)

    companion object
    {
        private val EXPRESSION = Regex("^\\${'$'}subject\\.([A-Za-z_][A-Za-z0-9_]*)\\s+(startsWith|contains|>=|<=|==|!=|>|<)\\s+(.+)$")
        private val INTEGER = Regex("^[+-]?[0-9]+$")
        private val NUMBER = Regex("^[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)$")
        private val BOOLEAN_VALUES = setOf("true", "false")
        private val NON_EMPTY_TEXT_OPERATORS = setOf("contains", "startsWith")
    }
}
