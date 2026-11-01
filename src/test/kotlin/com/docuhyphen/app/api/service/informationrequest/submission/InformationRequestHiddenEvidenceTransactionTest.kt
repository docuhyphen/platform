package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException
import com.docuhyphen.app.api.service.informationrequest.submission.model.HiddenEvidenceScenario
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHiddenEvidenceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `files of a hidden conditional Document Requirement are not frozen into the submitted package`() {
        val scenario = scenario("RETAIN_SECURELY")

        val packageId = submit(scenario)

        assertEquals("HIDDEN", completenessOf(scenario, packageId))
        assertEquals(0, frozenFiles(scenario, packageId))
        assertEquals("ACTIVE", artifactState(scenario))
    }

    @Test
    fun `a save that hides files under a clear-with-confirmation condition lists them and withdraws them once confirmed`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION")

        val confirmation = assertThrows(InformationRequestHiddenResponseClearRequiredException::class.java) {
            saveAnswer(scenario, "Recorded answer kept", "save-without-confirmation", emptySet())
        }
        assertEquals(listOf(scenario.runtime.documentRequirementId), confirmation.targets.map { it.requirementId })
        assertEquals("ACTIVE", artifactState(scenario))

        saveAnswer(
            scenario,
            "Recorded answer kept",
            "save-with-confirmation",
            setOf(scenario.runtime.documentRequirementId)
        )

        assertEquals("WITHDRAWN", artifactState(scenario))
    }

    @Test
    fun `files uploaded to a Document Requirement nobody answered are still listed before a hiding save withdraws them`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION")
        markDocumentUnanswered(scenario)

        val confirmation = assertThrows(InformationRequestHiddenResponseClearRequiredException::class.java) {
            saveAnswer(scenario, "Recorded answer kept", "save-without-confirmation", emptySet())
        }
        assertEquals(listOf(scenario.runtime.documentRequirementId), confirmation.targets.map { it.requirementId })
        assertEquals("ACTIVE", artifactState(scenario))

        saveAnswer(
            scenario,
            "Recorded answer kept",
            "save-with-confirmation",
            setOf(scenario.runtime.documentRequirementId)
        )

        assertEquals("WITHDRAWN", artifactState(scenario))
    }

    @Test
    fun `archiving an answered Document keeps its old files out when it becomes applicable again`() {
        val scenario = scenario(ARCHIVE)
        saveAnswer(scenario, NOT_APPLICABLE, "hide-record", emptySet())
        saveAnswer(scenario, APPLIES, "show-record", emptySet())

        assertEquals(0, activeSubmissionMembers(scenario), "An archived file must not return as active evidence")
        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals("archived-by-condition", artifactReason(scenario))
        assertEquals(listOf("PROVIDED|1"), archives(scenario))
        assertEquals(setOf("${scenario.runtime.evidenceVersionId}|1"), archivedFiles(scenario))
        assertEquals(listOf("ARCHIVED", "REACTIVATED"), effects(scenario))
        assertEquals(1, versions(scenario))
    }

    @Test
    fun `archiving a Document with a file but no response keeps the file out when it becomes applicable again`() {
        val scenario = scenario(ARCHIVE)
        markDocumentUnanswered(scenario)
        saveAnswer(scenario, NOT_APPLICABLE, "hide-file-only-record", emptySet())
        saveAnswer(scenario, APPLIES, "show-file-only-record", emptySet())

        assertEquals(
            0,
            activeSubmissionMembers(scenario),
            "Archiving covers files even without a completed response envelope"
        )
        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals(listOf("NOT_ANSWERED|1"), archives(scenario))
        assertEquals(setOf("${scenario.runtime.evidenceVersionId}|1"), archivedFiles(scenario))
        assertEquals(listOf("ARCHIVED", "REACTIVATED"), effects(scenario))
    }

    @Test
    fun `archiving a Document that holds only a file and no response record records the file as the archived answer`() {
        val scenario = scenario(ARCHIVE)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM information_request_response WHERE id = ?",
                scenario.runtime.documentResponseId
            )
        }

        saveAnswer(scenario, NOT_APPLICABLE, "hide-unrecorded-file", emptySet())

        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals(listOf("NOT_ANSWERED|1"), archives(scenario))
        assertEquals(setOf("${scenario.runtime.evidenceVersionId}|1"), archivedFiles(scenario))
        assertEquals(listOf("ARCHIVED"), effects(scenario))
    }

    @Test
    fun `retaining a Document returns the original file when it becomes applicable again`() {
        val scenario = scenario("RETAIN_SECURELY")
        saveAnswer(scenario, NOT_APPLICABLE, "hide-retained-record", emptySet())
        saveAnswer(scenario, APPLIES, "show-retained-record", emptySet())

        assertEquals(1, activeSubmissionMembers(scenario))
        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(emptyList<String>(), archives(scenario))
    }

    @Test
    fun `confirmed clearing keeps the Document's old file out when it becomes applicable again`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION")
        saveAnswer(scenario, NOT_APPLICABLE, "hide-cleared-record", setOf(scenario.runtime.documentRequirementId))
        saveAnswer(scenario, APPLIES, "show-cleared-record", emptySet())

        assertEquals(0, activeSubmissionMembers(scenario))
        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals("hidden-by-condition", artifactReason(scenario))
        assertEquals(emptyList<String>(), archives(scenario))
    }

    @Test
    fun `an archived file cannot complete a later submission without being provided again`() {
        val scenario = scenario(ARCHIVE)
        saveAnswer(scenario, NOT_APPLICABLE, "hide-archived-submission", emptySet())
        saveAnswer(scenario, APPLIES, "show-archived-submission", emptySet())

        assertThrows(InformationRequestSubmissionIncompleteException::class.java) { submit(scenario) }
    }

    @Test
    fun `a retained file can complete a later submission and is frozen into it`() {
        val scenario = scenario("RETAIN_SECURELY")
        saveAnswer(scenario, NOT_APPLICABLE, "hide-retained-submission", emptySet())
        saveAnswer(scenario, APPLIES, "show-retained-submission", emptySet())

        val packageId = submit(scenario)

        assertEquals("COMPLETE", completenessOf(scenario, packageId))
        assertEquals(1, frozenFiles(scenario, packageId))
    }

    @Test
    fun `a cleared file cannot complete a later submission without being provided again`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION")
        saveAnswer(scenario, NOT_APPLICABLE, "hide-cleared-submission", setOf(scenario.runtime.documentRequirementId))
        saveAnswer(scenario, APPLIES, "show-cleared-submission", emptySet())

        assertThrows(InformationRequestSubmissionIncompleteException::class.java) { submit(scenario) }
    }

    @Test
    fun `archiving a file after its submission was withdrawn leaves the earlier package's frozen file in place`() {
        val scenario = scenario(ARCHIVE, reviewed = true)
        saveAnswer(scenario, APPLIES, "show-before-submission", emptySet())
        val packageId = submit(scenario)
        withdraw(scenario, packageId)

        saveAnswer(scenario, NOT_APPLICABLE, "hide-after-withdrawal", emptySet())

        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals(1, frozenFiles(scenario, packageId))
        assertEquals(setOf(scenario.runtime.evidenceVersionId.toString()), frozenVersions(scenario, packageId))
        assertEquals(setOf("${scenario.runtime.evidenceVersionId}|1"), archivedFiles(scenario))
    }

    @Test
    fun `a Document given once is kept hidden instead of archived and the save goes through`() {
        val scenario = scenario(ARCHIVE, documentResponseMode = "PROVIDE_ONCE", prepare = ::givenOnceAndWithdrawn)

        saveAnswer(scenario, NOT_APPLICABLE, "hide-given-once", emptySet())

        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(emptyList<String>(), archives(scenario))
        assertEquals(listOf("HIDDEN"), effects(scenario))
        assertEquals("RETAIN_SECURELY|false|PROVIDED", documentResponse(scenario))
        assertEquals(NOT_APPLICABLE, controllingAnswer(scenario))
    }

    @Test
    fun `archiving keeps another party's Document and its files hidden and lets the governing answer save`() {
        val scenario = scenario(ARCHIVE, documentContributorRole = "PREPARER")

        saveAnswer(scenario, NOT_APPLICABLE, "hide-other-party-file", emptySet())

        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(emptyList<String>(), archives(scenario))
        assertEquals(listOf("HIDDEN"), effects(scenario))
        assertEquals("RETAIN_SECURELY|false|PROVIDED", documentResponse(scenario))
        assertEquals(NOT_APPLICABLE, controllingAnswer(scenario))
    }

    @Test
    fun `clearing keeps another party's Document hidden without a confirmation and returns it when its condition holds again`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION", documentContributorRole = "PREPARER")

        saveAnswer(scenario, NOT_APPLICABLE, "hide-other-party-record", emptySet())
        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals("RETAIN_SECURELY|false|PROVIDED", documentResponse(scenario))

        saveAnswer(scenario, APPLIES, "show-other-party-record", emptySet())

        assertEquals("|true|PROVIDED", documentResponse(scenario))
        assertEquals(1, activeSubmissionMembers(scenario))
        assertEquals(listOf("HIDDEN", "REACTIVATED"), effects(scenario))
    }

    @Test
    fun `confirming the clear of another party's Document is refused like an unknown Requirement and changes nothing`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION", documentContributorRole = "PREPARER")

        val refusal = assertThrows(ForbiddenException::class.java) {
            saveAnswer(
                scenario,
                NOT_APPLICABLE,
                "confirm-other-party-clear",
                setOf(scenario.runtime.documentRequirementId)
            )
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            saveAnswer(scenario, NOT_APPLICABLE, "confirm-unknown-clear", setOf(UUID.randomUUID()))
        }

        assertEquals(refusal.message, unknown.message)
        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals("|true|PROVIDED", documentResponse(scenario))
        assertEquals(SEEDED_ANSWER, controllingAnswer(scenario))
    }

    @Test
    fun `a Document of a submitted stage stays as submitted when a later stage's answer hides it`() {
        val scenario = scenario(ARCHIVE, staged = true)
        saveAnswer(scenario, APPLIES, "show-staged-record", emptySet())
        submitStage(scenario, RECORD_STAGE)

        saveAnswer(scenario, NOT_APPLICABLE, "hide-submitted-record", emptySet())

        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(emptyList<String>(), archives(scenario))
        assertEquals(listOf("HIDDEN"), effects(scenario))
        assertEquals("RETAIN_SECURELY|false|PROVIDED", documentResponse(scenario))
        assertEquals(NOT_APPLICABLE, controllingAnswer(scenario))
    }

    @Test
    fun `withdrawing a hidden Document's file records an evidence transition naming the condition`() {
        val scenario = scenario("CLEAR_WITH_CONFIRMATION")

        saveAnswer(scenario, NOT_APPLICABLE, "withdraw-hidden-record", setOf(scenario.runtime.documentRequirementId))

        assertEquals("WITHDRAWN", artifactState(scenario))
        assertEquals(listOf("hidden-by-condition"), evidenceTransitions(scenario))
    }

    private fun activeSubmissionMembers(scenario: HiddenEvidenceScenario): Int =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(scenario.runtime.requestId))
            runtime.contentCollector.collect(request, null).items
                .single { it.requirement.id == scenario.runtime.documentRequirementId }
                .evidence?.members.orEmpty().size
        }

    @Suppress("LongParameterList")
    private fun scenario(
        hiddenDataPolicy: String,
        documentResponseMode: String = "PROVIDE",
        documentContributorRole: String = "CONTRIBUTOR",
        reviewed: Boolean = false,
        staged: Boolean = false,
        prepare: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> },
    ): HiddenEvidenceScenario {
        lateinit var answers: FieldAnswerSqlFixture
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(
                connection,
                staged = staged,
                reviewed = reviewed,
                beforePublish = { configured ->
                    answers = FieldAnswerSqlFixture(connection, configured.template)
                    if (staged) {
                        execute(
                            connection,
                            "UPDATE information_request_template_requirement_binding SET template_section_id = ? WHERE id = ?",
                            configured.confirmationSectionId,
                            answers.bindingId,
                        )
                    }
                    val ruleId = UUID.randomUUID()
                    execute(
                        connection,
                        """
                    INSERT INTO information_request_template_condition_rule
                        (id, template_version_id, rule_key, expression_version, hidden_data_policy)
                    VALUES (?, ?, 'when-record-applies', 1, ?)
                    """.trimIndent(),
                        ruleId,
                        configured.template.versionId,
                        hiddenDataPolicy,
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
                    SET requiredness = 'CONDITIONAL', conditional_rule_key = 'when-record-applies',
                        response_mode = ?, contributor_role = ?
                    WHERE id = ?
                    """.trimIndent(),
                        documentResponseMode,
                        documentContributorRole,
                        configured.documentBindingId,
                    )
                }).also { configured ->
                answers.materialize(configured)
                execute(
                    connection,
                    "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?",
                    answers.schemaFieldBindingId
                )
                grantRespondentShares(connection, configured)
                prepare(connection, configured)
            }
        }
        return HiddenEvidenceScenario(
            fixture,
            answers,
            runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        )
    }

    private fun saveAnswer(scenario: HiddenEvidenceScenario, value: String, key: String, confirmed: Set<UUID>) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.runtime.requestId,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.runtime.requestId))),
                    ),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = scenario.answers.requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(
                                    FieldValueEntry(
                                        scenario.answers.fieldContractId,
                                        JsonPrimitive(value)
                                    )
                                ),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                    confirmedHiddenResponseClears = confirmed,
                ),
            )
        }

    private fun submit(scenario: HiddenEvidenceScenario): UUID {
        val attested = QuarkusTransaction.requiringNew().call {
            scenario.services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = scenario.runtime.requestId,
                    requirementId = scenario.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(scenario.runtime.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(scenario)),
                    idempotencyKey = "hidden-evidence-assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            scenario.services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.runtime.requestId,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "hidden-evidence-submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    private fun submitStage(scenario: HiddenEvidenceScenario, stageKey: String): UUID =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(scenario.runtime.requestId))
            scenario.services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.runtime.requestId,
                    stageKey = stageKey,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.submissionOf(
                            stageKey,
                            runtime.contentCollector.collect(request, stageKey).contentHash
                        ),
                    ),
                    idempotencyKey = "hidden-evidence-stage-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id

    private fun withdraw(scenario: HiddenEvidenceScenario, packageId: UUID) {
        QuarkusTransaction.requiringNew().run {
            scenario.services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = scenario.runtime.requestId,
                    packageId = packageId,
                    reasonCode = "record-revisited",
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.runtime.requestId))),
                    ),
                    idempotencyKey = "hidden-evidence-withdraw-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun givenOnceAndWithdrawn(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
        val packageId = UUID.randomUUID()
        fixture.insertPackage(packageId, 1)
        fixture.insertItem(
            UUID.randomUUID(),
            packageId,
            fixture.documentRequirementId,
            fixture.documentRevisionId,
            fixture.documentBindingId,
            "DOCUMENT",
            fixture.documentResponseId,
        )
        execute(
            connection,
            """
            INSERT INTO information_request_submission_withdrawal
                (id, package_id, information_request_id, reason_code, withdrawn_by_principal_kind, withdrawn_by_principal_id, withdrawn_at)
            VALUES (?, ?, ?, 'record-revisited', 'USER', ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            packageId,
            fixture.requestId,
            fixture.contributorUserId,
        )
    }

    private fun markDocumentUnanswered(scenario: HiddenEvidenceScenario) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request_response SET disposition = 'NOT_ANSWERED' WHERE information_request_requirement_id = ?",
                scenario.runtime.documentRequirementId,
            )
        }
    }

    private fun submissionETag(scenario: HiddenEvidenceScenario): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(scenario.runtime.requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun completenessOf(scenario: HiddenEvidenceScenario, packageId: UUID): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT completeness_state FROM information_request_submission_item WHERE package_id = ? AND information_request_requirement_id = ?",
                packageId,
                scenario.runtime.documentRequirementId,
            )
        }

    private fun frozenFiles(scenario: HiddenEvidenceScenario, packageId: UUID): Int =
        dataSource.connection.use {
            queryInt(
                it,
                """
                SELECT COUNT(*)
                FROM information_request_submission_evidence evidence
                JOIN information_request_submission_item item ON item.id = evidence.item_id
                WHERE evidence.package_id = ? AND item.information_request_requirement_id = ?
                """.trimIndent(),
                packageId,
                scenario.runtime.documentRequirementId,
            )
        }

    private fun frozenVersions(scenario: HiddenEvidenceScenario, packageId: UUID): Set<String> =
        dataSource.connection.use {
            queryStrings(
                it,
                """
                SELECT evidence.evidence_version_id::text
                FROM information_request_submission_evidence evidence
                JOIN information_request_submission_item item ON item.id = evidence.item_id
                WHERE evidence.package_id = ? AND item.information_request_requirement_id = ?
                """.trimIndent(),
                packageId,
                scenario.runtime.documentRequirementId,
            )
        }

    private fun artifactState(scenario: HiddenEvidenceScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT collection_state FROM information_request_evidence_artifact WHERE id = ?",
                scenario.runtime.artifactId
            )
        }

    private fun artifactReason(scenario: HiddenEvidenceScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT state_reason FROM information_request_evidence_artifact WHERE id = ?",
                scenario.runtime.artifactId
            )
        }

    private fun versions(scenario: HiddenEvidenceScenario): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_evidence_version WHERE evidence_artifact_id = ?",
                scenario.runtime.artifactId
            )
        }

    private fun archives(scenario: HiddenEvidenceScenario): List<String> =
        rows(
            """
            SELECT disposition || '|' || evidence_count
            FROM information_request_response_archive
            WHERE information_request_requirement_id = ?
            ORDER BY archived_at
            """.trimIndent(),
            scenario.runtime.documentRequirementId,
        )

    private fun archivedFiles(scenario: HiddenEvidenceScenario): Set<String> =
        dataSource.connection.use {
            queryStrings(
                it,
                """
                SELECT member.evidence_version_id::text || '|' || member.evidence_version_number
                FROM information_request_response_archive_evidence member
                JOIN information_request_response_archive archive ON archive.id = member.archive_id
                WHERE archive.information_request_requirement_id = ? AND member.evidence_artifact_id = ?
                """.trimIndent(),
                scenario.runtime.documentRequirementId,
                scenario.runtime.artifactId,
            )
        }

    private fun effects(scenario: HiddenEvidenceScenario): List<String> =
        rows(
            """
            SELECT effect.effect
            FROM information_request_response_condition_effect effect
            JOIN information_request_transition transition ON transition.id = effect.transition_id
            WHERE effect.information_request_requirement_id = ?
            ORDER BY transition.sequence_number
            """.trimIndent(),
            scenario.runtime.documentRequirementId,
        )

    private fun documentResponse(scenario: HiddenEvidenceScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                """
                SELECT COALESCE(hidden_data_policy, '') || '|' || active_in_response || '|' || disposition
                FROM information_request_response
                WHERE information_request_requirement_id = ?
                """.trimIndent(),
                scenario.runtime.documentRequirementId,
            )
        }

    private fun evidenceTransitions(scenario: HiddenEvidenceScenario): List<String> =
        rows(
            """
            SELECT reason_code
            FROM information_request_transition
            WHERE information_request_id = ? AND mutation = 'ADMINISTER_EVIDENCE'
            ORDER BY sequence_number
            """.trimIndent(),
            scenario.runtime.requestId,
        )

    private fun controllingAnswer(scenario: HiddenEvidenceScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT text_value FROM field_value WHERE id = ?",
                scenario.answers.fieldValueId,
            )
        }

    private fun rows(sql: String, vararg values: Any?): List<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }

    private fun grantRespondentShares(connection: Connection, fixture: SubmissionRuntimeSqlFixture) {
        listOf(
            Triple(fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR"),
            Triple(fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR"),
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
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
    }

    private fun access(userId: UUID) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "hidden-evidence-session", mfaSatisfied = true)
        )

    private companion object {
        const val ARCHIVE = "ARCHIVE_OUTSIDE_ACTIVE_RESPONSE"
        const val APPLIES = "applies"
        const val NOT_APPLICABLE = "not applicable"
        const val SEEDED_ANSWER = "Recorded answer"
        const val RECORD_STAGE = "record-stage"
    }
}
