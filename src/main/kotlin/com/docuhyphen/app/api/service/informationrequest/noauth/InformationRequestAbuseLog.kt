package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import org.slf4j.LoggerFactory

object InformationRequestAbuseLog
{
    const val MARKER = "INFORMATION_REQUEST_ABUSE_REFUSED"

    private val logger = LoggerFactory.getLogger(InformationRequestAbuseLog::class.java)

    fun line(control: InformationRequestAbuseControl, detail: String = ""): String =
        listOf("$MARKER control=$control", detail.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    fun refused(control: InformationRequestAbuseControl, detail: String = "")
    {
        logger.warn(line(control, detail))
    }
}
