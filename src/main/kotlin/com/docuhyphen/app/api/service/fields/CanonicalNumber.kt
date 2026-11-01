package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import java.math.BigDecimal
import java.math.RoundingMode

object CanonicalNumber
{
    const val MAX_FRACTION_DIGITS = 10
    const val MAX_INTEGER_DIGITS = 28

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

    fun assertStorableScale(scale: Int)
    {
        if (scale < 0 || scale > MAX_FRACTION_DIGITS)
        {
            throw FieldValidationException("A decimal scale must be between 0 and $MAX_FRACTION_DIGITS")
        }
    }

    fun significantScale(number: BigDecimal): Int =
        number.stripTrailingZeros().scale().coerceAtLeast(0)

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
