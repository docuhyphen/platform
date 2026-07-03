package com.docuhyphen.app.api.service.fields

import kotlinx.serialization.json.Json

/**
 * Shared kotlinx.serialization [Json] for the Fields engine. Lenient on unknown keys so older
 * persisted constraint/option JSON keeps parsing as the contract grows; encodes defaults so
 * round-tripped canonical JSON is explicit.
 */
object FieldsJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
