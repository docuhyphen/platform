package com.docuhyphen.app.api.service.auth.idp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

object OAuthJsonParser
{
    private val objectMapper = ObjectMapper()

    private val mapTypeRef = object : TypeReference<Map<String, Any?>>() {}

    fun parseJsonToMap(json: String): Map<String, Any?>
    {
        return objectMapper.readValue(json, mapTypeRef) ?: emptyMap()
    }
}
