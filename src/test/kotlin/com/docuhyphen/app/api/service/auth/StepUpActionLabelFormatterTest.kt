package com.docuhyphen.app.api.service.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepUpActionLabelFormatterTest
{
    @Test
    fun `uses explicit workflow save label`()
    {
        assertEquals("save this workflow", StepUpActionLabelFormatter.labelFor("WORKFLOW_DEFINITION_SAVE"))
    }

    @Test
    fun `formats unknown enum-like action labels`()
    {
        assertEquals("update audit export", StepUpActionLabelFormatter.labelFor("ORG_AUDIT_EXPORT_UPDATE"))
    }

    @Test
    fun `keeps friendly action labels unchanged`()
    {
        assertEquals("save this workflow", StepUpActionLabelFormatter.labelFor("save this workflow"))
    }
}
