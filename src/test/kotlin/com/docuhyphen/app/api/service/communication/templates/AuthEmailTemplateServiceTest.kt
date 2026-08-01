package com.docuhyphen.app.api.service.communication.templates

import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthEmailTemplateServiceTest
{
    private val renderer = mock<EmailTemplateRenderer>()
    private val configurationService = mock<ConfigurationService>().also {
        whenever(it.emailSubjectTitle).thenReturn("DocuHyphen")
    }
    private val service = AuthEmailTemplateService(renderer, configurationService)

    @Test
    fun `step-up email model uses a friendly action label`()
    {
        whenever(renderer.render(eq("step-up-email-MFA.ftl"), any())).thenReturn("<html></html>")

        service.renderStepUpMfaEmail("123456", 10, "WORKFLOW_DEFINITION_SAVE")

        val modelCaptor = argumentCaptor<Map<String, Any>>()
        verify(renderer).render(eq("step-up-email-MFA.ftl"), modelCaptor.capture())

        val actionDescription = modelCaptor.firstValue["actionDescription"].toString()
        assertEquals("save this workflow", actionDescription)
        assertFalse(actionDescription.contains("_"))
    }
}
