package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class ExchangeInitiationFieldsTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val schemaDefinitionId: UUID = UUID.randomUUID()
    private val fieldContractId: UUID = UUID.randomUUID()

    private val access = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    private val fieldsAccessContextFactory = mock<FieldsAccessContextFactory>().also {
        whenever(it.current()).thenReturn(access)
    }

    private fun resource() = FieldsResourceRef(ResourceType.EXCHANGE.name, exchangeId)

    private fun assignCommand() = SchemaAssignmentCommand(
        resource(), access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId, SchemaAssignmentSource.MANUAL,
    )

    private fun writeCommand() = FieldValueWriteCommand(resource(), access, listOf(entry()))

    private fun makeService(schemaAssignmentService: SchemaAssignmentService): ExchangeInitiationService =
        ExchangeInitiationService(
            exchangeRepository = mock(),
            appUserRepository = mock(),
            appUserService = mock(),
            emailTemplateService = mock(),
            otpService = mock(),
            authTokenContext = mock(),
            authorizationService = mock(),
            authorizationContextFactory = mock(),
            authenticationService = mock(),
            authRateLimitService = mock(),
            configurationService = mock(),
            authAuditService = mock(),
            shareService = mock(),
            exchangeRecipientService = mock(),
            exchangeRecipientAttestationService = mock(),
            externalIdentityResolutionService = mock(),
            exchangeRecipientSelectionResolver = mock(),
            organizationGroupService = mock(),
            workflowEngineService = mock(),
            organizationService = mock(),
            templateVariableInterpolator = mock(),
            documentLibraryService = mock(),
            fileStorageService = mock(),
            documentContentHashService = mock(),
            documentThumbnailService = mock(),
            schemaAssignmentService = schemaAssignmentService,
            fieldsAccessContextFactory = fieldsAccessContextFactory,
            noAuthExchangeAccessTokenService = mock(),
            exchangeNotificationDeliveryService = mock(),
            exchangeInitiationSubscriptionGuard = mock(),
            blueprintInformationRequestStarter = mock(),
        )

    private fun entry(): FieldValueEntry =
        FieldValueEntry(fieldContractId = fieldContractId, value = JsonPrimitive("Onboarding"))

    @Test
    fun `schema and values - assigns schema then sets values for the exchange`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
            this.fieldValues = listOf(entry())
        }

        service.applyCreationTimeFields(exchangeId, dto)

        val order = inOrder(schemaAssignmentService)
        order.verify(schemaAssignmentService)
            .applySchemaAssignment(eq(assignCommand()))
        order.verify(schemaAssignmentService)
            .setValues(eq(writeCommand()))
    }

    @Test
    fun `schema without values - assigns schema and does not call setValues`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
        }

        service.applyCreationTimeFields(exchangeId, dto)

        verify(schemaAssignmentService)
            .applySchemaAssignment(eq(assignCommand()))
        verify(schemaAssignmentService, never()).setValues(any())
    }

    @Test
    fun `values without schema - rejected and nothing assigned`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.fieldValues = listOf(entry())
        }

        assertThrows<IllegalArgumentException> { service.applyCreationTimeFields(exchangeId, dto) }

        verify(schemaAssignmentService, never()).applySchemaAssignment(any())
        verify(schemaAssignmentService, never()).setValues(any())
    }

    @Test
    fun `invalid value - validation failure propagates for rollback after schema assigned`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        whenever(schemaAssignmentService.setValues(any()))
            .thenThrow(FieldValidationException("Category is required"))
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
            this.fieldValues = listOf(entry())
        }

        assertThrows<FieldValidationException> { service.applyCreationTimeFields(exchangeId, dto) }

        verify(schemaAssignmentService)
            .applySchemaAssignment(eq(assignCommand()))
    }

    @Test
    fun `forbidden assignment propagates`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        whenever(schemaAssignmentService.applySchemaAssignment(any()))
            .thenThrow(ForbiddenException("not owner"))
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
            this.fieldValues = listOf(entry())
        }

        assertThrows<ForbiddenException> { service.applyCreationTimeFields(exchangeId, dto) }

        verify(schemaAssignmentService, never()).setValues(any())
    }

    @Test
    fun `no schema supplied - nothing assigned`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val service = makeService(schemaAssignmentService)

        service.applyCreationTimeFields(exchangeId, ExchangeInitiationDto())

        verify(schemaAssignmentService, never()).applySchemaAssignment(any())
        verify(schemaAssignmentService, never()).setValues(any())
    }
}
