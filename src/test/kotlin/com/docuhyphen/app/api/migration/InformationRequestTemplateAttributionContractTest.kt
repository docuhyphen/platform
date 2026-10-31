package com.docuhyphen.app.api.migration

import com.docuhyphen.app.api.model.entity.PrincipalKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*

class InformationRequestTemplateAttributionContractTest {
    @Test
    fun `Template creators and publication actors support every canonical principal kind`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                assertCanonicalColumns(connection)
                PrincipalKind.entries.forEach { kind ->
                    val actorId = UUID.randomUUID()
                    execute(
                        connection, """
                        UPDATE information_request_template_definition
                        SET created_by_principal_kind = ?, created_by_principal_id = ? WHERE id = ?
                    """.trimIndent(), kind.name, actorId, fixture.definitionId
                    )
                    execute(
                        connection, """
                        UPDATE information_request_template_version
                        SET created_by_principal_kind = ?, created_by_principal_id = ?,
                            published_by_principal_kind = ?, published_by_principal_id = ?,
                            retired_by_principal_kind = ?, retired_by_principal_id = ? WHERE id = ?
                    """.trimIndent(), kind.name, actorId, kind.name, actorId, kind.name, actorId, fixture.versionId
                    )
                    assertEquals(
                        kind.name,
                        actor(
                            connection,
                            "information_request_template_definition",
                            fixture.definitionId,
                            "created",
                            "kind"
                        )
                    )
                    listOf("created", "published", "retired").forEach { operation ->
                        assertEquals(
                            kind.name,
                            actor(
                                connection,
                                "information_request_template_version",
                                fixture.versionId,
                                operation,
                                "kind"
                            )
                        )
                        assertEquals(
                            actorId.toString(),
                            actor(
                                connection,
                                "information_request_template_version",
                                fixture.versionId,
                                operation,
                                "id"
                            )
                        )
                    }
                }
                listOf("created", "published", "retired").forEach { operation ->
                    refusedBy(connection, "ck_request_template_version_${operation}_principal") {
                        execute(
                            connection,
                            "UPDATE information_request_template_version SET ${operation}_by_principal_kind = NULL WHERE id = ?",
                            fixture.versionId
                        )
                    }
                    refusedBy(connection, "ck_request_template_version_${operation}_principal") {
                        execute(
                            connection,
                            "UPDATE information_request_template_version SET ${operation}_by_principal_id = NULL WHERE id = ?",
                            fixture.versionId
                        )
                    }
                    refusedBy(connection, "ck_request_template_version_${operation}_principal") {
                        execute(
                            connection,
                            "UPDATE information_request_template_version SET ${operation}_by_principal_kind = 'UNRESOLVED' WHERE id = ?",
                            fixture.versionId
                        )
                    }
                }
                refusedBy(connection, "ck_request_template_definition_created_principal") {
                    execute(
                        connection,
                        "UPDATE information_request_template_definition SET created_by_principal_id = NULL WHERE id = ?",
                        fixture.definitionId
                    )
                }
                assertReplacedColumnsRefused(connection, fixture)
            }
        }
    }

    @Test
    fun `upgrading populated V158 Templates preserves recorded actors and frozen configuration`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres, "158").migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                execute(
                    connection,
                    "UPDATE information_request_template_definition SET created_by_app_user_id = ? WHERE id = ?",
                    fixture.userId,
                    fixture.definitionId
                )
                execute(
                    connection,
                    "UPDATE information_request_template_version SET created_by_app_user_id = ? WHERE id = ?",
                    fixture.userId,
                    fixture.versionId
                )
                val requirementId = UUID.randomUUID()
                fixture.insertRequirement(requirementId, "record-confirmation", "RESPONSE_ATTESTATION")
                fixture.insertBinding(UUID.randomUUID(), fixture.versionId, requirementId, fixture.sectionId, 1)
                fixture.recordDerivedCapabilities(fixture.versionId)
                execute(
                    connection, """
                    UPDATE information_request_template_version
                    SET status = 'PUBLISHED', published_at = now(), published_by_app_user_id = ? WHERE id = ?
                """.trimIndent(), fixture.userId, fixture.versionId
                )
                execute(
                    connection, """
                    UPDATE information_request_template_version
                    SET status = 'RETIRED', retired_at = now(), retired_by_app_user_id = ? WHERE id = ?
                """.trimIndent(), fixture.userId, fixture.versionId
                )
                val unknownVersionId = UUID.randomUUID()
                fixture.insertVersion(unknownVersionId, 2)
                val configurationBefore = queryString(
                    connection,
                    "SELECT row_to_json(b)::text FROM information_request_template_requirement_binding b WHERE template_version_id = ?",
                    fixture.versionId
                )

                submissionFlyway(postgres).migrate()

                assertCanonicalColumns(connection)
                assertEquals(
                    "USER",
                    actor(
                        connection,
                        "information_request_template_definition",
                        fixture.definitionId,
                        "created",
                        "kind"
                    )
                )
                assertEquals(
                    fixture.userId.toString(),
                    actor(connection, "information_request_template_definition", fixture.definitionId, "created", "id")
                )
                listOf("created", "published", "retired").forEach { operation ->
                    assertEquals(
                        "USER",
                        actor(connection, "information_request_template_version", fixture.versionId, operation, "kind")
                    )
                    assertEquals(
                        fixture.userId.toString(),
                        actor(connection, "information_request_template_version", fixture.versionId, operation, "id")
                    )
                    assertNull(
                        actor(
                            connection,
                            "information_request_template_version",
                            unknownVersionId,
                            operation,
                            "kind"
                        )
                    )
                    assertNull(
                        actor(
                            connection,
                            "information_request_template_version",
                            unknownVersionId,
                            operation,
                            "id"
                        )
                    )
                }
                assertEquals(
                    configurationBefore, queryString(
                        connection,
                        "SELECT row_to_json(b)::text FROM information_request_template_requirement_binding b WHERE template_version_id = ?",
                        fixture.versionId
                    )
                )
                refusedBy(connection, "immutable") {
                    execute(
                        connection,
                        "UPDATE information_request_template_version SET created_by_principal_id = ? WHERE id = ?",
                        UUID.randomUUID(),
                        fixture.versionId
                    )
                }
                assertReplacedColumnsRefused(connection, fixture)
            }
        }
    }

    @Test
    fun `retirement preserves creator and publisher provenance and records a different canonical actor`() {
        withSubmissionPostgres { postgres ->
            submissionFlyway(postgres).migrate()
            postgres.createConnection("").use { connection ->
                val fixture = SubmissionTemplateSqlFixture(connection)
                assertCanonicalColumns(connection)
                val creator = UUID.randomUUID()
                val publisher = UUID.randomUUID()
                val retirementActor = UUID.randomUUID()
                val requirementId = UUID.randomUUID()
                fixture.insertRequirement(requirementId, "record-confirmation", "RESPONSE_ATTESTATION")
                fixture.insertBinding(UUID.randomUUID(), fixture.versionId, requirementId, fixture.sectionId, 1)
                fixture.recordDerivedCapabilities(fixture.versionId)
                execute(
                    connection, """
                    UPDATE information_request_template_version
                    SET created_by_principal_kind = 'APPLICATION', created_by_principal_id = ?,
                        status = 'PUBLISHED', published_at = now(),
                        published_by_principal_kind = 'SERVICE_ACCOUNT', published_by_principal_id = ? WHERE id = ?
                """.trimIndent(), creator, publisher, fixture.versionId
                )
                listOf("created", "published").forEach { operation ->
                    listOf("kind", "id").forEach { suffix ->
                        refusedBy(connection, "immutable") {
                            val value: Any = if (suffix == "kind") "PARTICIPANT" else UUID.randomUUID()
                            execute(
                                connection, """
                                UPDATE information_request_template_version
                                SET status = 'RETIRED', retired_at = now(), ${operation}_by_principal_$suffix = ? WHERE id = ?
                            """.trimIndent(), value, fixture.versionId
                            )
                        }
                    }
                }
                execute(
                    connection, """
                    UPDATE information_request_template_version
                    SET status = 'RETIRED', retired_at = now(),
                        retired_by_principal_kind = 'PARTICIPANT', retired_by_principal_id = ? WHERE id = ?
                """.trimIndent(), retirementActor, fixture.versionId
                )
                assertEquals(
                    creator.toString(),
                    actor(connection, "information_request_template_version", fixture.versionId, "created", "id")
                )
                assertEquals(
                    publisher.toString(),
                    actor(connection, "information_request_template_version", fixture.versionId, "published", "id")
                )
                assertEquals(
                    "PARTICIPANT",
                    actor(connection, "information_request_template_version", fixture.versionId, "retired", "kind")
                )
                assertEquals(
                    retirementActor.toString(),
                    actor(connection, "information_request_template_version", fixture.versionId, "retired", "id")
                )
            }
        }
    }

    private fun assertCanonicalColumns(connection: Connection) {
        assertEquals(
            8, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
              AND table_name IN ('information_request_template_definition', 'information_request_template_version')
              AND column_name IN ('created_by_principal_kind', 'created_by_principal_id',
                  'published_by_principal_kind', 'published_by_principal_id', 'retired_by_principal_kind', 'retired_by_principal_id')
        """.trimIndent()
            )
        )
        assertEquals(
            0, queryInt(
                connection, """
            SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
              AND table_name IN ('information_request_template_definition', 'information_request_template_version')
              AND column_name IN ('created_by_app_user_id', 'published_by_app_user_id', 'retired_by_app_user_id')
        """.trimIndent()
            )
        )
    }

    private fun assertReplacedColumnsRefused(connection: Connection, fixture: SubmissionTemplateSqlFixture) {
        refusedBy(connection, "created_by_app_user_id") {
            execute(
                connection,
                "UPDATE information_request_template_definition SET created_by_app_user_id = ? WHERE id = ?",
                fixture.userId,
                fixture.definitionId
            )
        }
        listOf("created", "published", "retired").forEach { operation ->
            refusedBy(connection, "${operation}_by_app_user_id") {
                execute(
                    connection,
                    "UPDATE information_request_template_version SET ${operation}_by_app_user_id = ? WHERE id = ?",
                    fixture.userId,
                    fixture.versionId
                )
            }
        }
    }

    private fun actor(connection: Connection, table: String, id: UUID, operation: String, suffix: String): String? =
        queryString(
            connection,
            "SELECT to_jsonb(t) ->> '${operation}_by_principal_$suffix' FROM $table t WHERE id = ?",
            id
        )
}
