package com.docuhyphen.app.api.resource.command

object CommandIdempotencyKeyHeader {
    const val MAX_LENGTH = 128
    const val INVALID_REASON_CODE = "COMMAND_IDEMPOTENCY_KEY_INVALID"
    const val TOO_LONG_MESSAGE = "Idempotency-Key has at most $MAX_LENGTH characters"

    fun stated(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    fun exceedsLimit(key: String): Boolean = key.length > MAX_LENGTH
}
