package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

class InformationRequestTemplateReviewPlanContractTest
{
    @Test
    fun `a version states its review ordering and reuse purpose and freezes a default stage when it routes review`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                assertEquals("SEQUENTIAL", versionValue(connection, fixture.versionId, "review_stage_ordering"))
                assertNull(versionValue(connection, fixture.versionId, "fact_reuse_purpose_key"))

                refusedBy(connection, "ck_request_template_version_review_ordering") {
                    setVersion(connection, fixture.versionId, "review_stage_ordering", "ROUND_ROBIN")
                }
                refusedBy(connection, "ck_request_template_version_fact_purpose") {
                    setVersion(connection, fixture.versionId, "fact_reuse_purpose_key", "Not A Key")
                }
                setVersion(connection, fixture.versionId, "fact_reuse_purpose_key", "profile.reuse")

                reviewRoutedDocument(connection, fixture, fixture.sectionId, 1)
                fixture.recordDerivedCapabilities(fixture.versionId)
                fixture.publish(fixture.versionId)

                assertEquals(
                    listOf("review|1|Review|ANY||1|MOST_SEVERE_OUTCOME|false|false|false"),
                    stages(connection, fixture.versionId),
                )
                refusedBy(connection, "immutable except for retirement") {
                    setVersion(connection, fixture.versionId, "review_stage_ordering", "PARALLEL")
                }
                refusedBy(connection, "immutable except for retirement") {
                    setVersion(connection, fixture.versionId, "fact_reuse_purpose_key", "profile.other")
                }
                refusedBy(connection, "is immutable") {
                    insertStage(connection, fixture.versionId, "later", 2)
                }
            }
        }
    }

    @Test
    fun `a review stage states a coherent aggregation`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                refusedBy(connection, "ck_request_template_review_stage_aggregation") {
                    insertStage(connection, fixture.versionId, "first", 1, aggregation = "MAJORITY")
                }
                refusedBy(connection, "ck_request_template_review_stage_quorum") {
                    insertStage(connection, fixture.versionId, "first", 1, aggregation = "QUORUM")
                }
                refusedBy(connection, "ck_request_template_review_stage_quorum") {
                    insertStage(connection, fixture.versionId, "first", 1, aggregation = "ALL", quorum = 1, minimum = 2)
                }
                refusedBy(connection, "ck_request_template_review_stage_quorum") {
                    insertStage(connection, fixture.versionId, "first", 1, aggregation = "QUORUM", quorum = 3, minimum = 2)
                }
                refusedBy(connection, "ck_request_template_review_stage_minimum") {
                    insertStage(connection, fixture.versionId, "first", 1, minimum = 0)
                }
                refusedBy(connection, "ck_request_template_review_stage_tie") {
                    insertStage(connection, fixture.versionId, "first", 1, tie = "REQUIRE_OVERRIDE", override = false)
                }
                refusedBy(connection, "ck_request_template_review_stage_values") {
                    insertStage(connection, fixture.versionId, " ", 1)
                }
                insertStage(connection, fixture.versionId, "first", 1, aggregation = "QUORUM", quorum = 2, minimum = 3)
                refusedBy(connection, "ux_request_template_review_stage_key") {
                    insertStage(connection, fixture.versionId, "first", 2)
                }
                refusedBy(connection, "ux_request_template_review_stage_position") {
                    insertStage(connection, fixture.versionId, "second", 1)
                }
            }
        }
    }

    @Test
    fun `publication refuses stages a version cannot use and reviewed requirements no stage covers`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val unrouted = SubmissionTemplateSqlFixture(connection)
                val plainId = UUID.randomUUID()
                unrouted.insertRequirement(plainId, "plain-record", "DOCUMENT")
                val plainBinding = UUID.randomUUID()
                unrouted.insertBinding(plainBinding, unrouted.versionId, plainId, unrouted.sectionId, 1)
                unrouted.insertEvidencePolicy(UUID.randomUUID(), plainBinding, unrouted.versionId)
                unrouted.recordDerivedCapabilities(unrouted.versionId)
                insertStage(connection, unrouted.versionId, "first", 1)
                refusedBy(connection, "a version that routes no work to a reviewer states no review stage") {
                    unrouted.publish(unrouted.versionId)
                }

                val gapped = SubmissionTemplateSqlFixture(connection)
                reviewRoutedDocument(connection, gapped, gapped.sectionId, 1)
                gapped.recordDerivedCapabilities(gapped.versionId)
                insertStage(connection, gapped.versionId, "first", 1)
                insertStage(connection, gapped.versionId, "third", 3)
                refusedBy(connection, "review stage positions must run from one without a gap") {
                    gapped.publish(gapped.versionId)
                }

                val uncovered = SubmissionTemplateSqlFixture(connection)
                val otherSection = UUID.randomUUID()
                uncovered.insertSectionBeforeStages(otherSection, uncovered.versionId, "other-records", 2)
                reviewRoutedDocument(connection, uncovered, uncovered.sectionId, 1)
                uncovered.recordDerivedCapabilities(uncovered.versionId)
                val stageId = insertStage(connection, uncovered.versionId, "first", 1)
                insertStageSection(connection, uncovered.versionId, stageId, otherSection)
                refusedBy(connection, "every requirement that routes work to a reviewer must be covered by a review stage") {
                    uncovered.publish(uncovered.versionId)
                }
                insertStageSection(connection, uncovered.versionId, stageId, uncovered.sectionId)
                uncovered.publish(uncovered.versionId)
                assertEquals("PUBLISHED", versionValue(connection, uncovered.versionId, "status"))
                assertEquals(
                    setOf(otherSection.toString(), uncovered.sectionId.toString()),
                    queryStrings(
                        connection,
                        "SELECT template_section_id::text FROM information_request_template_review_stage_section WHERE review_stage_id = ?",
                        stageId,
                    ),
                )
            }
        }
    }

    @Test
    fun `a review stage covers only sections of its own version`()
    {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val first = SubmissionTemplateSqlFixture(connection)
                val second = SubmissionTemplateSqlFixture(connection)
                val stageId = insertStage(connection, first.versionId, "first", 1)
                refusedBy(connection, "request_template_review_stage_section_section_fkey") {
                    insertStageSection(connection, first.versionId, stageId, second.sectionId)
                }
                insertStageSection(connection, first.versionId, stageId, first.sectionId)
                refusedBy(connection, "ux_request_template_review_stage_section") {
                    insertStageSection(connection, first.versionId, stageId, first.sectionId)
                }
            }
        }
    }

    private fun reviewRoutedDocument(connection: Connection, fixture: SubmissionTemplateSqlFixture, section: UUID, order: Int)
    {
        val requirementId = UUID.randomUUID()
        val bindingId = UUID.randomUUID()
        fixture.insertRequirement(requirementId, "reviewed-record-$order", "DOCUMENT")
        fixture.insertBinding(bindingId, fixture.versionId, requirementId, section, order)
        fixture.insertEvidencePolicy(UUID.randomUUID(), bindingId, fixture.versionId)
        execute(
            connection,
            "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
            bindingId,
        )
    }

    @Suppress("LongParameterList")
    private fun insertStage(
        connection: Connection,
        version: UUID,
        key: String,
        position: Int,
        aggregation: String = "ANY",
        quorum: Int? = null,
        minimum: Int = 1,
        tie: String = "MOST_SEVERE_OUTCOME",
        override: Boolean = false,
    ): UUID
    {
        val id = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_template_review_stage
                (id, template_version_id, stage_key, position, title, aggregation, quorum_count,
                 minimum_reviewer_count, tie_resolution, override_permitted, excludes_response_parties,
                 excludes_prior_reviewers)
            VALUES (?, ?, ?, ?, 'Review stage', ?, ?, ?, ?, ?, FALSE, FALSE)
            """.trimIndent(),
            id,
            version,
            key,
            position,
            aggregation,
            quorum,
            minimum,
            tie,
            override,
        )
        return id
    }

    private fun insertStageSection(connection: Connection, version: UUID, stageId: UUID, sectionId: UUID)
    {
        execute(
            connection,
            """
            INSERT INTO information_request_template_review_stage_section
                (id, template_version_id, review_stage_id, template_section_id)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            version,
            stageId,
            sectionId,
        )
    }

    private fun stages(connection: Connection, version: UUID): List<String> =
        connection.prepareStatement(
            """
            SELECT stage_key, position, title, aggregation, quorum_count, minimum_reviewer_count, tie_resolution,
                   override_permitted, excludes_response_parties, excludes_prior_reviewers
            FROM information_request_template_review_stage
            WHERE template_version_id = ?
            ORDER BY position
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, version)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next())
                    {
                        add(
                            listOf(
                                rows.getString(1),
                                rows.getInt(2).toString(),
                                rows.getString(3),
                                rows.getString(4),
                                rows.getString(5).orEmpty(),
                                rows.getInt(6).toString(),
                                rows.getString(7),
                                rows.getBoolean(8).toString(),
                                rows.getBoolean(9).toString(),
                                rows.getBoolean(10).toString(),
                            ).joinToString("|"),
                        )
                    }
                }
            }
        }

    private fun versionValue(connection: Connection, version: UUID, column: String): String? =
        queryString(connection, "SELECT $column FROM information_request_template_version WHERE id = ?", version)

    private fun setVersion(connection: Connection, version: UUID, column: String, value: String)
    {
        execute(connection, "UPDATE information_request_template_version SET $column = ? WHERE id = ?", value, version)
    }
}
