package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Which resource a Schema is written for used to be settled before the caller asked: creation wrote
 * the Exchange code as a literal and never read the request. These tests hold the target as
 * something the author states and the platform checks, so a second kind of resource can be given
 * schemas without the first kind's name standing in for every kind.
 */
class SchemaTargetAuthoringTest
{
    private val principal = PrincipalRef.user(UUID.randomUUID())

    private data class Fixture(
        val service: SchemaDefinitionService,
        val schemaRepository: SchemaDefinitionRepository,
    )

    private fun fixture(): Fixture
    {
        val schemaRepository = mock<SchemaDefinitionRepository>()
        val versionRepository = mock<SchemaVersionRepository>()
        val bindingRepository = mock<SchemaFieldBindingRepository>()
        val contractRepository = mock<FieldContractRepository>()
        val fieldRepository = mock<FieldDefinitionRepository>()
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = null))
        val roles = mock<UserRoleService>()
        whenever(roles.isAppAdmin(principal.id)).thenReturn(true)
        whenever(schemaRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(versionRepository.save(any())).thenAnswer { it.getArgument(0) }

        return Fixture(
            SchemaDefinitionService(
                schemaDefinitionRepository = schemaRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldDefinitionRepository = fieldRepository,
                fieldValueValidator = mock<FieldValueValidator>(),
                authorizationService = mock<AuthorizationService>(),
                authorizationContextFactory = contextFactory,
                userRoleService = roles,
                auditRecorder = mock<AuditRecorder>(),
                subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository, contractRepository, fieldRepository, mock(), mock(),
                ),
                schemaTargets = SchemaTargetRegistry(),
            ),
            schemaRepository,
        )
    }

    private fun request(target: String? = null) = CreateSchemaRequest(
        namespace = "process",
        schemaKey = "collected-data",
        displayName = "Collected data",
        targetResourceType = target,
    )

    private fun savedBy(fixture: Fixture): SchemaDefinition
    {
        val saved = argumentCaptor<SchemaDefinition>()
        verify(fixture.schemaRepository).save(saved.capture())
        return saved.firstValue
    }

    @Test
    fun `a schema a caller names no target for is written for an exchange`()
    {
        val fixture = fixture()

        val result = fixture.service.createSchema(request())

        assertEquals(ResourceType.EXCHANGE.name, result.targetResourceType)
        assertEquals(ResourceType.EXCHANGE.name, savedBy(fixture).targetResourceType)
    }

    @Test
    fun `a schema may be written for an information request`()
    {
        val fixture = fixture()

        val result = fixture.service.createSchema(request("INFORMATION_REQUEST"))

        assertEquals("INFORMATION_REQUEST", result.targetResourceType)
        assertEquals("INFORMATION_REQUEST", savedBy(fixture).targetResourceType)
    }

    @Test
    fun `a schema may not be written for a target nobody declared`()
    {
        val fixture = fixture()

        assertThrows<FieldValidationException> {
            fixture.service.createSchema(request("UNDECLARED_RESOURCE"))
        }

        verify(fixture.schemaRepository, never()).save(any())
    }
}
