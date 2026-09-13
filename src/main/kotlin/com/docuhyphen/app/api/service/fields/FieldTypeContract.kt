package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A controlled type contract: it validates a raw JSON input against a Field Contract's constraints
 * and options and returns a [CanonicalFieldValue]. Validation is deterministic and authoritative
 * on the backend. Unknown type codes never reach a contract; the registry fails closed.
 * See FIELDS-FEATURE.md "Field Type System".
 */
interface FieldTypeContract
{
    val type: FieldValueType
    val typeContractVersion: Int
    val supportedOperators: Set<FieldOperator>

    /**
     * Validates [input] and returns its canonical form. A null / JSON-null / blank input yields an
     * empty value; requiredness is enforced by the binding layer, not here.
     * @throws FieldValidationException when the input is malformed or violates a constraint.
     */
    fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
}

private fun JsonElement?.isNullish(): Boolean =
    this == null || this is JsonNull

private fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.content

// ── Text types ───────────────────────────────────────────────────────────────────────────────

private abstract class TextTypeContract : FieldTypeContract
{
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.CONTAINS,
        FieldOperator.STARTS_WITH, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val raw = input!!.asStringOrNull()
            ?: throw FieldValidationException("Expected a text value")
        val value = raw.trim()
        if (value.isEmpty()) return CanonicalFieldValue.empty(type)

        constraints.minLength?.let {
            if (value.length < it) throw FieldValidationException("Must be at least $it characters")
        }
        constraints.maxLength?.let {
            if (value.length > it) throw FieldValidationException("Must be at most $it characters")
        }
        constraints.pattern?.let { pattern ->
            val matches = runCatching { Regex(pattern).matches(value) }.getOrElse {
                throw FieldValidationException("Field pattern is invalid")
            }
            if (!matches) throw FieldValidationException("Value does not match the required pattern")
        }
        return CanonicalFieldValue(type = type, isEmpty = false, textValue = value)
    }
}

private object ShortTextContract : TextTypeContract()
{
    override val type = FieldValueType.SHORT_TEXT
    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        // Short text has an implicit 255-char cap unless a smaller maxLength is set.
        val effective = constraints.copy(maxLength = minOf(constraints.maxLength ?: 255, 255))
        return super.canonicalize(input, effective, options)
    }
}

private object LongTextContract : TextTypeContract()
{
    override val type = FieldValueType.LONG_TEXT
}

// ── Boolean ──────────────────────────────────────────────────────────────────────────────────

private object BooleanContract : FieldTypeContract
{
    override val type = FieldValueType.BOOLEAN
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS,
        FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val prim = input as? JsonPrimitive
            ?: throw FieldValidationException("Expected true or false")
        val bool = prim.booleanOrNull ?: when (prim.content.trim().lowercase())
        {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            "" -> return CanonicalFieldValue.empty(type)
            else -> throw FieldValidationException("Expected true or false")
        }
        return CanonicalFieldValue(type = type, isEmpty = false, boolValue = bool)
    }
}

// ── Numeric types ──────────────────────────────────────────────────────────────────────────────

private abstract class NumberTypeContract : FieldTypeContract
{
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    protected abstract val integerOnly: Boolean

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val raw = (input as? JsonPrimitive)?.content?.trim()
            ?: throw FieldValidationException("Expected a number")
        if (raw.isEmpty()) return CanonicalFieldValue.empty(type)

        val number = runCatching { BigDecimal(raw) }.getOrElse {
            throw FieldValidationException("Not a valid number")
        }
        if (integerOnly && CanonicalNumber.significantScale(number) > 0)
        {
            throw FieldValidationException("Must be a whole number")
        }
        val scale = constraints.scale
        val normalized = if (!integerOnly && scale != null)
        {
            if (CanonicalNumber.significantScale(number) > scale)
                throw FieldValidationException("At most $scale decimal place(s) allowed")
            number.setScale(scale, RoundingMode.UNNECESSARY)
        }
        else number
        CanonicalNumber.assertStorable(normalized)

        constraints.minValue?.let {
            if (normalized < BigDecimal(it)) throw FieldValidationException("Must be at least $it")
        }
        constraints.maxValue?.let {
            if (normalized > BigDecimal(it)) throw FieldValidationException("Must be at most $it")
        }
        return CanonicalFieldValue(type = type, isEmpty = false, numberValue = normalized)
    }
}

private object IntegerContract : NumberTypeContract()
{
    override val type = FieldValueType.INTEGER
    override val integerOnly = true
}

private object DecimalContract : NumberTypeContract()
{
    override val type = FieldValueType.DECIMAL
    override val integerOnly = false
}

// ── Date / DateTime ────────────────────────────────────────────────────────────────────────────

private object DateContract : FieldTypeContract
{
    override val type = FieldValueType.DATE
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val raw = (input as? JsonPrimitive)?.content?.trim()
            ?: throw FieldValidationException("Expected an ISO date (yyyy-MM-dd)")
        if (raw.isEmpty()) return CanonicalFieldValue.empty(type)

        val date = runCatching { LocalDate.parse(raw) }.getOrElse {
            throw FieldValidationException("Expected an ISO date (yyyy-MM-dd)")
        }
        constraints.minDate?.let {
            val min = runCatching { LocalDate.parse(it) }.getOrNull()
            if (min != null && date.isBefore(min)) throw FieldValidationException("Must be on or after $it")
        }
        constraints.maxDate?.let {
            val max = runCatching { LocalDate.parse(it) }.getOrNull()
            if (max != null && date.isAfter(max)) throw FieldValidationException("Must be on or before $it")
        }
        return CanonicalFieldValue(type = type, isEmpty = false, dateValue = date)
    }
}

private object DateTimeContract : FieldTypeContract
{
    override val type = FieldValueType.DATE_TIME
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val raw = (input as? JsonPrimitive)?.content?.trim()
            ?: throw FieldValidationException("Expected an ISO date-time")
        if (raw.isEmpty()) return CanonicalFieldValue.empty(type)

        val reading = CanonicalDateTime.parse(raw)
            ?: throw FieldValidationException("Expected an ISO date-time")
        return CanonicalFieldValue(
            type = type,
            isEmpty = false,
            datetimeValue = reading.instant,
            datetimeOffsetMinutes = reading.offsetMinutes,
        )
    }
}

// ── Select types ─────────────────────────────────────────────────────────────────────────────

private object SingleSelectContract : FieldTypeContract
{
    override val type = FieldValueType.SINGLE_SELECT
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.IN, FieldOperator.NOT_IN,
        FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val code = (input as? JsonPrimitive)?.content?.trim()
            ?: throw FieldValidationException("Expected a single option code")
        if (code.isEmpty()) return CanonicalFieldValue.empty(type)

        val option = options.firstOrNull { it.code == code }
            ?: throw FieldValidationException("Unknown option: $code")
        if (!option.active) throw FieldValidationException("Option is no longer selectable: $code")
        return CanonicalFieldValue(type = type, isEmpty = false, selectionCodes = listOf(code))
    }
}

private object MultiSelectContract : FieldTypeContract
{
    override val type = FieldValueType.MULTI_SELECT
    override val typeContractVersion = 1
    override val supportedOperators = setOf(
        FieldOperator.CONTAINS, FieldOperator.IN, FieldOperator.NOT_IN,
        FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    )

    override fun canonicalize(
        input: JsonElement?,
        constraints: FieldConstraints,
        options: List<FieldOption>,
    ): CanonicalFieldValue
    {
        if (input.isNullish()) return CanonicalFieldValue.empty(type)
        val array = input as? JsonArray
            ?: throw FieldValidationException("Expected an array of option codes")
        val codes = array.map {
            (it as? JsonPrimitive)?.content?.trim()
                ?: throw FieldValidationException("Option codes must be strings")
        }.filter { it.isNotEmpty() }.distinct()

        if (codes.isEmpty()) return CanonicalFieldValue.empty(type)

        codes.forEach { code ->
            val option = options.firstOrNull { it.code == code }
                ?: throw FieldValidationException("Unknown option: $code")
            if (!option.active) throw FieldValidationException("Option is no longer selectable: $code")
        }
        constraints.minSelections?.let {
            if (codes.size < it) throw FieldValidationException("Select at least $it option(s)")
        }
        constraints.maxSelections?.let {
            if (codes.size > it) throw FieldValidationException("Select at most $it option(s)")
        }
        // Preserve the declared option order for a stable canonical representation.
        val ordered = options.filter { it.code in codes }.map { it.code }
        return CanonicalFieldValue(type = type, isEmpty = false, selectionCodes = ordered)
    }
}

/** All registered contracts, one per [FieldValueType]. */
internal val ALL_FIELD_TYPE_CONTRACTS: List<FieldTypeContract> = listOf(
    ShortTextContract,
    LongTextContract,
    BooleanContract,
    IntegerContract,
    DecimalContract,
    DateContract,
    DateTimeContract,
    SingleSelectContract,
    MultiSelectContract,
)
