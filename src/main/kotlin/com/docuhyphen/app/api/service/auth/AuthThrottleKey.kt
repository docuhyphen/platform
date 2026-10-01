package com.docuhyphen.app.api.service.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AuthThrottleKey
{
    fun hash(value: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
