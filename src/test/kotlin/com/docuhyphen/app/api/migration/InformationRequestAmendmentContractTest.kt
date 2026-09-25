package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestAmendmentContractTest
{
    @Test
    fun `a runtime requirement advances its effective binding to a later Version with a new revision`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val next = fixture.publishNextVersion()

                refusedBy(connection, "must use the Template Version pinned by its request") {
                    advance(connection, fixture.documentRequirementId, next.versionId, next.documentBindingId)
                }
                repin(connection, fixture.requestId, next.versionId)
                refusedBy(connection, "must use the Template Requirement placed by its binding") {
                    advance(connection, fixture.documentRequirementId, next.versionId, next.attestationBindingId)
                }
                advance(connection, fixture.documentRequirementId, next.versionId, next.documentBindingId)
                val secondRevision = insertRevision(connection, fixture, fixture.documentRequirementId, next.versionId,
                    fixture.documentTemplateRequirementId, next.documentBindingId, 2)
                execute(
                    connection,
                    """
                    UPDATE information_request_requirement_current
                    SET current_revision_id = ?, current_revision_number = 2
                    WHERE information_request_requirement_id = ?
                    """.trimIndent(),
                    secondRevision,
                    fixture.documentRequirementId,
                )

                assertEquals(
                    setOf("1:${fixture.documentBindingId}", "2:${next.documentBindingId}"),
                    queryStrings(
                        connection,
                        """
                        SELECT revision_number || ':' || source_template_binding_id
                        FROM information_request_requirement_revision
                        WHERE information_request_requirement_id = ?
                        """.trimIndent(),
                        fixture.documentRequirementId,
                    ),
                )
                assertEquals(
                    fixture.documentRevisionId.toString(),
                    queryString(
                        connection,
                        "SELECT requirement_revision_id FROM information_request_response WHERE id = ?",
                        fixture.documentResponseId,
                    ),
                )
                refusedBy(connection, "records the effective binding") {
                    insertRevision(connection, fixture, fixture.attestationRequirementId, next.versionId,
                        fixture.attestationTemplateRequirementId, next.attestationBindingId, 2)
                }
                refusedBy(connection, "keeps its identity") {
                    execute(
                        connection,
                        "UPDATE information_request_requirement SET occurrence_path = 'items[0]' WHERE id = ?",
                        fixture.documentRequirementId,
                    )
                }
                refusedBy(connection, "advances only to a later Version") {
                    advance(connection, fixture.documentRequirementId, fixture.template.versionId, fixture.documentBindingId)
                }
                refusedBy(connection, "append-only") {
                    execute(connection, "DELETE FROM information_request_requirement WHERE id = ?", fixture.attestationRequirementId)
                }
            }
        }
    }

    @Test
    fun `one stable Template Requirement has one runtime occurrence per path however its binding advanced`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val next = fixture.publishNextVersion()
                repin(connection, fixture.requestId, next.versionId)

                refusedBy(connection, "ux_information_request_requirement_stable_occurrence") {
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_requirement
                            (id, information_request_id, source_template_version_id, source_template_requirement_id,
                             source_template_binding_id, occurrence_path)
                        VALUES (?, ?, ?, ?, ?, 'root')
                        """.trimIndent(),
                        UUID.randomUUID(),
                        fixture.requestId,
                        next.versionId,
                        fixture.documentTemplateRequirementId,
                        next.documentBindingId,
                    )
                }
            }
        }
    }

    @Test
    fun `an amendment, its changes, and its pending notices are append-only records of their request`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val next = fixture.publishNextVersion()
                val draftVersion = UUID.randomUUID()
                fixture.template.insertVersion(draftVersion, 3)

                refusedBy(connection, "a later published Version of its own Template") {
                    insertAmendment(connection, fixture, UUID.randomUUID(), 1, next.versionId, fixture.template.versionId)
                }
                refusedBy(connection, "a later published Version of its own Template") {
                    insertAmendment(connection, fixture, UUID.randomUUID(), 1, fixture.template.versionId, draftVersion)
                }
                refusedBy(connection, "numbered in sequence") {
                    insertAmendment(connection, fixture, UUID.randomUUID(), 2, fixture.template.versionId, next.versionId)
                }
                val amendmentId = UUID.randomUUID()
                insertAmendment(connection, fixture, amendmentId, 1, fixture.template.versionId, next.versionId)

                insertChange(connection, fixture, amendmentId, fixture.documentTemplateRequirementId, "MEANING_CHANGED",
                    fixture.documentBindingId, next.documentBindingId, reconfirmation = true)
                refusedBy(connection, "ck_information_request_amendment_change_bindings") {
                    insertChange(connection, fixture, amendmentId, fixture.attestationTemplateRequirementId, "ADDED",
                        fixture.attestationBindingId, next.attestationBindingId)
                }
                refusedBy(connection, "ck_information_request_amendment_change_reconfirmation") {
                    insertChange(connection, fixture, amendmentId, fixture.attestationTemplateRequirementId,
                        "PRESENTATION_CHANGED", fixture.attestationBindingId, next.attestationBindingId, reconfirmation = true)
                }

                insertNotice(connection, fixture.requestId, amendmentId, fixture.contributorPartyId)
                refusedBy(connection, "ck_information_request_notice_intent_delivery") {
                    insertNotice(connection, fixture.requestId, amendmentId, fixture.attestorPartyId, "DELIVERED")
                }
                val otherRequest = SubmissionRuntimeSqlFixture(connection)
                refusedBy(connection, "information_request_notice_intent_party_fkey") {
                    insertNotice(connection, fixture.requestId, amendmentId, otherRequest.contributorPartyId)
                }

                execute(
                    connection,
                    "UPDATE information_request_response SET reconfirmation_required_by_amendment_id = ? WHERE id = ?",
                    amendmentId,
                    fixture.documentResponseId,
                )
                refusedBy(connection, "information_request_response_reconfirmation_fkey") {
                    execute(
                        connection,
                        "UPDATE information_request_response SET reconfirmation_required_by_amendment_id = ? WHERE id = ?",
                        UUID.randomUUID(),
                        fixture.documentResponseId,
                    )
                }

                listOf(
                    "UPDATE information_request_amendment SET reason_code = 'changed' WHERE id = ?",
                    "DELETE FROM information_request_amendment_change WHERE amendment_id = ?",
                    "UPDATE information_request_notice_intent SET notice_kind = 'REQUIREMENTS_AMENDED' WHERE amendment_id = ?",
                ).forEach { statement ->
                    refusedBy(connection, "append-only") { execute(connection, statement, amendmentId) }
                }
            }
        }
    }

    private fun repin(connection: Connection, requestId: UUID, versionId: UUID)
    {
        execute(connection, "UPDATE information_request SET template_version_id = ? WHERE id = ?", versionId, requestId)
    }

    private fun advance(connection: Connection, requirementId: UUID, versionId: UUID, bindingId: UUID)
    {
        execute(
            connection,
            """
            UPDATE information_request_requirement
            SET source_template_version_id = ?, source_template_binding_id = ?
            WHERE id = ?
            """.trimIndent(),
            versionId,
            bindingId,
            requirementId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertRevision(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        requirementId: UUID,
        versionId: UUID,
        templateRequirementId: UUID,
        bindingId: UUID,
        number: Int,
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_requirement_revision
                (id, information_request_requirement_id, information_request_id, source_template_version_id,
                 source_template_requirement_id, source_template_binding_id, revision_number, occurrence_path,
                 configuration_hash_sha256)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'root', ?)
            """.trimIndent(),
            id,
            requirementId,
            fixture.requestId,
            versionId,
            templateRequirementId,
            bindingId,
            number,
            "1".repeat(64),
        )
        return id
    }

    @Suppress("LongParameterList")
    private fun insertAmendment(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        id: UUID,
        number: Int,
        fromVersion: UUID,
        toVersion: UUID,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_amendment
                (id, information_request_id, amendment_number, from_template_version_id, to_template_version_id,
                 amended_by_principal_kind, amended_by_principal_id)
            VALUES (?, ?, ?, ?, ?, 'USER', ?)
            """.trimIndent(),
            id,
            fixture.requestId,
            number,
            fromVersion,
            toVersion,
            fixture.template.userId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertChange(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        amendmentId: UUID,
        templateRequirementId: UUID,
        kind: String,
        fromBinding: UUID?,
        toBinding: UUID?,
        reconfirmation: Boolean = false,
    )
    {
        execute(
            connection,
            """
            INSERT INTO information_request_amendment_change
                (id, amendment_id, information_request_id, template_requirement_id, requirement_key, change_kind,
                 from_template_binding_id, to_template_binding_id, reconfirmation_required)
            VALUES (?, ?, ?, ?, 'recorded-item', ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            amendmentId,
            fixture.requestId,
            templateRequirementId,
            kind,
            fromBinding,
            toBinding,
            reconfirmation,
        )
    }

    private fun insertNotice(connection: Connection, requestId: UUID, amendmentId: UUID, partyId: UUID, state: String = "PENDING")
    {
        execute(
            connection,
            """
            INSERT INTO information_request_notice_intent
                (id, information_request_id, amendment_id, party_id, notice_kind, delivery_state)
            VALUES (?, ?, ?, ?, 'REQUIREMENTS_AMENDED', ?)
            """.trimIndent(),
            UUID.randomUUID(),
            requestId,
            amendmentId,
            partyId,
            state,
        )
    }
}
