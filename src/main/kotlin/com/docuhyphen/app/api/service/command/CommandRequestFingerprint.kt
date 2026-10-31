package com.docuhyphen.app.api.service.command

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAmount
import java.util.*

object CommandRequestFingerprint {
    fun sha256Hex(canonicalRequest: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(canonicalRequest.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun of(vararg fields: Any?): String = sha256Hex(canonical(fields.asList()))

    fun canonical(fields: List<Any?>): String = JsonArray(fields.map(::element)).toString()

    fun <T> serialized(serializer: SerializationStrategy<T>, value: T?): JsonElement =
        value?.let { STATED.encodeToJsonElement(serializer, it) } ?: JsonNull

    private val STATED = Json { encodeDefaults = true }

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int, is Long, is Short, is Byte -> JsonPrimitive(value as Number)
        is BigDecimal -> JsonPrimitive(value.toPlainString())
        is BigInteger -> JsonPrimitive(value.toString())
        is UUID -> JsonPrimitive(value.toString())
        is Enum<*> -> JsonPrimitive(value.name)
        is Timestamp -> JsonPrimitive(value.toInstant().toString())
        is Date -> JsonPrimitive(Instant.ofEpochMilli(value.time).toString())
        is TemporalAccessor, is TemporalAmount -> JsonPrimitive(value.toString())
        is JsonElement -> sorted(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, entry) -> key(key) to element(entry) }.toSortedMap())
        is Set<*> -> JsonArray(value.map(::element).sortedBy(JsonElement::toString))
        is Collection<*> -> JsonArray(value.map(::element))
        is Array<*> -> JsonArray(value.map(::element))
        else -> throw IllegalArgumentException("A command fingerprint field has no canonical form: ${value::class.qualifiedName}")
    }

    private fun key(key: Any?): String = when (key) {
        is String -> key
        is UUID -> key.toString()
        is Enum<*> -> key.name
        else -> throw IllegalArgumentException("A command fingerprint map key has no canonical form: ${key?.let { it::class.qualifiedName }}")
    }

    private fun sorted(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (_, entry) -> sorted(entry) }.toSortedMap())
        is JsonArray -> JsonArray(value.map(::sorted))
        else -> value
    }
}
