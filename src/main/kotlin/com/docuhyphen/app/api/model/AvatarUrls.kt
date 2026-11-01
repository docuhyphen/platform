package com.docuhyphen.app.api.model

import java.util.*

object AvatarUrls
{
    fun forUser(userId: UUID): String = "/app-user/$userId/avatar"
}

