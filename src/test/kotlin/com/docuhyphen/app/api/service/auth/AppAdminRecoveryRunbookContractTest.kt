package com.docuhyphen.app.api.service.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AppAdminRecoveryRunbookContractTest
{
    private val runbook = Files.readString(Path.of("docs/APP_ADMIN_RECOVERY_RUNBOOK.md"))

    @Test
    fun `runbook uses the complete effective assignment boundary`()
    {
        assertTrue(runbook.contains("r.is_active = true"))
        assertTrue(runbook.contains("r.expires_at IS NULL OR r.expires_at > CURRENT_TIMESTAMP"))
        assertTrue(runbook.contains("u.is_active = true"))
        assertTrue(runbook.contains("u.deprovisioned_at IS NULL"))
    }

    @Test
    fun `runbook uses controlled bootstrap and audited application operations`()
    {
        assertTrue(runbook.contains("APP_ADMIN_BOOTSTRAP_EMAIL"))
        assertTrue(runbook.contains("AppAdminBootstrapEmail"))
        assertTrue(runbook.contains("DELETE /admin/roles/app-admins/{assignmentId}"))
        assertTrue(runbook.contains("admin.app_admin.grant"))
        assertTrue(runbook.contains("admin.app_admin.revoke"))
    }

    @Test
    fun `runbook does not prescribe direct assignment writes`()
    {
        assertFalse(runbook.contains("INSERT INTO app_role_assignment", ignoreCase = true))
        assertFalse(runbook.contains("UPDATE app_role_assignment", ignoreCase = true))
        assertFalse(runbook.contains("DELETE FROM app_role_assignment", ignoreCase = true))
    }
}
