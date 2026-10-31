package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.model.NextSubmissionVersion
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.FollowUpPolicyScenario
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestFollowUpPolicyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `a follow-up leaves out a party the sharing policy no longer permits and records it`() {
        val scenario = scenario()
        joinUntrustedOrganization(scenario.fixture.contributorUserId)

        val supplement = QuarkusTransaction.requiringNew()
            .call { scenario.services.successors.create(supplement(scenario, "supplement")) }

        assertEquals(0, partiesOf(supplement.successor.id, scenario.fixture.contributorUserId))
        assertEquals(1, partiesOf(supplement.successor.id, scenario.fixture.attestorUserId))
        assertEquals(1, partiesOf(scenario.sourceId, scenario.fixture.contributorUserId))
        assertEquals(scenario.fixture.contributorPartyId.toString(), notCopiedOf(scenario.sourceId))
    }

    @Test
    fun `a supplement keeps a personal group party that the person who admitted it owns`() {
        val scenario = scenario()
        val groupOwner = scenario.fixture.template.userId
        val groupId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO principal_group (id, name, scope, owner_app_user_id, externally_published, is_active, created_date)
                VALUES (?, 'Process contributors', 'PERSONAL', ?, FALSE, TRUE, now())
                """.trimIndent(),
                groupId,
                groupOwner,
            )
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                     assigned_by_principal_kind, assigned_by_principal_id, assigned_at, created_at, updated_at)
                VALUES (?, ?, 'CONTRIBUTOR', 'PRINCIPAL_GROUP', ?, TRUE, 1, 'USER', ?, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                scenario.sourceId,
                groupId,
                groupOwner,
            )
        }

        val supplement = QuarkusTransaction.requiringNew()
            .call { scenario.services.successors.create(supplement(scenario, "supplement-personal-group")) }

        assertEquals(1, groupPartiesOf(supplement.successor.id, groupId))
    }

    @Test
    fun `a supplement reason that is not a machine code is refused before anything is recorded`() {
        val scenario = scenario()

        val refusal = assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.successors.create(
                    supplement(scenario, "supplement-free-text").copy(reasonCode = "Send the latest statement again"),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.REASON_CODE_INVALID, refusal.reasonCode)
        assertEquals(0, lineagesOf(scenario.sourceId))
    }

    @Test
    fun `a follow-up cannot pin a Version of another Template of the same owner`() {
        val scenario = scenario()
        val otherTemplateVersion = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(
                connection,
                sharedOrganizationId = scenario.fixture.template.organizationId
            ).template.versionId
        }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.successors.create(
                    supplement(
                        scenario,
                        "supplement-other-template",
                        otherTemplateVersion
                    )
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.SUCCESSOR_TARGET_INVALID, refusal.reasonCode)
        assertEquals(0, lineagesOf(scenario.sourceId))
    }

    @Test
    fun `a follow-up may pin a later published Version of its own Template`() {
        val scenario = scenario(publishNext = true)
        val next = requireNotNull(scenario.next)

        val supplement = QuarkusTransaction.requiringNew().call {
            scenario.services.successors.create(supplement(scenario, "supplement-next-version", next.versionId))
        }

        assertEquals(next.versionId, supplement.successor.templateVersionId)
    }

    private fun scenario(publishNext: Boolean = false): FollowUpPolicyScenario {
        val managerId = UUID.randomUUID()
        var next: NextSubmissionVersion? = null
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { fixture ->
                val managerPartyId = UUID.randomUUID()
                fixture.insertActingParty(managerPartyId, "DECISION_MAKER", managerId)
                listOf(
                    Triple(fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR"),
                    Triple(fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR"),
                    Triple(managerPartyId, managerId, "DECISION_MAKER"),
                ).forEach { (partyId, userId, role) ->
                    val shareId = UUID.randomUUID()
                    execute(
                        connection,
                        """
                        INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                        VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                        """.trimIndent(),
                        shareId,
                        fixture.requestId,
                        userId,
                        role,
                    )
                    execute(
                        connection,
                        "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                        shareId,
                        partyId
                    )
                }
                if (publishNext) next = fixture.publishNextVersion()
            }
        }
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        submitWhole(services, fixture)
        return FollowUpPolicyScenario(fixture, services, PrincipalRef.user(managerId), next)
    }

    private fun submitWhole(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture) {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(PrincipalRef.user(fixture.attestorUserId)),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val request = requireNotNull(requestRepository.findById(fixture.requestId))
                            InformationRequestETag.submissionOf(
                                null,
                                runtime.contentCollector.collect(request, null).contentHash
                            )
                        },
                    ),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(PrincipalRef.user(fixture.contributorUserId)),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun supplement(scenario: FollowUpPolicyScenario, key: String, targetTemplateVersionId: UUID? = null) =
        CreateInformationRequestSuccessorCommand(
            sourceRequestId = scenario.sourceId,
            kind = InformationRequestLineageKind.SUPPLEMENT,
            targetTemplateVersionId = targetTemplateVersionId,
            reasonCode = "additional-information",
            access = access(scenario.manager),
            precondition = CommandPrecondition.ExpectedRevision(
                QuarkusTransaction.requiringNew().call {
                    InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(scenario.sourceId)))
                },
            ),
            idempotencyKey = key,
        )

    private fun joinUntrustedOrganization(userId: UUID) {
        dataSource.connection.use { connection ->
            val organizationId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                VALUES (?, 'Unrelated Owner', ?, TRUE, TRUE, now())
                """.trimIndent(),
                organizationId,
                "REG-${organizationId.toString().take(8)}",
            )
            execute(
                connection,
                """
                INSERT INTO organization_membership (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                VALUES (?, ?, ?, 'ACTIVE', TRUE, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                userId,
                organizationId,
            )
        }
    }

    private fun partiesOf(requestId: UUID, userId: UUID): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_party WHERE information_request_id = ? AND principal_kind = 'USER' AND principal_id = ? AND active = TRUE",
            requestId,
            userId,
        )
    }

    private fun groupPartiesOf(requestId: UUID, groupId: UUID): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_party WHERE information_request_id = ? AND principal_kind = 'PRINCIPAL_GROUP' AND principal_id = ? AND active = TRUE",
            requestId,
            groupId,
        )
    }

    private fun notCopiedOf(sourceId: UUID): String? = dataSource.connection.use { connection: Connection ->
        queryString(
            connection,
            """
            SELECT payload_json::jsonb ->> 'partiesNotCopied'
            FROM audit_outbox
            WHERE target_id = ? AND event_type_key = 'information_request.request.successor'
            """.trimIndent(),
            sourceId.toString(),
        )
    }

    private fun lineagesOf(sourceId: UUID): Int = dataSource.connection.use { connection ->
        queryInt(connection, "SELECT count(*) FROM information_request_lineage WHERE source_request_id = ?", sourceId)
    }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "follow-up-session"))
}
