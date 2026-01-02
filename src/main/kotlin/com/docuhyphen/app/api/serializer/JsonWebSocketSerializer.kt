package com.docuhyphen.app.api.serializer

import jakarta.websocket.Decoder
import jakarta.websocket.Encoder
import jakarta.websocket.EndpointConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JsonWebSocketSerializer : Encoder.Text<Any>, Decoder.Text<Any>
{
    private val json = Json { ignoreUnknownKeys = true }

    override fun encode(obj: Any): String
    {
        return json.encodeToString(obj)
    }

    override fun init(config: EndpointConfig)
    {
    }

    override fun destroy()
    {
    }

    override fun decode(s: String): Any
    {
        return json.decodeFromString(s)
    }

    override fun willDecode(s: String): Boolean
    {
        return true
    }
}