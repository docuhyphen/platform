package com.docuhyphen.app.api.service.informationrequest.notice

import java.security.MessageDigest

object InformationRequestNoticeContentHash {
    fun of(subject: String, body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$subject\u0000$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}