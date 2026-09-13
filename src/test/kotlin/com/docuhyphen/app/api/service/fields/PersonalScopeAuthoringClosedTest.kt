package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Storage can now record a person as the owner of a Field or a Schema, but nothing yet resolves that
 * owner when a request arrives: there is no personal owner in the configuration scope model, the
 * subscription guard has no paying subject for one, and no rollout decision has been made about who
 * may author personally.
 *
 * A request naming the personal scope must therefore be turned away with a reason the caller can
 * read, rather than reaching a guard that fails on a missing organization or a database that refuses
 * the row. These tests hold that door shut until the personal path is finished behind it.
 */
class PersonalScopeAuthoringClosedTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    @Test
    fun `a Schema cannot yet be authored into a personal scope`()
    {
        val repository = mock<SchemaDefinitionRepository>()
        val service = schemaService(repository)

        val refusal = assertThrows<FieldValidationException> {
            service.createSchema(
                CreateSchemaRequest(
                    namespace = "process",
                    schemaKey = "process-data",
                    displayName = "Process data",
                    scopeKind = FieldScopeKind.PERSONAL,
                ),
            )
        }

        assertTrue(
            refusal.message.orEmpty().contains("Personal", ignoreCase = true),
            "The refusal names the scope that is not available: ${refusal.message}",
        )
        verify(repository, never()).save(any())
    }

    @Test
    fun `a Field cannot yet be authored into a personal scope`()
    {
        val repository = mock<FieldDefinitionRepository>()
        val service = fieldService(repository)

        val refusal = assertThrows<FieldValidationException> {
            service.createDefinition(
                CreateFieldDefinitionRequest(
                    namespace = "process",
                    fieldKey = "recorded-note",
                    scopeKind = FieldScopeKind.PERSONAL,
                    contract = FieldContractRequest(
                        valueType = FieldValueType.SHORT_TEXT,
                        label = "Recorded note",
                    ),
                ),
            )
        }

        assertTrue(
            refusal.message.orEmpty().contains("Personal", ignoreCase = true),
            "The refusal names the scope that is not available: ${refusal.message}",
        )
        verify(repository, never()).save(any())
    }

    private fun schemaService(repository: SchemaDefinitionRepository): SchemaDefinitionService
    {
        val bindingRepository = mock<SchemaFieldBindingRepository>()
        val contractRepository = mock<FieldContractRepository>()
        val fieldRepository = mock<FieldDefinitionRepository>()

        return SchemaDefinitionService(
            schemaDefinitionRepository = repository,
            schemaVersionRepository = mock<SchemaVersionRepository>(),
            bindingRepository = bindingRepository,
            fieldContractRepository = contractRepository,
            fieldDefinitionRepository = fieldRepository,
            fieldValueValidator = mock<FieldValueValidator>(),
            authorizationService = authorization(),
            authorizationContextFactory = contextFactory(),
            userRoleService = roles(),
            auditRecorder = mock<AuditRecorder>(),
            subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
            projectionLoader = FieldsProjectionLoader(
                bindingRepository, contractRepository, fieldRepository, mock(), mock(),
            ),
            schemaTargets = SchemaTargetRegistry(),
        )
    }

    private fun fieldService(repository: FieldDefinitionRepository): FieldDefinitionService =
        FieldDefinitionService(
            fieldDefinitionRepository = repository,
            fieldContractRepository = mock<FieldContractRepository>(),
            validator = mock<FieldValueValidator>(),
            typeRegistry = FieldTypeRegistry(),
            authorizationService = authorization(),
            authorizationContextFactory = contextFactory(),
            userRoleService = roles(),
            auditRecorder = mock<AuditRecorder>(),
            subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
        )

    private fun contextFactory(): AuthorizationContextFactory
    {
        val contextFactory = mock<AuthorizationContextFactory>()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(AuthorizationContext(activeOrgId = organizationId))
        return contextFactory
    }

    private fun roles(): UserRoleService
    {
        val roles = mock<UserRoleService>()
        whenever(roles.isAppAdmin(principalId)).thenReturn(true)
        whenever(roles.orgRolesIn(any(), any())).thenReturn(setOf(OrganizationRoleName.ORG_ADMIN))
        return roles
    }

    private fun authorization(): AuthorizationService
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        return authorization
    }
}
