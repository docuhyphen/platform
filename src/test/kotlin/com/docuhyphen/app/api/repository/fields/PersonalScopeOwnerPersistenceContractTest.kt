package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

private class PersonalScopeOwnerPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<PersonalScopeOwnerPostgreSQLContainer>(imageName)

class PersonalScopeOwnerPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = PersonalScopeOwnerPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_personal_owner_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

/**
 * A person owning reusable configuration has to survive the round trip through the mapped entity,
 * not only through a hand-written statement, and the lookup that decides whether a key is already
 * taken has to ask about the same owner that the key belongs to.
 *
 * Looking a key up by scope kind and organization alone answers for every person at once, because
 * they all share the absent organization. Two people would then be told each other's key is taken,
 * and one person's configuration would be handed to another.
 */
@QuarkusTest
@QuarkusTestResource(PersonalScopeOwnerPostgreSQLResource::class)
class PersonalScopeOwnerPersistenceContractTest
{
    @Inject
    lateinit var fieldDefinitionRepository: FieldDefinitionRepository

    @Inject
    lateinit var schemaDefinitionRepository: SchemaDefinitionRepository

    @Inject
    lateinit var schemaAssignmentRepository: SchemaAssignmentRepository

    @Inject
    lateinit var schemaVersionRepository: SchemaVersionRepository

    @Inject
    lateinit var dataSource: DataSource

    @Test
    fun `a personal owner survives the round trip on a field, a schema, and an assignment`()
    {
        val owner = insertOwner("round-trip-owner")
        val namespace = uniqueNamespace()

        val fieldId = QuarkusTransaction.requiringNew().call<UUID> {
            fieldDefinitionRepository.save(
                personalField(owner, namespace, "recorded-note"),
            ).id
        }
        val schema = QuarkusTransaction.requiringNew().call {
            schemaDefinitionRepository.save(personalSchema(owner, namespace, "process-data"))
        }
        val versionId = QuarkusTransaction.requiringNew().call<UUID> {
            schemaVersionRepository.save(
                com.docuhyphen.app.api.model.entity.SchemaVersion().apply {
                    schemaDefinitionId = schema.id
                    versionNumber = 1
                    status = FieldLifecycleStatus.PUBLISHED
                },
            ).id
        }
        val assignmentId = QuarkusTransaction.requiringNew().call<UUID> {
            schemaAssignmentRepository.save(
                SchemaAssignment().apply {
                    resourceType = "EXCHANGE"
                    resourceId = UUID.randomUUID()
                    schemaVersionId = versionId
                    scopeKind = FieldScopeKind.PERSONAL
                    scopeUserId = owner
                },
            ).id
        }

        QuarkusTransaction.requiringNew().run {
            assertEquals(owner, fieldDefinitionRepository.findById(fieldId)?.scopeUserId)
            assertNull(fieldDefinitionRepository.findById(fieldId)?.scopeOrgId)
            assertEquals(owner, schemaDefinitionRepository.findById(schema.id)?.scopeUserId)
            assertEquals(owner, schemaAssignmentRepository.findById(assignmentId)?.scopeUserId)
        }
    }

    @Test
    fun `a key lookup answers for one person, not for everybody without an organization`()
    {
        val owner = insertOwner("first-key-owner")
        val otherOwner = insertOwner("second-key-owner")
        val namespace = uniqueNamespace()

        val ownedField = QuarkusTransaction.requiringNew().call<UUID> {
            fieldDefinitionRepository.save(personalField(owner, namespace, "recorded-note")).id
        }
        val otherOwnedField = QuarkusTransaction.requiringNew().call<UUID> {
            fieldDefinitionRepository.save(personalField(otherOwner, namespace, "recorded-note")).id
        }
        val ownedSchema = QuarkusTransaction.requiringNew().call<UUID> {
            schemaDefinitionRepository.save(personalSchema(owner, namespace, "process-data")).id
        }
        val otherOwnedSchema = QuarkusTransaction.requiringNew().call<UUID> {
            schemaDefinitionRepository.save(personalSchema(otherOwner, namespace, "process-data")).id
        }

        assertNotEquals(ownedField, otherOwnedField, "Both people hold the same key under their own name")

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                ownedField,
                fieldDefinitionRepository.findByKey(
                    FieldScopeKind.PERSONAL, null, owner, namespace, "recorded-note",
                )?.id,
            )
            assertEquals(
                otherOwnedField,
                fieldDefinitionRepository.findByKey(
                    FieldScopeKind.PERSONAL, null, otherOwner, namespace, "recorded-note",
                )?.id,
            )
            assertEquals(
                ownedSchema,
                schemaDefinitionRepository.findByKey(
                    FieldScopeKind.PERSONAL, null, owner, namespace, "process-data",
                )?.id,
            )
            assertEquals(
                otherOwnedSchema,
                schemaDefinitionRepository.findByKey(
                    FieldScopeKind.PERSONAL, null, otherOwner, namespace, "process-data",
                )?.id,
            )

            // A third person has not taken the key merely by having no organization either.
            assertNull(
                fieldDefinitionRepository.findByKey(
                    FieldScopeKind.PERSONAL, null, insertOwner("third-key-owner"), namespace, "recorded-note",
                ),
            )
        }
    }

    @Test
    fun `the platform key space is not a person's key space`()
    {
        val owner = insertOwner("platform-neighbour")
        val namespace = uniqueNamespace()

        QuarkusTransaction.requiringNew().run {
            fieldDefinitionRepository.save(
                FieldDefinition().apply {
                    scopeKind = FieldScopeKind.PLATFORM
                    this.namespace = namespace
                    fieldKey = "recorded-note"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
            fieldDefinitionRepository.save(personalField(owner, namespace, "recorded-note"))
        }

        QuarkusTransaction.requiringNew().run {
            val platformOwned = fieldDefinitionRepository.findByKey(
                FieldScopeKind.PLATFORM, null, null, namespace, "recorded-note",
            )
            val personallyOwned = fieldDefinitionRepository.findByKey(
                FieldScopeKind.PERSONAL, null, owner, namespace, "recorded-note",
            )

            assertNull(platformOwned?.scopeUserId, "The platform's own field belongs to nobody")
            assertEquals(owner, personallyOwned?.scopeUserId)
            assertNotEquals(platformOwned?.id, personallyOwned?.id)
        }
    }

    private fun personalField(owner: UUID, namespace: String, key: String) = FieldDefinition().apply {
        scopeKind = FieldScopeKind.PERSONAL
        scopeUserId = owner
        this.namespace = namespace
        fieldKey = key
        status = FieldLifecycleStatus.PUBLISHED
    }

    private fun personalSchema(owner: UUID, namespace: String, key: String) = SchemaDefinition().apply {
        scopeKind = FieldScopeKind.PERSONAL
        scopeUserId = owner
        this.namespace = namespace
        schemaKey = key
        displayName = "Process data"
        targetResourceType = "EXCHANGE"
        status = FieldLifecycleStatus.PUBLISHED
    }

    /** Namespaces are per test so one test's stored key never decides another's lookup. */
    private fun uniqueNamespace(): String = "process-${UUID.randomUUID().toString().take(8)}"

    private fun insertOwner(label: String): UUID
    {
        val id = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, ?, ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, "$label-${id.toString().take(8)}@process.test")
                statement.executeUpdate()
            }
        }
        return id
    }
}
