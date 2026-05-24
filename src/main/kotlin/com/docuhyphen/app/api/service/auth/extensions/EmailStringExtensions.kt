package com.docuhyphen.app.api.service.auth.extensions

fun String?.normalizeEmailOrNull(): String?
{
    return this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}

fun String.maskEmailForLogs(): String
{
    val at = indexOf('@')

    if (at <= 1)
    {
        return "***"
    }

    return "${first()}***${substring(at)}"
}

