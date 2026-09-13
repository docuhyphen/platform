package com.docuhyphen.app.api.service.fields

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * The canonical reading of a date-time answer.
 *
 * A date-time names one moment, so the moment is what gets stored and compared. The offset the
 * responder wrote it at is kept beside the moment rather than folded into it, so the answer can
 * still be presented the way it was given. A reading that carries no offset records none and is
 * taken as UTC, which is the only deterministic reading available for it; callers that know the
 * responder's offset are expected to send it.
 */
object CanonicalDateTime
{
    /** [offsetMinutes] is null when the reading carried no offset of its own. */
    data class Reading(val instant: Instant, val offsetMinutes: Int?)

    /** ISO forms carrying an offset, extended form first, then the two basic forms. */
    private val offsetForms: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        offsetForm("+HHMM"),
        offsetForm("+HH"),
    )

    /**
     * Seconds are always written, sub-second digits only when the moment has them, and the offset
     * as an ISO id so a UTC reading is a plain `Z`.
     */
    private val canonicalForm: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
        .appendOffsetId()
        .toFormatter()

    /** The moment [raw] names, or null when it is not an ISO date-time. */
    fun parse(raw: String): Reading?
    {
        for (form in offsetForms)
        {
            val withOffset = runCatching { OffsetDateTime.parse(raw, form) }.getOrNull() ?: continue
            return Reading(withOffset.toInstant(), withOffset.offset.totalSeconds / SECONDS_PER_MINUTE)
        }
        val withoutOffset = runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: return null
        return Reading(withoutOffset.toInstant(ZoneOffset.UTC), null)
    }

    /** The canonical text for a stored moment, written at the offset it was submitted with. */
    fun format(instant: Instant, offsetMinutes: Int?): String =
        canonicalForm.format(instant.atOffset(offsetOf(offsetMinutes)))

    /** True when [offsetMinutes] is an offset a reading can actually carry. */
    fun isSupportedOffset(offsetMinutes: Int): Boolean =
        runCatching { ZoneOffset.ofTotalSeconds(offsetMinutes * SECONDS_PER_MINUTE) }.isSuccess

    private fun offsetOf(offsetMinutes: Int?): ZoneOffset = offsetMinutes
        ?.let { runCatching { ZoneOffset.ofTotalSeconds(it * SECONDS_PER_MINUTE) }.getOrNull() }
        ?: ZoneOffset.UTC

    private fun offsetForm(offsetPattern: String): DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .appendOffset(offsetPattern, "Z")
        .toFormatter()

    private const val SECONDS_PER_MINUTE = 60
}
