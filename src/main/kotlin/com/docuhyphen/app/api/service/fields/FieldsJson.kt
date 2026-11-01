package com.docuhyphen.app.api.service.fields

import kotlinx.serialization.json.Json

object FieldsJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
}
