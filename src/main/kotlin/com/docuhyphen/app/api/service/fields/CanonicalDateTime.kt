package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.fields.CanonicalDateTimeReading
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object CanonicalDateTime
{
    private val offsetForms: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        offsetForm("+HHMM"),
        offsetForm("+HH"),
    )

    private val canonicalForm: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
        .appendOffsetId()
        .toFormatter()

    fun parse(raw: String): CanonicalDateTimeReading?
    {
        for (form in offsetForms)
        {
            val withOffset = runCatching { OffsetDateTime.parse(raw, form) }.getOrNull() ?: continue
            return CanonicalDateTimeReading(withOffset.toInstant(), withOffset.offset.totalSeconds / SECONDS_PER_MINUTE)
        }
        val withoutOffset = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return null
        return CanonicalDateTimeReading(withoutOffset.toInstant(ZoneOffset.UTC), null)
    }

    fun format(instant: Instant, offsetMinutes: Int?): String =
        canonicalForm.format(instant.atOffset(offsetOf(offsetMinutes)))

    private fun offsetOf(offsetMinutes: Int?): ZoneOffset = offsetMinutes
        ?.let { runCatching { ZoneOffset.ofTotalSeconds(it * SECONDS_PER_MINUTE) }.getOrNull() }
        ?: ZoneOffset.UTC

    private fun offsetForm(offsetPattern: String): DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .appendOffset(offsetPattern, "Z")
        .toFormatter()

    private const val SECONDS_PER_MINUTE = 60
}
