package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.Grant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry.Companion.toResourceKind
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class RequestRecordAuthorizationContextTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var authorization: AuthorizationService

    @Test
    fun `only the platform control-plane types carry no resource-level authorization context`() {
        assertEquals(
            setOf(ResourceType.APPLICATION, ResourceType.WORKFLOW_WEBHOOK_ENDPOINT),
            ResourceType.entries.filter { it.toResourceKind() == null }.toSet(),
        )
    }

    @Test
    fun `a request record is refused for an organization administrator until its own facts can be resolved`() {
        val administrator = organizationAdministrator()
        val principal = PrincipalRef.user(administrator.userId)
        val context = AuthorizationContext(activeOrgId = administrator.organizationId)

        val outcomes = QuarkusTransaction.requiringNew().call {
            REQUEST_RECORD_TYPES.associateWith { type ->
                val record = ResourceRef(type, UUID.randomUUID())
                authorization.authorize(principal, Action.INFORMATION_REQUEST_VIEW, record, context) to
                        authorization.grantsOn(principal, record, context)
            }
        }

        assertAll(
            outcomes.map { (type, outcome) ->
                Executable {
                    val (decision, grants) = outcome
                    assertEquals(
                        Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED,
                        (decision as? Decision.Deny)?.reasonCode,
                        "$type was decided as $decision",
                    )
                    assertTrue(grants.isEmpty(), "$type contributed $grants")
                }
            },
        )
    }

    @Test
    fun `a platform control-plane type keeps taking organization grants from the selected organization`() {
        val administrator = organizationAdministrator()
        val principal = PrincipalRef.user(administrator.userId)
        val context = AuthorizationContext(activeOrgId = administrator.organizationId)

        val grants = QuarkusTransaction.requiringNew().call {
            authorization.grantsOn(
                principal,
                ResourceRef(ResourceType.WORKFLOW_WEBHOOK_ENDPOINT, UUID.randomUUID()),
                context
            )
        }

        assertTrue(grants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }, "$grants")
    }

    private fun organizationAdministrator(): OrganizationAdministrator {
        val administrator = OrganizationAdministrator(UUID.randomUUID(), UUID.randomUUID())
        val membershipId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Process records organization', ?, TRUE, TRUE, now())
                """.trimIndent(),
                administrator.organizationId,
                "REG-${administrator.organizationId.toString().take(8)}",
            )
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                administrator.userId,
                "record-administrator-${administrator.userId.toString().take(8)}@process.test",
            )
            execute(
                connection,
                """
                INSERT INTO organization_membership
                    (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                VALUES (?, ?, ?, 'ACTIVE', TRUE, now(), now())
                """.trimIndent(),
                membershipId,
                administrator.userId,
                administrator.organizationId,
            )
            execute(
                connection,
                "INSERT INTO organization_membership_role (organization_membership_id, role_name) VALUES (?, 'ORG_ADMIN')",
                membershipId,
            )
        }
        return administrator
    }

    private class OrganizationAdministrator(val userId: UUID, val organizationId: UUID)

    private companion object {
        val REQUEST_RECORD_TYPES = listOf(
            ResourceType.INFORMATION_REQUEST_PARTY,
            ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY,
            ResourceType.INFORMATION_REQUEST_ACCESS_LINK,
            ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK,
            ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT,
            ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE,
            ResourceType.INFORMATION_REQUEST_SUBMISSION_ATTESTATION,
            ResourceType.INFORMATION_REQUEST_AMENDMENT,
            ResourceType.INFORMATION_REQUEST_REVIEW,
            ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT,
            ResourceType.INFORMATION_REQUEST_REVIEW_FINDING,
            ResourceType.INFORMATION_REQUEST_REVIEW_COMMENT,
            ResourceType.INFORMATION_REQUEST_ACCEPTED_FACT,
            ResourceType.INFORMATION_REQUEST_FACT_RECERTIFICATION,
            ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE,
            ResourceType.INFORMATION_REQUEST_BUSINESS_DECISION,
            ResourceType.INFORMATION_REQUEST_RECORD_EXPORT,
            ResourceType.INFORMATION_REQUEST_PRIVACY_REQUEST,
            ResourceType.INFORMATION_REQUEST_SUBJECT_RESTRICTION,
            ResourceType.RECORD_PRESERVATION_HOLD,
            ResourceType.RECORD_RETENTION_SCHEDULE,
            ResourceType.INFORMATION_REQUEST_CLOCK_POLICY,
            ResourceType.INFORMATION_REQUEST_TEMPLATE,
        )
    }
}
