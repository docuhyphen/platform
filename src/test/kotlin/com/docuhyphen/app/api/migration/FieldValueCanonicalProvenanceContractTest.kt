package com.docuhyphen.app.api.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class CanonicalProvenancePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<CanonicalProvenancePostgreSQLContainer>(imageName)

/**
 * Authorship of a stored answer and of a Schema Assignment must name the canonical principal rather
 * than a registered user only. The released schema records authorship as a foreign key into
 * `app_user`, so an external participant, a link-verified recipient, a registered application, and a
 * service principal cannot be named at all, and a write attributed to one of them either names the
 * wrong table or fails that foreign key outright.
 *
 * A stored answer also has no history: the row is overwritten in place. These contract tests prove
 * that the canonical pair is filled from the trustworthy legacy key and then becomes the only
 * authorship a row carries, that authorship nobody recorded stays unrecorded, and that every stored
 * answer becomes addressable through an append-only revision that cannot be rewritten.
 */
class FieldValueCanonicalProvenanceContractTest
{
    private val releasedVersion = "79"

    private val principalKinds = listOf(
        "USER", "PARTICIPANT", "PRINCIPAL_GROUP", "ORGANIZATION", "APPLICATION",
        "SERVICE_ACCOUNT", "PUBLIC_LINK",
    )

    @Test
    fun `canonical authorship is the only authorship a Fields row carries`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertFalse(hasColumn(connection, "field_value", "updated_by_app_user_id"))
                assertFalse(hasColumn(connection, "schema_assignment", "assigned_by_app_user_id"))
                assertFalse(hasColumn(connection, "field_value_revision", "recorded_by_app_user_id"))

                assertTrue(hasColumn(connection, "field_value", "updated_by_principal_id"))
                assertTrue(hasColumn(connection, "schema_assignment", "assigned_by_principal_id"))
                assertTrue(hasColumn(connection, "field_value_revision", "recorded_by_principal_id"))
            }
        }
    }

    @Test
    fun `recorded authorship is carried into the canonical principal columns`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertSetValue(
                    connection, fixture, fixture.firstValueId, fixture.rootSetId,
                    fixture.textContractId, "Recorded answer", appUserId = fixture.appUserId,
                )
                insertSetValue(
                    connection, fixture, fixture.secondValueId, fixture.rootSetId,
                    fixture.secondContractId, "Answer nobody is recorded as leaving", appUserId = null,
                )
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                assertEquals("USER", principalKindOf(connection, fixture.firstValueId))
                assertEquals(fixture.appUserId, principalIdOf(connection, fixture.firstValueId))

                // Authorship nobody recorded must stay unrecorded rather than be invented.
                assertNull(principalKindOf(connection, fixture.secondValueId))
                assertNull(principalIdOf(connection, fixture.secondValueId))

                assertEquals("USER", assignerKindOf(connection, fixture.assignmentId))
                assertEquals(fixture.appUserId, assignerIdOf(connection, fixture.assignmentId))

                assertNull(assignerKindOf(connection, fixture.otherAssignmentId))
                assertNull(assignerIdOf(connection, fixture.otherAssignmentId))
            }
        }
    }

    @Test
    fun `every stored answer becomes addressable through its first recorded revision`()
    {
        withPostgres { postgres ->
            flyway(postgres, target = releasedVersion).migrate()

            val fixture = Fixture()
            val recordedAt = Timestamp.from(Instant.parse("2026-02-03T04:05:06Z"))
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertSetValue(
                    connection, fixture, fixture.firstValueId, fixture.rootSetId,
                    fixture.textContractId, "Recorded answer", appUserId = fixture.appUserId,
                    updatedAt = recordedAt,
                )
                insertSetValue(
                    connection, fixture, fixture.secondValueId, fixture.occurrenceSetId,
                    fixture.selectContractId, null, appUserId = fixture.appUserId,
                    valueType = "MULTI_SELECT",
                )
                insertSelection(connection, fixture.secondValueId, "selected-option", 0)
                insertSelection(connection, fixture.secondValueId, "second-option", 1)
            }

            flyway(postgres).migrate()

            postgres.createConnection("").use { connection ->
                val first = requireNotNull(revisionOf(connection, fixture.firstValueId)) {
                    "Every stored answer must be addressable through a recorded revision"
                }
                assertEquals(1, first.revisionNumber)
                assertEquals(fixture.rootSetId, first.valueSetId)
                assertEquals(fixture.textContractId, first.fieldContractId)
                assertEquals("Recorded answer", first.textValue)
                assertEquals("USER", first.principalKind)
                assertEquals(fixture.appUserId, first.principalId)
                assertEquals(
                    recordedAt, first.recordedAt,
                    "A revision is dated from the change it records, not from the upgrade",
                )
                assertFalse(first.isCleared, "Recording an answer is not the same as clearing it")

                val second = requireNotNull(revisionOf(connection, fixture.secondValueId))
                assertEquals(
                    1, second.revisionNumber,
                    "A repetition numbers its own answers rather than continuing the root set's",
                )
                assertEquals(fixture.occurrenceSetId, second.valueSetId)
                assertEquals(fixture.selectContractId, second.fieldContractId)
                assertEquals(
                    listOf("selected-option", "second-option"),
                    revisionSelectionCodes(connection, second.id),
                    "Chosen options belong to the revision that recorded them",
                )
            }
        }
    }

    @Test
    fun `a clean baseline refuses authorship the canonical model cannot express`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)

                assertAcceptsEveryCanonicalPrincipalKind(connection, fixture)
                assertRefusesAnUnknownPrincipalKind(connection, fixture)
                assertRefusesHalfAPrincipal(connection, fixture)
            }
        }
    }

    @Test
    fun `a recorded revision cannot be renumbered or rewritten`()
    {
        withPostgres { postgres ->
            flyway(postgres).migrate()

            val fixture = Fixture()
            postgres.createConnection("").use { connection ->
                insertFieldsFixture(connection, fixture)
                insertSetValue(
                    connection, fixture, fixture.firstValueId, fixture.rootSetId,
                    fixture.textContractId, "Recorded answer", appUserId = fixture.appUserId,
                )

                val revisionId = insertRevision(connection, fixture, fixture.rootSetId, revisionNumber = 1)

                val duplicated = assertThrows<SQLException> {
                    insertRevision(connection, fixture, fixture.rootSetId, revisionNumber = 1)
                }
                assertTrue(
                    duplicated.message?.contains("ux_field_value_revision_number") == true,
                    "Expected two revisions to be refused the same number: ${duplicated.message}",
                )

                // A repetition answering the same question keeps its own numbering.
                insertRevision(connection, fixture, fixture.occurrenceSetId, revisionNumber = 1)

                val rewritten = assertThrows<SQLException> {
                    connection.prepareStatement(
                        "UPDATE field_value_revision SET text_value = 'Rewritten' WHERE id = ?",
                    ).use { statement ->
                        statement.setObject(1, revisionId)
                        statement.executeUpdate()
                    }
                }
                assertTrue(
                    rewritten.message?.contains("field_value_revision") == true,
                    "Expected a recorded revision to refuse being rewritten: ${rewritten.message}",
                )
            }
        }
    }

    /** Every principal the platform can authenticate must be recordable as an author. */
    private fun assertAcceptsEveryCanonicalPrincipalKind(connection: Connection, fixture: Fixture)
    {
        principalKinds.forEachIndexed { index, kind ->
            val setId = insertValueSet(
                connection, fixture.assignmentId, kind = "OCCURRENCE", occurrencePath = "items[$index]",
            )
            insertSetValue(
                connection, fixture, UUID.randomUUID(), setId, fixture.textContractId,
                "Answer left by a $kind", appUserId = if (kind == "USER") fixture.appUserId else null,
                principalKind = kind,
                principalId = if (kind == "USER") fixture.appUserId else UUID.randomUUID(),
            )
        }
    }

    private fun assertRefusesAnUnknownPrincipalKind(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertSetValue(
                connection, fixture, UUID.randomUUID(), fixture.rootSetId, fixture.textContractId,
                "Answer of an unknown kind", appUserId = null,
                principalKind = "ROBOT", principalId = UUID.randomUUID(),
            )
        }
        assertTrue(
            refused.message?.contains("ck_field_value_principal_kind") == true,
            "Expected an unknown principal kind to be refused: ${refused.message}",
        )
    }

    private fun assertRefusesHalfAPrincipal(connection: Connection, fixture: Fixture)
    {
        val refused = assertThrows<SQLException> {
            insertSetValue(
                connection, fixture, UUID.randomUUID(), fixture.rootSetId, fixture.textContractId,
                "Answer of half a principal", appUserId = null,
                principalKind = "PARTICIPANT", principalId = null,
            )
        }
        assertTrue(
            refused.message?.contains("ck_field_value_principal_pair") == true,
            "Expected a kind with no principal to be refused: ${refused.message}",
        )
    }

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val appUserId: UUID = UUID.randomUUID()
        val otherAppUserId: UUID = UUID.randomUUID()
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val secondDefinitionId: UUID = UUID.randomUUID()
        val selectDefinitionId: UUID = UUID.randomUUID()
        val textContractId: UUID = UUID.randomUUID()
        val secondContractId: UUID = UUID.randomUUID()
        val selectContractId: UUID = UUID.randomUUID()
        val schemaDefinitionId: UUID = UUID.randomUUID()
        val schemaVersionId: UUID = UUID.randomUUID()
        val textBindingId: UUID = UUID.randomUUID()
        val secondBindingId: UUID = UUID.randomUUID()
        val selectBindingId: UUID = UUID.randomUUID()
        val assignmentId: UUID = UUID.randomUUID()
        val resourceId: UUID = UUID.randomUUID()
        val otherAssignmentId: UUID = UUID.randomUUID()
        val otherResourceId: UUID = UUID.randomUUID()
        val firstValueId: UUID = UUID.randomUUID()
        val secondValueId: UUID = UUID.randomUUID()
        lateinit var rootSetId: UUID
        lateinit var occurrenceSetId: UUID

        fun bindingFor(contractId: UUID): UUID = when (contractId)
        {
            textContractId -> textBindingId
            secondContractId -> secondBindingId
            else -> selectBindingId
        }
    }

    private data class RecordedRevision(
        val id: UUID,
        val valueSetId: UUID,
        val fieldContractId: UUID,
        val revisionNumber: Int,
        val textValue: String?,
        val isCleared: Boolean,
        val principalKind: String?,
        val principalId: UUID?,
        val recordedAt: Timestamp,
    )

    private fun hasColumn(connection: Connection, table: String, column: String): Boolean
    {
        connection.prepareStatement(
            "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, column)
            statement.executeQuery().use { rows -> return rows.next() }
        }
    }

    private fun withPostgres(block: (CanonicalProvenancePostgreSQLContainer) -> Unit)
    {
        val postgres = CanonicalProvenancePostgreSQLContainer("postgres:17")
            .withDatabaseName("docuhyphen_canonical_provenance_test")
            .withUsername("docuhyphen")
            .withPassword("docuhyphen")

        postgres.start()
        try
        {
            block(postgres)
        }
        finally
        {
            postgres.stop()
        }
    }

    private fun flyway(postgres: CanonicalProvenancePostgreSQLContainer, target: String? = null): Flyway
    {
        val configuration = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")

        target?.let(configuration::target)
        return configuration.load()
    }

    private fun insertFieldsFixture(connection: Connection, fixture: Fixture)
    {
        val now = Timestamp.from(Instant.now())

        connection.prepareStatement(
            """
            INSERT INTO organization (id, name, registration_number, is_active, verification_complete,
                                      created_date)
            VALUES (?, ?, ?, TRUE, TRUE, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.organizationId)
            statement.setString(2, "Process Owner Org")
            statement.setString(3, "REG-PROCESS-PROVENANCE")
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        insertAppUser(connection, fixture.appUserId, "recorder@process.example")
        insertAppUser(connection, fixture.otherAppUserId, "other-recorder@process.example")

        insertFieldDefinition(connection, fixture.fieldDefinitionId, fixture.organizationId, "recorded-note")
        insertFieldDefinition(connection, fixture.secondDefinitionId, fixture.organizationId, "recorded-second-note")
        insertFieldDefinition(connection, fixture.selectDefinitionId, fixture.organizationId, "recorded-options")
        insertFieldContract(connection, fixture.textContractId, fixture.fieldDefinitionId, "Recorded note")
        insertFieldContract(connection, fixture.secondContractId, fixture.secondDefinitionId, "Second recorded note")
        insertFieldContract(
            connection, fixture.selectContractId, fixture.selectDefinitionId, "Recorded options",
            valueType = "MULTI_SELECT",
        )

        connection.prepareStatement(
            """
            INSERT INTO schema_definition (id, scope_kind, scope_org_id, namespace, schema_key,
                                           display_name, target_resource_type, status, created_at,
                                           updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', 'process-data', 'Process data', 'EXCHANGE',
                    'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.schemaDefinitionId)
            statement.setObject(2, fixture.organizationId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO schema_version (id, schema_definition_id, version_number, status, published_at,
                                        created_at)
            VALUES (?, ?, 1, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.schemaVersionId)
            statement.setObject(2, fixture.schemaDefinitionId)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }

        insertBinding(connection, fixture, fixture.textBindingId, fixture.textContractId, fixture.fieldDefinitionId, 0)
        insertBinding(connection, fixture, fixture.secondBindingId, fixture.secondContractId, fixture.secondDefinitionId, 1)
        insertBinding(connection, fixture, fixture.selectBindingId, fixture.selectContractId, fixture.selectDefinitionId, 2)

        insertAssignment(connection, fixture, fixture.assignmentId, fixture.resourceId, fixture.appUserId)
        insertAssignment(connection, fixture, fixture.otherAssignmentId, fixture.otherResourceId, null)

        fixture.rootSetId = existingRootSet(connection, fixture.assignmentId)
            ?: insertValueSet(connection, fixture.assignmentId, kind = "ROOT")
        fixture.occurrenceSetId = insertValueSet(
            connection, fixture.assignmentId, kind = "OCCURRENCE", occurrencePath = "items[99]",
        )
    }

    private fun insertAppUser(connection: Connection, id: UUID, email: String)
    {
        connection.prepareStatement(
            """
            INSERT INTO app_user (id, email, created_date, is_active, is_temporary,
                                  sign_in_attempts, exchange_version,
                                  multifactor_authentication_type)
            VALUES (?, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setString(2, email)
            statement.setTimestamp(3, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertFieldDefinition(connection: Connection, id: UUID, organizationId: UUID, key: String)
    {
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_definition (id, scope_kind, scope_org_id, namespace, field_key, status,
                                          created_at, updated_at)
            VALUES (?, 'ORGANIZATION', ?, 'process', ?, 'PUBLISHED', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, organizationId)
            statement.setString(3, key)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }

    private fun insertFieldContract(
        connection: Connection,
        id: UUID,
        definitionId: UUID,
        label: String,
        valueType: String = "SHORT_TEXT",
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO field_contract (id, field_definition_id, contract_version, value_type, label,
                                        created_at)
            VALUES (?, ?, 1, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, definitionId)
            statement.setString(3, valueType)
            statement.setString(4, label)
            statement.setTimestamp(5, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun insertBinding(
        connection: Connection,
        fixture: Fixture,
        bindingId: UUID,
        contractId: UUID,
        definitionId: UUID,
        order: Int,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO schema_field_binding (id, schema_version_id, field_contract_id,
                                              field_definition_id, display_order, is_required,
                                              is_read_only, visibility)
            VALUES (?, ?, ?, ?, ?, false, false, 'INTERNAL')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, bindingId)
            statement.setObject(2, fixture.schemaVersionId)
            statement.setObject(3, contractId)
            statement.setObject(4, definitionId)
            statement.setInt(5, order)
            statement.executeUpdate()
        }
    }

    private fun insertAssignment(
        connection: Connection,
        fixture: Fixture,
        assignmentId: UUID,
        resourceId: UUID,
        assignedByAppUserId: UUID?,
    )
    {
        connection.prepareStatement(
            """
            INSERT INTO schema_assignment (id, resource_type, resource_id, schema_version_id,
                                           scope_kind, scope_org_id, assignment_source,
                                           ${assignerColumns(connection)}, assigned_at)
            VALUES (?, 'EXCHANGE', ?, ?, 'ORGANIZATION', ?, 'MANUAL', ${assignerPlaceholders(connection)}, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, assignmentId)
            statement.setObject(2, resourceId)
            statement.setObject(3, fixture.schemaVersionId)
            statement.setObject(4, fixture.organizationId)
            val next = if (hasColumn(connection, "schema_assignment", "assigned_by_app_user_id"))
            {
                statement.setObject(5, assignedByAppUserId)
                6
            }
            else
            {
                statement.setString(5, assignedByAppUserId?.let { "USER" })
                statement.setObject(6, assignedByAppUserId)
                7
            }
            statement.setTimestamp(next, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }

    private fun assignerColumns(connection: Connection): String =
        if (hasColumn(connection, "schema_assignment", "assigned_by_app_user_id")) "assigned_by_app_user_id"
        else "assigned_by_principal_kind, assigned_by_principal_id"

    private fun assignerPlaceholders(connection: Connection): String =
        if (hasColumn(connection, "schema_assignment", "assigned_by_app_user_id")) "?" else "?, ?"

    private fun insertSetValue(
        connection: Connection,
        fixture: Fixture,
        valueId: UUID,
        valueSetId: UUID,
        contractId: UUID,
        text: String?,
        appUserId: UUID?,
        valueType: String = "SHORT_TEXT",
        updatedAt: Timestamp = Timestamp.from(Instant.now()),
        principalKind: String? = null,
        principalId: UUID? = null,
    )
    {
        val bindingId = fixture.bindingFor(contractId)
        val assignmentId = valueSetOwner(connection, valueSetId)
        val resourceId = if (assignmentId == fixture.assignmentId) fixture.resourceId else fixture.otherResourceId
        val legacyShape = hasColumn(connection, "field_value", "updated_by_app_user_id")
        val statedKind = principalKind ?: appUserId?.takeUnless { legacyShape }?.let { "USER" }
        val statedId = principalId ?: appUserId?.takeUnless { legacyShape }
        val legacyColumn = if (legacyShape) ", updated_by_app_user_id" else ""
        val legacyPlaceholder = if (legacyShape) ", ?" else ""
        val canonicalColumns = if (statedKind != null || statedId != null)
            ", updated_by_principal_kind, updated_by_principal_id" else ""
        val canonicalPlaceholders = if (canonicalColumns.isEmpty()) "" else ", ?, ?"

        connection.prepareStatement(
            """
            INSERT INTO field_value (id, field_value_set_id, schema_assignment_id,
                                     schema_field_binding_id, field_contract_id, resource_type,
                                     resource_id, value_type, text_value, provenance, created_at,
                                     updated_at$legacyColumn$canonicalColumns)
            VALUES (?, ?, ?, ?, ?, 'EXCHANGE', ?, ?, ?, 'USER', ?, ?$legacyPlaceholder$canonicalPlaceholders)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, valueId)
            statement.setObject(2, valueSetId)
            statement.setObject(3, assignmentId)
            statement.setObject(4, bindingId)
            statement.setObject(5, contractId)
            statement.setObject(6, resourceId)
            statement.setString(7, valueType)
            statement.setString(8, text)
            statement.setTimestamp(9, updatedAt)
            statement.setTimestamp(10, updatedAt)
            var next = 11
            if (legacyShape)
            {
                statement.setObject(next++, appUserId)
            }
            if (canonicalColumns.isNotEmpty())
            {
                statement.setString(next++, statedKind)
                statement.setObject(next, statedId)
            }
            statement.executeUpdate()
        }
    }

    private fun insertSelection(connection: Connection, valueId: UUID, code: String, order: Int)
    {
        connection.prepareStatement(
            """
            INSERT INTO field_value_selection (id, field_value_id, option_code, display_order)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, valueId)
            statement.setString(3, code)
            statement.setInt(4, order)
            statement.executeUpdate()
        }
    }

    private fun insertRevision(
        connection: Connection,
        fixture: Fixture,
        valueSetId: UUID,
        revisionNumber: Int,
        valueId: UUID = fixture.firstValueId,
    ): UUID
    {
        val id = UUID.randomUUID()
        connection.prepareStatement(
            """
            INSERT INTO field_value_revision (id, field_value_id, field_value_set_id,
                                              schema_assignment_id, schema_field_binding_id,
                                              field_contract_id, revision_number, value_type,
                                              text_value, is_cleared, provenance,
                                              recorded_by_principal_kind, recorded_by_principal_id,
                                              recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'SHORT_TEXT', 'Recorded answer', false, 'USER', 'USER', ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, valueId)
            statement.setObject(3, valueSetId)
            statement.setObject(4, fixture.assignmentId)
            statement.setObject(5, fixture.textBindingId)
            statement.setObject(6, fixture.textContractId)
            statement.setInt(7, revisionNumber)
            statement.setObject(8, fixture.appUserId)
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
        return id
    }

    private fun insertValueSet(
        connection: Connection,
        assignmentId: UUID,
        kind: String,
        occurrencePath: String? = null,
    ): UUID
    {
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        connection.prepareStatement(
            """
            INSERT INTO field_value_set (id, schema_assignment_id, set_kind, occurrence_path,
                                         created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, assignmentId)
            statement.setString(3, kind)
            statement.setString(4, occurrencePath)
            statement.setTimestamp(5, now)
            statement.setTimestamp(6, now)
            statement.executeUpdate()
        }
        return id
    }

    private fun existingRootSet(connection: Connection, assignmentId: UUID): UUID? =
        connection.prepareStatement(
            "SELECT id FROM field_value_set WHERE schema_assignment_id = ? AND set_kind = 'ROOT'",
        ).use { statement ->
            statement.setObject(1, assignmentId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else rows.getObject(1, UUID::class.java)
            }
        }

    private fun valueSetOwner(connection: Connection, valueSetId: UUID): UUID =
        connection.prepareStatement("SELECT schema_assignment_id FROM field_value_set WHERE id = ?")
            .use { statement ->
                statement.setObject(1, valueSetId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getObject(1, UUID::class.java)
                }
            }

    private fun principalKindOf(connection: Connection, valueId: UUID): String? =
        stringColumn(connection, "SELECT updated_by_principal_kind FROM field_value WHERE id = ?", valueId)

    private fun principalIdOf(connection: Connection, valueId: UUID): UUID? =
        uuidColumn(connection, "SELECT updated_by_principal_id FROM field_value WHERE id = ?", valueId)

    private fun assignerKindOf(connection: Connection, assignmentId: UUID): String? =
        stringColumn(
            connection,
            "SELECT assigned_by_principal_kind FROM schema_assignment WHERE id = ?",
            assignmentId,
        )

    private fun assignerIdOf(connection: Connection, assignmentId: UUID): UUID? =
        uuidColumn(connection, "SELECT assigned_by_principal_id FROM schema_assignment WHERE id = ?", assignmentId)

    private fun stringColumn(connection: Connection, sql: String, id: UUID): String? =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "Expected a row for $id")
                rows.getString(1)
            }
        }

    private fun uuidColumn(connection: Connection, sql: String, id: UUID): UUID? =
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows ->
                assertTrue(rows.next(), "Expected a row for $id")
                rows.getObject(1, UUID::class.java)
            }
        }

    private fun revisionOf(connection: Connection, valueId: UUID): RecordedRevision? =
        connection.prepareStatement(
            """
            SELECT id, field_value_set_id, field_contract_id, revision_number, text_value, is_cleared,
                   recorded_by_principal_kind, recorded_by_principal_id, recorded_at
            FROM field_value_revision
            WHERE field_value_id = ?
            ORDER BY revision_number DESC
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, valueId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null
                else RecordedRevision(
                    id = rows.getObject(1, UUID::class.java),
                    valueSetId = rows.getObject(2, UUID::class.java),
                    fieldContractId = rows.getObject(3, UUID::class.java),
                    revisionNumber = rows.getInt(4),
                    textValue = rows.getString(5),
                    isCleared = rows.getBoolean(6),
                    principalKind = rows.getString(7),
                    principalId = rows.getObject(8, UUID::class.java),
                    recordedAt = rows.getTimestamp(9),
                )
            }
        }

    private fun revisionSelectionCodes(connection: Connection, revisionId: UUID): List<String> =
        connection.prepareStatement(
            """
            SELECT option_code FROM field_value_revision_selection
            WHERE field_value_revision_id = ?
            ORDER BY display_order
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, revisionId)
            statement.executeQuery().use { rows ->
                val codes = mutableListOf<String>()
                while (rows.next()) codes += rows.getString(1)
                codes
            }
        }
}
