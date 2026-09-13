package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The canonical reading of a numeric answer.
 *
 * The stored column holds [MAX_INTEGER_DIGITS] digits before the point and [MAX_FRACTION_DIGITS]
 * after it. An answer wider than that is refused with a validation message rather than quietly
 * reshaped by the database, so what a responder is told was accepted is what comes back. Trailing
 * zeros are not precision, so an answer is measured after they are removed.
 *
 * The canonical form is the digits themselves rather than a JSON number, because a JSON number is
 * read as a binary floating-point value by every JavaScript client and cannot carry this width.
 */
object CanonicalNumber
{
    const val MAX_FRACTION_DIGITS = 10
    const val MAX_INTEGER_DIGITS = 28

    /** @throws FieldValidationException when [number] is wider than the store can hold. */
    fun assertStorable(number: BigDecimal)
    {
        val significant = number.stripTrailingZeros()
        if (significantScale(significant) > MAX_FRACTION_DIGITS)
        {
            throw FieldValidationException("At most $MAX_FRACTION_DIGITS decimal place(s) can be stored")
        }
        if (integerDigits(significant) > MAX_INTEGER_DIGITS)
        {
            throw FieldValidationException(
                "At most $MAX_INTEGER_DIGITS digit(s) before the decimal point can be stored",
            )
        }
    }

    /** @throws FieldValidationException when a contract asks for a scale the store cannot hold. */
    fun assertStorableScale(scale: Int)
    {
        if (scale < 0 || scale > MAX_FRACTION_DIGITS)
        {
            throw FieldValidationException("A decimal scale must be between 0 and $MAX_FRACTION_DIGITS")
        }
    }

    /** The number of fraction digits [number] actually carries. */
    fun significantScale(number: BigDecimal): Int =
        number.stripTrailingZeros().scale().coerceAtLeast(0)

    /**
     * The exact digits of [number]. A whole number never carries a fraction, and a decimal is
     * written at [scale] when its contract configures one so the stored padding does not leak into
     * the canonical text. A stored value that cannot be written at that scale without rounding is
     * left at its own width rather than silently rounded.
     */
    fun text(type: FieldValueType, number: BigDecimal, scale: Int?): String
    {
        if (type == FieldValueType.INTEGER) return number.stripTrailingZeros().toPlainString()
        val atConfiguredScale = scale
            ?.takeIf { it >= 0 }
            ?.let { runCatching { number.setScale(it, RoundingMode.UNNECESSARY) }.getOrNull() }
        return (atConfiguredScale ?: number.stripTrailingZeros()).toPlainString()
    }

    private fun integerDigits(number: BigDecimal): Int =
        (number.precision() - number.scale()).coerceAtLeast(1)
}
