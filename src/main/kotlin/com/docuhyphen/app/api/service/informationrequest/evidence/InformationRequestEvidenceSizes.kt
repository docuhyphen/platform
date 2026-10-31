package com.docuhyphen.app.api.service.informationrequest.evidence

object InformationRequestEvidenceSizes {
    private const val KIBIBYTE = 1024L
    private const val MEBIBYTE = KIBIBYTE * 1024
    private const val GIBIBYTE = MEBIBYTE * 1024

    fun describe(bytes: Long): String = when {
        bytes == 1L -> "1 byte"
        bytes < KIBIBYTE -> "$bytes bytes"
        bytes < MEBIBYTE -> "${scaled(bytes, KIBIBYTE)} KiB"
        bytes < GIBIBYTE -> "${scaled(bytes, MEBIBYTE)} MiB"
        else -> "${scaled(bytes, GIBIBYTE)} GiB"
    }

    private fun scaled(bytes: Long, unit: Long): String {
        val tenths = (bytes * 10 + unit / 2) / unit
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
