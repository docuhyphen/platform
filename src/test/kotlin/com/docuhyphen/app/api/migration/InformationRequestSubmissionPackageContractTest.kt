package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestSubmissionPackageContractTest
{
    @Test
    fun `a package freezes its members and nothing in it can be rewritten or removed`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                val packageId = UUID.randomUUID()
                val documentItemId = UUID.randomUUID()
                val attestationItemId = UUID.randomUUID()
                val attestationId = UUID.randomUUID()
                val linkId = UUID.randomUUID()
                execute(
                    connection,
                    """
                    INSERT INTO information_request_supporting_evidence_link
                        (id, information_request_id, supported_requirement_id, supporting_requirement_id,
                         template_evidence_link_id)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    linkId,
                    fixture.requestId,
                    fixture.attestationRequirementId,
                    fixture.documentRequirementId,
                    fixture.supportingLinkTemplateId,
                )
                fixture.insertAttestation(attestationId, 1)
                fixture.insertPackage(packageId, 1)
                fixture.insertItem(
                    documentItemId,
                    packageId,
                    fixture.documentRequirementId,
                    fixture.documentRevisionId,
                    fixture.documentBindingId,
                    "DOCUMENT",
                    fixture.documentResponseId,
                )
                fixture.insertItem(
                    attestationItemId,
                    packageId,
                    fixture.attestationRequirementId,
                    fixture.attestationRevisionId,
                    fixture.attestationBindingId,
                    "RESPONSE_ATTESTATION",
                )
                fixture.insertEvidenceMember(UUID.randomUUID(), packageId, documentItemId)
                execute(
                    connection,
                    """
                    INSERT INTO information_request_submission_supporting_link
                        (id, package_id, information_request_id, supporting_evidence_link_id, supported_requirement_id,
                         supporting_requirement_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    UUID.randomUUID(),
                    packageId,
                    fixture.requestId,
                    linkId,
                    fixture.attestationRequirementId,
                    fixture.documentRequirementId,
                )
                execute(
                    connection,
                    """
                    INSERT INTO information_request_submission_package_attestation
                        (package_id, attestation_id, information_request_id)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    packageId,
                    attestationId,
                    fixture.requestId,
                )

                listOf(
                    "information_request_submission_package",
                    "information_request_submission_item",
                    "information_request_submission_evidence",
                    "information_request_submission_supporting_link",
                    "information_request_submission_package_attestation",
                    "information_request_submission_attestation",
                ).forEach { table ->
                    refusedBy(connection, "append-only") {
                        execute(connection, "UPDATE $table SET information_request_id = information_request_id")
                    }
                    refusedBy(connection, "append-only") {
                        execute(connection, "DELETE FROM $table")
                    }
                }
                assertEquals(1, queryInt(connection, "SELECT COUNT(*) FROM information_request_submission_evidence"))
            }
        }
    }

    @Test
    fun `packages number from one without a gap and pin the configuration the request is on`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection, staged = true)
                refusedBy(connection, "a submission package numbers from one without a gap") {
                    fixture.insertPackage(UUID.randomUUID(), 2)
                }

                val otherVersionId = UUID.randomUUID()
                fixture.template.insertVersion(otherVersionId, 2)
                refusedBy(connection, "a submission package pins the template version its request is on") {
                    fixture.insertPackage(UUID.randomUUID(), 1, templateVersionId = otherVersionId)
                }

                refusedBy(connection, "a staged package names a stage its template version defines") {
                    fixture.insertPackage(UUID.randomUUID(), 1)
                }
                refusedBy(connection, "a staged package names a stage its template version defines") {
                    fixture.insertPackage(UUID.randomUUID(), 1, stageKey = "unknown-stage")
                }
                val first = UUID.randomUUID()
                fixture.insertPackage(first, 1, stageKey = "record-stage")
                refusedBy(connection, "a submission package numbers from one without a gap") {
                    fixture.insertPackage(UUID.randomUUID(), 1, stageKey = "record-stage")
                }
                refusedBy(connection, "a resubmission follows a package of the same stage") {
                    fixture.insertPackage(UUID.randomUUID(), 2, stageKey = "confirmation-stage", previousPackageId = first)
                }
                fixture.insertPackage(UUID.randomUUID(), 2, stageKey = "record-stage", previousPackageId = first)
                assertEquals(
                    2,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_submission_package WHERE information_request_id = ?",
                        fixture.requestId,
                    ),
                )
            }
        }
    }

    @Test
    fun `an item, an evidence member, and an attestation member each stay inside the package's own request`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                refusedBy(connection, "a whole-package package names no stage") {
                    fixture.insertPackage(UUID.randomUUID(), 1, stageKey = "record-stage")
                }
                val packageId = UUID.randomUUID()
                fixture.insertPackage(packageId, 1)

                refusedBy(connection, "a submission item names a revision of its own requirement") {
                    fixture.insertItem(
                        UUID.randomUUID(),
                        packageId,
                        fixture.documentRequirementId,
                        fixture.attestationRevisionId,
                        fixture.documentBindingId,
                        "DOCUMENT",
                    )
                }

                val documentItemId = UUID.randomUUID()
                val attestationItemId = UUID.randomUUID()
                fixture.insertItem(
                    documentItemId,
                    packageId,
                    fixture.documentRequirementId,
                    fixture.documentRevisionId,
                    fixture.documentBindingId,
                    "DOCUMENT",
                    fixture.documentResponseId,
                )
                refusedBy(connection, "ux_information_request_submission_item_requirement") {
                    fixture.insertItem(
                        UUID.randomUUID(),
                        packageId,
                        fixture.documentRequirementId,
                        fixture.documentRevisionId,
                        fixture.documentBindingId,
                        "DOCUMENT",
                    )
                }
                fixture.insertItem(
                    attestationItemId,
                    packageId,
                    fixture.attestationRequirementId,
                    fixture.attestationRevisionId,
                    fixture.attestationBindingId,
                    "RESPONSE_ATTESTATION",
                )

                refusedBy(connection, "a submitted evidence version answers its item's requirement") {
                    fixture.insertEvidenceMember(UUID.randomUUID(), packageId, attestationItemId)
                }
                fixture.insertEvidenceMember(UUID.randomUUID(), packageId, documentItemId)

                val attestationId = UUID.randomUUID()
                fixture.insertAttestation(attestationId, 1)
                val otherPackageId = UUID.randomUUID()
                fixture.insertPackage(otherPackageId, 2)
                refusedBy(connection, "a package freezes only attestations of requirements it holds") {
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_submission_package_attestation
                            (package_id, attestation_id, information_request_id)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                        otherPackageId,
                        attestationId,
                        fixture.requestId,
                    )
                }
            }
        }
    }

    @Test
    fun `a submission attestation is one party's explicit assent or reasoned refusal of an assertion`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                refusedBy(connection, "ck_information_request_submission_attestation_refusal") {
                    fixture.insertAttestation(UUID.randomUUID(), 1, decision = "REFUSED")
                }
                refusedBy(connection, "ck_information_request_submission_attestation_refusal") {
                    fixture.insertAttestation(UUID.randomUUID(), 1, refusalReason = "Not applicable")
                }
                refusedBy(connection, "ck_information_request_submission_attestation_decision") {
                    fixture.insertAttestation(UUID.randomUUID(), 1, decision = "ABSTAINED")
                }
                refusedBy(connection, "ck_information_request_submission_attestation_expiry") {
                    fixture.insertAttestation(UUID.randomUUID(), 1, expiresAfterAttestation = false)
                }
                refusedBy(connection, "a submission attestation answers an assertion") {
                    fixture.insertAttestation(
                        UUID.randomUUID(),
                        1,
                        requirementId = fixture.documentRequirementId,
                        revisionId = fixture.documentRevisionId,
                    )
                }
                refusedBy(connection, "a submission attestation is made by a party of its own request") {
                    fixture.insertAttestation(UUID.randomUUID(), 1, partyId = UUID.randomUUID())
                }
                refusedBy(connection, "submission attestations number from one without a gap") {
                    fixture.insertAttestation(UUID.randomUUID(), 2)
                }

                fixture.insertAttestation(UUID.randomUUID(), 1, decision = "REFUSED", refusalReason = "The record is incomplete")
                fixture.insertAttestation(UUID.randomUUID(), 2)
                assertEquals(
                    2,
                    queryInt(
                        connection,
                        "SELECT COUNT(*) FROM information_request_submission_attestation WHERE information_request_id = ?",
                        fixture.requestId,
                    ),
                )
            }
        }
    }

    @Test
    fun `a package is withdrawn at most once and a request records the package that satisfied it`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection, staged = true)
                val packageId = UUID.randomUUID()
                fixture.insertPackage(packageId, 1, stageKey = "record-stage")
                val withdraw = {
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_submission_withdrawal
                            (id, package_id, information_request_id, withdrawn_by_principal_kind,
                             withdrawn_by_principal_id, withdrawn_at)
                        VALUES (?, ?, ?, 'USER', ?, ?)
                        """.trimIndent(),
                        UUID.randomUUID(),
                        packageId,
                        fixture.requestId,
                        fixture.contributorUserId,
                        fixture.template.now,
                    )
                }
                withdraw()
                refusedBy(connection, "ux_information_request_submission_withdrawal_package") { withdraw() }
                refusedBy(connection, "append-only") {
                    execute(connection, "DELETE FROM information_request_submission_withdrawal")
                }

                refusedBy(connection, "ck_information_request_satisfaction") {
                    execute(
                        connection,
                        "UPDATE information_request SET satisfied_at = ? WHERE id = ?",
                        fixture.template.now,
                        fixture.requestId,
                    )
                }
                execute(
                    connection,
                    """
                    UPDATE information_request
                    SET satisfied_at = ?, satisfied_by_package_id = ?, state = 'CLOSED', closed_at = ?
                    WHERE id = ?
                    """.trimIndent(),
                    fixture.template.now,
                    packageId,
                    fixture.template.now,
                    fixture.requestId,
                )
                assertEquals(
                    packageId.toString(),
                    queryString(connection, "SELECT satisfied_by_package_id FROM information_request WHERE id = ?", fixture.requestId),
                )
            }
        }
    }

    @Test
    fun `history accepts the submission, withdrawal, successor, and follow-up mutations`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionRuntimeSqlFixture(connection)
                listOf("SUBMIT", "WITHDRAW_SUBMISSION", "CREATE_SUCCESSOR", "SCHEDULE_FOLLOW_UP", "CLOSE")
                    .forEachIndexed { index, mutation ->
                        execute(
                            connection,
                            """
                            INSERT INTO information_request_transition
                                (id, information_request_id, sequence_number, from_state, to_state, mutation,
                                 actor_kind, actor_id, occurred_at)
                            VALUES (?, ?, ?, 'ISSUED', 'ISSUED', ?, 'USER', ?, ?)
                            """.trimIndent(),
                            UUID.randomUUID(),
                            fixture.requestId,
                            index + 1,
                            mutation,
                            fixture.contributorUserId,
                            fixture.template.now,
                        )
                    }
                refusedBy(connection, "ck_information_request_transition_mutation") {
                    execute(
                        connection,
                        """
                        INSERT INTO information_request_transition
                            (id, information_request_id, sequence_number, from_state, to_state, mutation,
                             actor_kind, actor_id, occurred_at)
                        VALUES (?, ?, 99, 'ISSUED', 'ISSUED', 'UNDECLARED_MUTATION', 'USER', ?, ?)
                        """.trimIndent(),
                        UUID.randomUUID(),
                        fixture.requestId,
                        fixture.contributorUserId,
                        fixture.template.now,
                    )
                }
            }
        }
    }
}
