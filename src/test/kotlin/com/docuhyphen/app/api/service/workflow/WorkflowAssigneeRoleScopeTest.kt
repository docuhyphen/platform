package com.docuhyphen.app.api.service.workflow

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorkflowAssigneeRoleScopeTest
{
    @Test
    fun `app role assignee rejects an organization role`()
    {
        val json = workflowWithAssignee("""{"kind":"APP_ROLE","roleName":"ORG_ADMIN"}""")

        assertThrows(SerializationException::class.java) { WorkflowSpecJson.decode(json) }
    }

    @Test
    fun `organization role assignee rejects an app role`()
    {
        val json = workflowWithAssignee(
            """{"kind":"ORGANIZATION_ROLE","roleName":"APP_ADMIN","organizationIdRef":"${'$'}subject.orgId"}""",
        )

        assertThrows(SerializationException::class.java) { WorkflowSpecJson.decode(json) }
    }

    private fun workflowWithAssignee(assignee: String): String =
        """{"steps":[{"type":"APPROVAL","assignees":[$assignee]}]}"""
}
