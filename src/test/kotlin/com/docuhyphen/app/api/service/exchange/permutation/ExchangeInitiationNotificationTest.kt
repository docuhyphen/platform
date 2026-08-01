package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify

class ExchangeInitiationNotificationTest
{
    @Test
    fun `existing user selected by email does not receive registration instructions`()
    {
        val fixture = ExchangeInitiationAutoAcceptFixture()
        fixture.recipient.isTemporary = false

        fixture.initiate(
            primaryRecipient = ExternalEmailRecipientSelectionRequest(
                fixture.recipient.email,
                "Existing",
                "Recipient",
            ),
            requestRecipientSignIn = true,
        )

        verify(fixture.emailTemplateService).renderExchangeCreatedRecipientEmail(
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            eq(false),
            isNull(),
            isNull(),
        )
    }
}
