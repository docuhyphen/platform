package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestAbuseControl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InformationRequestAbuseLogTest
{
    @Test
    fun `an abuse refusal logs one stable marker that names its control`()
    {
        assertEquals(
            "INFORMATION_REQUEST_ABUSE_REFUSED control=REMINDER_COOLDOWN request=request-a",
            InformationRequestAbuseLog.line(InformationRequestAbuseControl.REMINDER_COOLDOWN, "request=request-a"),
        )
        assertEquals(
            "INFORMATION_REQUEST_ABUSE_REFUSED control=NO_AUTH_CHALLENGE_RATE",
            InformationRequestAbuseLog.line(InformationRequestAbuseControl.NO_AUTH_CHALLENGE_RATE),
        )
    }
}
