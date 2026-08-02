package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.service.exchange.ExchangeEmailDelivery
import com.docuhyphen.app.api.service.exchange.ExchangeInAppDelivery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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

    @Test
    fun `initiator does not receive an in app notification for their own exchange initiation`()
    {
        val fixture = ExchangeInitiationAutoAcceptFixture()

        val exchange = fixture.initiate()

        val inAppCaptor = argumentCaptor<List<ExchangeInAppDelivery>>()
        val refreshCaptor = argumentCaptor<Set<java.util.UUID>>()
        verify(fixture.exchangeNotificationDeliveryService).scheduleAfterCommit(
            eq(exchange.id),
            eq(exchange.status.name),
            any<List<ExchangeEmailDelivery>>(),
            inAppCaptor.capture(),
            refreshCaptor.capture(),
        )

        val notifiedUserIds = inAppCaptor.firstValue.map { it.appUserId }.toSet()
        assertFalse(notifiedUserIds.contains(fixture.initiator.id))
        assertTrue(notifiedUserIds.contains(fixture.recipient.id))
        assertTrue(refreshCaptor.firstValue.contains(fixture.initiator.id))
    }

    @Test
    fun `initiator email recipient label does not duplicate external recipient name`()
    {
        val fixture = ExchangeInitiationAutoAcceptFixture()
        fixture.recipient.isTemporary = true
        fixture.recipient.person = Person().apply {
            firstName = "Jane"
            lastName = "Doe"
        }

        fixture.initiate(
            primaryRecipient = ExternalEmailRecipientSelectionRequest(
                fixture.recipient.email,
                "Jane",
                "Doe",
            ),
            requestRecipientSignIn = true,
        )

        val recipientLabelCaptor = argumentCaptor<String>()
        verify(fixture.emailTemplateService).renderExchangeCreatedInitiatorEmail(
            any(),
            any(),
            recipientLabelCaptor.capture(),
            any(),
        )
        assertEquals("Jane Doe", recipientLabelCaptor.firstValue)
    }
}
