package com.docuhyphen.app.api.util

object UserAgentParser
{
    fun parse(userAgent: String?): String
    {
        if (userAgent.isNullOrBlank()) return "Unknown device"
        val browser = detectBrowser(userAgent)
        val os = detectOs(userAgent)
        return if (os != null) "$browser on $os" else browser
    }

    private fun detectBrowser(ua: String): String = when
    {
        ua.contains("Edg/") || ua.contains("Edge/") -> "Edge"
        ua.contains("OPR/") || ua.contains("Opera/") -> "Opera"
        ua.contains("Firefox/") -> "Firefox"
        // Chrome must come after Edge and Opera since those UAs also contain "Chrome/"
        ua.contains("Chrome/") -> "Chrome"
        ua.contains("Safari/") -> "Safari"
        else -> "Browser"
    }

    private fun detectOs(ua: String): String? = when
    {
        ua.contains("Android") -> "Android"
        ua.contains("iPhone") || ua.contains("iPad") -> "iOS"
        ua.contains("Windows") -> "Windows"
        ua.contains("Mac OS X") || ua.contains("Macintosh") -> "macOS"
        ua.contains("Linux") -> "Linux"
        else -> null
    }
}
