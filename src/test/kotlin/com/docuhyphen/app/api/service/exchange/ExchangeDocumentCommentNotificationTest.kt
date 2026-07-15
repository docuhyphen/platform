package com.docuhyphen.app.api.service.exchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExchangeDocumentCommentNotificationTest
{
    @Test
    fun `identifies the commenter document and Exchange`()
    {
        val message = buildDocumentCommentNotificationMessage(
            commenterName = "Jane Doe",
            documentName = "Signed agreement.pdf",
            exchangeName = "Supplier onboarding",
        )

        assertEquals(
            "Jane Doe commented on \"Signed agreement.pdf\" in Exchange \"Supplier onboarding\".",
            message,
        )
    }
}
