package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestCommandAuthorityTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var authorities: InformationRequestDelegatedAuthorityService

    @Inject
    lateinit var lifecycle: InformationRequestLifecycleService

    @Inject
    lateinit var completionGates: InformationRequestCompletionGateService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var artifacts: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `a party assignment replay needs the authority to manage parties that the command needed`() {
        val fixture = fixture()
        val managerParty = fixture.assign(
            fixture.owner,
            fixture.memberId,
            InformationRequestShareRoleKey.DECISION_MAKER,
            "assign-manager"
        )
        fixture.assign(
            fixture.owner,
            fixture.memberId,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            "assign-member-contributor"
        )
        val command = fixture.assignCommand(
            fixture.member,
            fixture.user(),
            InformationRequestShareRoleKey.CONTRIBUTOR,
            "manager-assigns"
        )
        QuarkusTransaction.requiringNew().call { partyAssignments.assign(command) }
        fixture.revoke(managerParty, "revoke-manager")

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { partyAssignments.assign(command) }
        }
    }

    @Test
    fun `a delegated authority replay needs the authority to manage parties that the command needed`() {
        val fixture = fixture()
        val managerParty = fixture.assign(
            fixture.owner,
            fixture.memberId,
            InformationRequestShareRoleKey.DECISION_MAKER,
            "assign-manager"
        )
        fixture.assign(
            fixture.owner,
            fixture.memberId,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            "assign-member-contributor"
        )
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.requestId,
            assignedPartyId = fixture.runtime.contributorPartyId,
            delegatePrincipal = PrincipalRef.user(fixture.user()),
            access = fixture.member,
            precondition = fixture.partiesPrecondition(),
            idempotencyKey = "manager-delegates",
        )
        QuarkusTransaction.requiringNew().call { authorities.grant(command) }
        fixture.revoke(managerParty, "revoke-manager")

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { authorities.grant(command) }
        }
    }

    @Test
    fun `a caller without access who assigns a party with a stale precondition is refused as forbidden`() {
        val fixture = fixture()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                partyAssignments.assign(
                    fixture.assignCommand(
                        fixture.outsider,
                        fixture.memberId,
                        InformationRequestShareRoleKey.CONTRIBUTOR,
                        "outsider-assigns",
                        STALE
                    ),
                )
            }
        }
    }

    @Test
    fun `a caller without access who revokes a party with a stale precondition is refused as forbidden`() {
        val fixture = fixture()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                partyRevocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        fixture.requestId,
                        fixture.runtime.contributorPartyId,
                        fixture.outsider,
                        STALE,
                        "outsider-revokes"
                    ),
                )
            }
        }
    }

    @Test
    fun `a caller without access who delegates authority with a stale precondition is refused as forbidden`() {
        val fixture = fixture()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                authorities.grant(
                    GrantInformationRequestDelegatedAuthorityCommand(
                        requestId = fixture.requestId,
                        assignedPartyId = fixture.runtime.contributorPartyId,
                        delegatePrincipal = PrincipalRef.user(fixture.memberId),
                        access = fixture.outsider,
                        precondition = STALE,
                        idempotencyKey = "outsider-delegates",
                    ),
                )
            }
        }
    }

    @Test
    fun `a caller without access who cancels the request with a stale precondition is refused as forbidden`() {
        val fixture = fixture()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                lifecycle.cancel(
                    CancelInformationRequestCommand(
                        fixture.requestId,
                        "outsider.cancel",
                        fixture.outsider,
                        STALE,
                        "outsider-cancels"
                    )
                )
            }
        }
    }

    @Test
    fun `a caller without access who changes the completion gate with a stale precondition is refused as forbidden`() {
        val fixture = fixture()

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                completionGates.change(
                    ChangeInformationRequestCompletionGateCommand(
                        fixture.requestId,
                        false,
                        fixture.outsider,
                        STALE,
                        "outsider-gates"
                    )
                )
            }
        }
    }

    @Test
    fun `a respondent's withdrawal replay does not show a file a manager has since removed`() {
        val fixture = fixture()
        fixture.assign(fixture.owner, fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-manager")
        fixture.grantContributorShare()
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        val withdrawal =
            fixture.evidenceCommand(fixture.access(fixture.runtime.contributorUserId), "contributor-withdraws", null)
        QuarkusTransaction.requiringNew().call { services.evidence.withdraw(withdrawal) }
        QuarkusTransaction.requiringNew().call {
            services.evidence.remove(
                fixture.evidenceCommand(
                    fixture.member,
                    "manager-removes",
                    "Removed for an unreadable scan"
                )
            )
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { services.evidence.withdraw(withdrawal) }
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
    }

    private companion object {
        val STALE = CommandPrecondition.ExpectedRevision(setOf("\"stale\""))
    }

    private fun fixture(): AuthorityFixture = dataSource.connection.use { connection ->
        val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtimeFixture.exchangeId,
            runtimeFixture.template.userId,
        )
        execute(
            connection,
            """
            INSERT INTO request_execution_grant
                (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                 enforcement_mode, acting_party_cap, issued_at, created_at)
            VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtimeFixture.requestId,
            runtimeFixture.template.organizationId,
        )
        val memberId = UUID.randomUUID()
        runtimeFixture.insertUser(memberId)
        AuthorityFixture(runtimeFixture, memberId)
    }

    private inner class AuthorityFixture(val runtime: SubmissionRuntimeSqlFixture, val memberId: UUID) {
        val requestId: UUID = runtime.requestId
        val owner = access(runtime.template.userId)
        val member = access(memberId)
        val outsider = access(UUID.randomUUID())

        fun access(userId: UUID) =
            RequestAccessContext(
                PrincipalRef.user(userId),
                AuthorizationContext(sessionRef = "authority-session", mfaSatisfied = true)
            )

        fun assign(
            access: RequestAccessContext,
            userId: UUID,
            role: InformationRequestShareRoleKey,
            key: String
        ): UUID =
            QuarkusTransaction.requiringNew()
                .call { partyAssignments.assign(assignCommand(access, userId, role, key)) }.party.id

        fun assignCommand(
            access: RequestAccessContext,
            userId: UUID,
            role: InformationRequestShareRoleKey,
            key: String,
            precondition: CommandPrecondition = partiesPrecondition(),
        ) = AssignInformationRequestPartyCommand(
            requestId = requestId,
            roleKey = role,
            principal = PrincipalRef.user(userId),
            access = access,
            precondition = precondition,
            idempotencyKey = key,
        )

        fun revoke(partyId: UUID, key: String) {
            QuarkusTransaction.requiringNew().run {
                partyRevocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        requestId,
                        partyId,
                        owner,
                        partiesPrecondition(),
                        key
                    )
                )
            }
        }

        fun evidenceCommand(access: RequestAccessContext, key: String, reason: String?) =
            ChangeInformationRequestEvidenceStateCommand(
                requestId = requestId,
                requirementId = runtime.documentRequirementId,
                artifactId = runtime.artifactId,
                access = access,
                precondition = QuarkusTransaction.requiringNew().call {
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.artifactOf(
                            requireNotNull(
                                artifacts.findById(
                                    runtime.artifactId
                                )
                            )
                        )
                    )
                },
                idempotencyKey = key,
                reason = reason,
            )

        fun partiesPrecondition(): CommandPrecondition = QuarkusTransaction.requiringNew().call {
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(
                    requireNotNull(
                        requests.findById(
                            requestId
                        )
                    )
                )
            )
        }

        fun grantContributorShare() {
            dataSource.connection.use { connection ->
                val shareId = UUID.randomUUID()
                execute(
                    connection,
                    """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
                    """.trimIndent(),
                    shareId,
                    requestId,
                    runtime.contributorUserId,
                )
                execute(
                    connection,
                    "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                    shareId,
                    runtime.contributorPartyId
                )
            }
        }

        fun user(): UUID = UUID.randomUUID().also { id ->
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
                    "authority-${id.toString().take(8)}@process.test",
                )
            }
        }
    }
}
