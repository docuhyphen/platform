package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceCollectionState
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyAssignmentService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import io.quarkus.narayana.jta.QuarkusTransaction
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
class InformationRequestEvidencePartyOwnershipTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var collection: InformationRequestEvidenceCollectionService

    @Inject
    lateinit var queries: InformationRequestEvidenceQueryService

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var artifacts: InformationRequestEvidenceArtifactRepository

    @Test
    fun `a party sharing the Requirement neither withdraws nor removes a file another party provided`() {
        val fixture = fixture()

        val refusals = listOf(
            assertThrows(InformationRequestLifecycleException::class.java) {
                QuarkusTransaction.requiringNew()
                    .call { collection.withdraw(fixture.stateCommand(fixture.coParty, "co-party-withdraws")) }
            },
            assertThrows(InformationRequestLifecycleException::class.java) {
                QuarkusTransaction.requiringNew()
                    .call { collection.remove(fixture.stateCommand(fixture.coParty, "co-party-removes", REASON)) }
            },
        )

        assertEquals(
            List(2) { InformationRequestErrorCatalog.EVIDENCE_PROVIDED_BY_ANOTHER_PARTY },
            refusals.map { it.reasonCode },
        )
        assertEquals(InformationRequestEvidenceCollectionState.ACTIVE, fixture.artifactState())
    }

    @Test
    fun `the providing party withdraws its own file and a manager may remove any file`() {
        val fixture = fixture()

        QuarkusTransaction.requiringNew()
            .call { collection.withdraw(fixture.stateCommand(fixture.provider, "provider-withdraws")) }
        assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, fixture.artifactState())

        QuarkusTransaction.requiringNew()
            .call { collection.remove(fixture.stateCommand(fixture.manager, "manager-removes", REASON)) }
        assertEquals(InformationRequestEvidenceCollectionState.REMOVED, fixture.artifactState())
    }

    @Test
    fun `a file whose provider no longer acts for any party can be withdrawn by a party that remains`() {
        val fixture = fixture()
        fixture.revokeProvider()

        QuarkusTransaction.requiringNew()
            .call { collection.withdraw(fixture.stateCommand(fixture.coParty, "co-party-withdraws-left-file")) }

        assertEquals(InformationRequestEvidenceCollectionState.WITHDRAWN, fixture.artifactState())
    }

    @Test
    fun `the evidence list says which files the caller may change`() {
        val fixture = fixture()

        val changeable = { access: RequestAccessContext ->
            QuarkusTransaction.requiringNew().call {
                queries.list(fixture.requestId, fixture.runtime.documentRequirementId, access)
                    .artifacts
                    .single { it.artifact.id == fixture.runtime.artifactId }
                    .changeableByCaller
            }
        }

        assertEquals(true, changeable(fixture.provider))
        assertEquals(false, changeable(fixture.coParty))
    }

    private fun fixture(): OwnershipFixture {
        val fixture = dataSource.connection.use { connection ->
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
            val providerShareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                providerShareId,
                runtimeFixture.requestId,
                runtimeFixture.contributorUserId,
            )
            execute(
                connection,
                "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                providerShareId,
                runtimeFixture.contributorPartyId,
            )
            val coPartyUserId = UUID.randomUUID()
            val managerUserId = UUID.randomUUID()
            runtimeFixture.insertUser(coPartyUserId)
            runtimeFixture.insertUser(managerUserId)
            OwnershipFixture(runtimeFixture, coPartyUserId, managerUserId)
        }
        fixture.assign(fixture.coPartyUserId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-co-party")
        fixture.assign(fixture.managerUserId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-manager")
        return fixture
    }

    private inner class OwnershipFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val coPartyUserId: UUID,
        val managerUserId: UUID,
    ) {
        val requestId: UUID = runtime.requestId
        val owner = access(runtime.template.userId)
        val provider = access(runtime.contributorUserId)
        val coParty = access(coPartyUserId)
        val manager = access(managerUserId)

        fun assign(userId: UUID, role: InformationRequestShareRoleKey, key: String) {
            QuarkusTransaction.requiringNew().call {
                partyAssignments.assign(
                    AssignInformationRequestPartyCommand(
                        requestId = requestId,
                        roleKey = role,
                        principal = PrincipalRef.user(userId),
                        access = owner,
                        precondition = partiesPrecondition(),
                        idempotencyKey = key,
                    ),
                )
            }
        }

        fun revokeProvider() {
            QuarkusTransaction.requiringNew().call {
                partyRevocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        requestId,
                        runtime.contributorPartyId,
                        owner,
                        partiesPrecondition(),
                        "revoke-provider",
                    ),
                )
            }
        }

        fun stateCommand(access: RequestAccessContext, key: String, reason: String? = null) =
            ChangeInformationRequestEvidenceStateCommand(
                requestId = requestId,
                requirementId = runtime.documentRequirementId,
                artifactId = runtime.artifactId,
                access = access,
                precondition = QuarkusTransaction.requiringNew().call {
                    CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.artifactOf(requireNotNull(artifacts.findById(runtime.artifactId)))
                    )
                },
                idempotencyKey = key,
                reason = reason,
            )

        fun artifactState(): InformationRequestEvidenceCollectionState =
            QuarkusTransaction.requiringNew()
                .call { requireNotNull(artifacts.findById(runtime.artifactId)).collectionState }

        private fun partiesPrecondition(): CommandPrecondition = QuarkusTransaction.requiringNew().call {
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(requireNotNull(requests.findById(requestId)))
            )
        }

        private fun access(userId: UUID) =
            RequestAccessContext(
                PrincipalRef.user(userId),
                AuthorizationContext(sessionRef = "ownership-session", mfaSatisfied = true),
            )
    }

    private companion object {
        const val REASON = "The file belongs to another response"
    }
}
