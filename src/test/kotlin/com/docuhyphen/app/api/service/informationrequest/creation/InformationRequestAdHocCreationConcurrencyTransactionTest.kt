package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAdHocCreationConcurrencyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var creation: InformationRequestAdHocCreationService

    private val race by lazy { OverlappingCommandRace(dataSource) }

    @Test
    fun `overlapping retries of one creation command create one request and both return it`() {
        val owner = insertOwner()
        val exchangeId = insertExchange(owner)
        val command = CreateAdHocInformationRequestCommand(
            exchangeId = exchangeId,
            displayName = "Process collection",
            configuration = InformationRequestTemplateConfigurationRequest(
                sections = listOf(
                    InformationRequestTemplateSectionRequest(
                        "requested-records",
                        "Requested records",
                        requirements = listOf(
                            InformationRequestTemplateRequirementRequest(
                                "supporting-record",
                                InformationRequestRequirementType.DOCUMENT,
                                "Provide the supporting record",
                                permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                                evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                            ),
                        ),
                    ),
                ),
            ),
            access = RequestAccessContext(PrincipalRef.user(owner), AuthorizationContext(sessionRef = "owner-session")),
            idempotencyKey = "create-${UUID.randomUUID()}",
        )

        val outcome = race.whileRowLocked("exchange", exchangeId, List(2) { { creation.createAdHoc(command) } })

        assertTrue(outcome.overlapped, "both retries wait before creating the request")
        assertTrue(outcome.results.all { it.isSuccess }, outcome.results.toString())
        assertEquals(1, outcome.results.map { it.getOrThrow().request.id }.toSet().size)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM information_request WHERE exchange_id = ?", exchangeId)
            )
            assertEquals(
                1,
                queryInt(connection, "SELECT count(*) FROM command_receipt WHERE resource_id = ?", exchangeId)
            )
        }
    }

    private fun insertOwner(): UUID {
        val owner = UUID.randomUUID()
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
                owner,
                "creation-${owner.toString().take(8)}@process.test",
            )
            execute(
                connection,
                "INSERT INTO user_subscription_policy (id, app_user_id, plan_code, subscription_status) VALUES (?, ?, 'PERSONAL', 'ACTIVE')",
                UUID.randomUUID(),
                owner,
            )
        }
        return owner
    }

    private fun insertExchange(owner: UUID): UUID {
        val exchangeId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO exchange (id, owner_user_id, initiator_id, is_deleted, require_recipient_sign_in,
                    created_date, last_activity, description, initial_share_message, name, status)
                VALUES (?, ?, ?, FALSE, FALSE, now(), now(), 'Collect records', 'Please respond', 'Process collection', 'ACCEPTED_STARTED')
                """.trimIndent(),
                exchangeId,
                owner,
                owner,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name,
                    source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                exchangeId,
                owner,
            )
        }
        return exchangeId
    }
}
