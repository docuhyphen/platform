package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.*
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.condition.model.HiddenTargetScenario
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.noauth.NoAuthSessionSqlScenario
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHiddenTargetTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @Inject
    lateinit var readAccess: InformationRequestNoAuthReadAccessService

    @Inject
    lateinit var responses: InformationRequestResponseDraftService

    @Inject
    lateinit var uploads: InformationRequestEvidenceUploadService

    @Inject
    lateinit var evidence: InformationRequestEvidenceQueryService

    enum class Surface {
        SIGNED_IN,
        ACCESS_LINK,
    }

    @ParameterizedTest
    @EnumSource(Surface::class)
    fun `a patch to a Document its condition hides is refused naming it and saves nothing`(surface: Surface) {
        val scenario = scenario(surface)

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            QuarkusTransaction.requiringNew().call {
                responses.patch(
                    PatchInformationRequestResponsesCommand(
                        requestId = scenario.runtime.requestId,
                        access = scenario.access,
                        precondition = CommandPrecondition.Unconditioned,
                        idempotencyKey = key(),
                        patches = listOf(
                            InformationRequestResponsePatch(
                                scenario.runtime.documentRequirementId,
                                disposition = InformationRequestResponseDisposition.PROVIDED,
                                narrative = ResponseNarrativePatch.Set("Written while hidden"),
                            ),
                        ),
                    ),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION, refusal.reasonCode)
        assertEquals(scenario.runtime.documentRequirementId, refusal.problems.single().requirementId)
        assertEquals(0, responsesWithNarrative(scenario, "Written while hidden"))
    }

    @ParameterizedTest
    @EnumSource(Surface::class)
    fun `an upload to a Document its condition hides is refused and stores no file`(surface: Surface) {
        val scenario = scenario(surface)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                uploads.upload(
                    UploadInformationRequestEvidenceCommand(
                        requestId = scenario.runtime.requestId,
                        requirementId = scenario.runtime.documentRequirementId,
                        access = scenario.access,
                        surface = scenario.evidenceSurface,
                        precondition = CommandPrecondition.Unconditioned,
                        idempotencyKey = key(),
                        file = evidenceFile(),
                        attributes = InformationRequestEvidenceAttributes(),
                    ),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION, refusal.reasonCode)
        assertEquals(1, artifactCount(scenario))
    }

    @ParameterizedTest
    @EnumSource(Surface::class)
    fun `the evidence list of a Document its condition hides shows none of its files`(surface: Surface) {
        val scenario = scenario(surface)

        val listed = QuarkusTransaction.requiringNew().call {
            evidence.list(scenario.runtime.requestId, scenario.runtime.documentRequirementId, scenario.access)
        }

        assertEquals(emptyList<UUID>(), listed.artifacts.map { it.artifact.id })
    }

    @ParameterizedTest
    @EnumSource(Surface::class)
    fun `a file of a Document its condition hides cannot be read or opened by its id`(surface: Surface) {
        val scenario = scenario(surface)
        val runtime = scenario.runtime

        val detail = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                evidence.artifact(runtime.requestId, runtime.documentRequirementId, runtime.artifactId, scenario.access)
            }
        }
        val content = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                evidence.openContent(
                    runtime.requestId,
                    runtime.documentRequirementId,
                    runtime.artifactId,
                    runtime.evidenceVersionId,
                    scenario.access,
                    InformationRequestEvidenceContentUse.DOWNLOAD,
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, detail.reasonCode)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, content.reasonCode)
    }

    @ParameterizedTest
    @EnumSource(Surface::class)
    fun `the evidence list of a Document shows its file once its condition holds`(surface: Surface) {
        val scenario = scenario(surface)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE field_value SET text_value = 'applies' WHERE id = ?",
                scenario.answers.fieldValueId
            )
        }

        val listed = QuarkusTransaction.requiringNew().call {
            evidence.list(scenario.runtime.requestId, scenario.runtime.documentRequirementId, scenario.access)
        }

        assertEquals(listOf(scenario.runtime.artifactId), listed.artifacts.map { it.artifact.id })
        val detail = QuarkusTransaction.requiringNew().call {
            evidence.artifact(
                scenario.runtime.requestId,
                scenario.runtime.documentRequirementId,
                scenario.runtime.artifactId,
                scenario.access,
            )
        }
        assertEquals(scenario.runtime.artifactId, detail.artifact.id)
    }

    private fun scenario(surface: Surface): HiddenTargetScenario {
        lateinit var answers: FieldAnswerSqlFixture
        val beforePublish: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { connection, configured ->
            answers = hideDocumentBehindCondition(connection, configured)
        }
        val prepare: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { connection, configured ->
            answers.materialize(configured)
            execute(
                connection,
                "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?",
                answers.schemaFieldBindingId
            )
        }
        return when (surface) {
            Surface.SIGNED_IN -> {
                val runtime = dataSource.connection.use { connection ->
                    SubmissionRuntimeSqlFixture(
                        connection,
                        beforePublish = { beforePublish(connection, it) }).also { configured ->
                        prepare(connection, configured)
                        grantContributorShare(connection, configured)
                    }
                }
                HiddenTargetScenario(
                    runtime,
                    answers,
                    RequestAccessContext(
                        PrincipalRef.user(runtime.contributorUserId),
                        AuthorizationContext(sessionRef = "hidden-target-session", mfaSatisfied = true),
                    ),
                    InformationRequestEvidenceSurface.AUTHENTICATED,
                )
            }

            Surface.ACCESS_LINK -> {
                val linked = NoAuthSessionSqlScenario(dataSource, links, sessions, beforePublish, prepare)
                val issued = QuarkusTransaction.requiringNew().call { linked.issueSession() }
                HiddenTargetScenario(
                    linked.runtime,
                    answers,
                    QuarkusTransaction.requiringNew().call {
                        readAccess.resolve(linked.requestId, linked.rawToken, issued.sessionToken)
                    },
                    InformationRequestEvidenceSurface.NO_AUTH,
                )
            }
        }
    }

    private fun hideDocumentBehindCondition(
        connection: Connection,
        configured: SubmissionRuntimeSqlFixture
    ): FieldAnswerSqlFixture {
        val answers = FieldAnswerSqlFixture(connection, configured.template)
        val ruleId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_template_condition_rule
                (id, template_version_id, rule_key, expression_version, hidden_data_policy)
            VALUES (?, ?, 'when-record-applies', 1, 'RETAIN_SECURELY')
            """.trimIndent(),
            ruleId,
            configured.template.versionId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_template_condition_predicate
                (id, condition_rule_id, template_version_id, display_order, field_definition_id, value_type, operator, text_value)
            VALUES (?, ?, ?, 0, ?, 'SHORT_TEXT', 'EQUALS', 'applies')
            """.trimIndent(),
            UUID.randomUUID(),
            ruleId,
            configured.template.versionId,
            answers.fieldDefinitionId,
        )
        execute(
            connection,
            """
            UPDATE information_request_template_requirement_binding
            SET requiredness = 'CONDITIONAL', conditional_rule_key = 'when-record-applies'
            WHERE id = ?
            """.trimIndent(),
            configured.documentBindingId,
        )
        return answers
    }

    private fun grantContributorShare(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            fixture.requestId,
            fixture.contributorUserId,
        )
        execute(
            connection,
            "UPDATE information_request_party SET share_id = ? WHERE id = ?",
            shareId,
            fixture.contributorPartyId
        )
    }

    private fun responsesWithNarrative(scenario: HiddenTargetScenario, narrative: String): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_response WHERE information_request_id = ? AND narrative = ?",
                scenario.runtime.requestId,
                narrative,
            )
        }

    private fun artifactCount(scenario: HiddenTargetScenario): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM information_request_evidence_artifact WHERE information_request_requirement_id = ?",
                scenario.runtime.documentRequirementId,
            )
        }

    private fun evidenceFile(): InformationRequestEvidenceFile {
        val file = Files.createTempFile("hidden-target", ".pdf").toFile()
        file.writeBytes("%PDF-1.4 hidden target record".toByteArray())
        file.deleteOnExit()
        return InformationRequestEvidenceFile(file, "record.pdf", "application/pdf", DocumentEncryptionMode.INTERNAL)
    }

    private fun key() = "hidden-target-${UUID.randomUUID()}"
}
