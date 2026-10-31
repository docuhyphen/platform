package com.docuhyphen.app.api.repository.workflow

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class WorkflowDefinitionPersonalTriggerTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var definitions: WorkflowDefinitionRepository

    @Test
    fun `a personal trigger resolves only its owner's active personal definitions when the owner has any`() {
        val trigger = "information_request.submitted.${UUID.randomUUID()}"
        val owner = insertUser()
        val otherUser = insertUser()
        val platform = insertDefinition(trigger, "APP", null)
        val personal = insertDefinition(trigger, "PERSONAL", owner)
        insertDefinition(trigger, "PERSONAL", otherUser)
        insertDefinition(trigger, "PERSONAL", owner, active = false)
        insertDefinition(trigger, "PERSONAL", owner, deleted = true)

        val resolved =
            QuarkusTransaction.requiringNew().call { definitions.findAllActiveForPersonalTrigger(trigger, owner) }
        val unscoped = QuarkusTransaction.requiringNew().call { definitions.findAllActiveForTrigger(trigger, null) }

        assertEquals(listOf(personal), resolved.map { it.id })
        assertEquals(listOf(platform), unscoped.map { it.id })
    }

    @Test
    fun `a personal trigger falls back to the platform definitions when its owner has none`() {
        val trigger = "information_request.submitted.${UUID.randomUUID()}"
        val owner = insertUser()
        val platform = insertDefinition(trigger, "APP", null)
        insertDefinition(trigger, "PERSONAL", insertUser())

        val resolved =
            QuarkusTransaction.requiringNew().call { definitions.findAllActiveForPersonalTrigger(trigger, owner) }

        assertEquals(listOf(platform), resolved.map { it.id })
    }

    private fun insertDefinition(
        trigger: String,
        scope: String,
        creator: UUID?,
        active: Boolean = true,
        deleted: Boolean = false,
    ): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO workflow_definition
                    (id, name, version, scope, trigger_event, steps_json, is_active, is_deleted, created_by_app_user_id, created_at)
                VALUES (?, ?, 1, ?, ?, '{"steps":[]}', ?, ?, ?, now())
                """.trimIndent(),
                id,
                "Process check ${id.toString().take(8)}",
                scope,
                trigger,
                active,
                deleted,
                creator,
            )
        }
        return id
    }

    private fun insertUser(): UUID {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                id,
                "workflow-owner-${id.toString().take(8)}@process.test",
            )
        }
        return id
    }
}
