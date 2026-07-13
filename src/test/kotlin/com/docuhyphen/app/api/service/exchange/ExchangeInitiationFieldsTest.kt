package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Creation-time schema + field-value seam (EXCHANGE-FIELDS-AT-CREATION-PLAN.md, items 1-3).
 *
 * These target [ExchangeInitiationService.applyCreationTimeFields], the internal seam invoked by
 * [ExchangeInitiationService.initiateExchange] AFTER the Exchange is persisted and BEFORE any
 * workflow fires. That ordering is guaranteed structurally by the single call site in
 * `initiateExchange` (the helper runs before the `workflowEngineService.trigger(...)` block).
 *
 * The service has a Mockito-only test harness (no Quarkus/DB container), so we exercise the seam
 * directly rather than driving the full initiation flow. Verified here:
 *  - schema + values -> assignSchema then setValues, in order, for the EXCHANGE resource type.
 *  - values without a schema -> rejected; no assignment attempted.
 *  - invalid value (validator failure) -> exception propagates so the @Transactional creation rolls
 *    back; the assignment was attempted first.
 *  - a non-owner / forbidden path (assignSchema throws ForbiddenException) propagates.
 *  - no schema supplied -> nothing is assigned (unchanged behavior).
 */
class ExchangeInitiationFieldsTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val schemaDefinitionId: UUID = UUID.randomUUID()
    private val fieldContractId: UUID = UUID.randomUUID()

    private fun makeService(schemaAssignmentService: SchemaAssignmentService): ExchangeInitiationService =
        ExchangeInitiationService(
            exchangeRepository = mock(),
            appUserRepository = mock(),
            appUserService = mock(),
            emailService = mock(),
            emailTemplateService = mock(),
            otpService = mock(),
            authTokenContext = mock(),
            authenticationService = mock(),
            authRateLimitService = mock(),
            configurationService = mock(),
            authAuditService = mock(),
            appNotificationService = mock(),
            shareService = mock(),
            principalGroupRepository = mock(),
            principalGroupMemberRepository = mock(),
            organizationExchangePolicyService = mock(),
            workflowEngineService = mock(),
            organizationMembershipService = mock(),
            organizationRepository = mock(),
            templateVariableInterpolator = mock(),
            documentLibraryService = mock(),
            fileStorageService = mock(),
            schemaAssignmentService = schemaAssignmentService,
            noAuthExchangeAccessTokenService = mock(),
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
            .assignSchema(eq(ResourceType.EXCHANGE.name), eq(exchangeId), eq(schemaDefinitionId), eq(SchemaAssignmentSource.MANUAL))
        order.verify(schemaAssignmentService)
            .setValues(eq(ResourceType.EXCHANGE.name), eq(exchangeId), eq(listOf(entry())))
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
            .assignSchema(eq(ResourceType.EXCHANGE.name), eq(exchangeId), eq(schemaDefinitionId), eq(SchemaAssignmentSource.MANUAL))
        verify(schemaAssignmentService, never()).setValues(any(), any(), any())
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

        verify(schemaAssignmentService, never()).assignSchema(any(), any(), any(), any())
        verify(schemaAssignmentService, never()).setValues(any(), any(), any())
    }

    @Test
    fun `invalid value - validation failure propagates for rollback after schema assigned`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        whenever(schemaAssignmentService.setValues(any(), any(), any()))
            .thenThrow(FieldValidationException("Category is required"))
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
            this.fieldValues = listOf(entry())
        }

        assertThrows<FieldValidationException> { service.applyCreationTimeFields(exchangeId, dto) }

        verify(schemaAssignmentService)
            .assignSchema(eq(ResourceType.EXCHANGE.name), eq(exchangeId), eq(schemaDefinitionId), eq(SchemaAssignmentSource.MANUAL))
    }

    @Test
    fun `forbidden assignment propagates`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        whenever(schemaAssignmentService.assignSchema(any(), any(), any(), any()))
            .thenThrow(ForbiddenException("not owner"))
        val service = makeService(schemaAssignmentService)

        val dto = ExchangeInitiationDto().apply {
            this.schemaDefinitionId = this@ExchangeInitiationFieldsTest.schemaDefinitionId.toString()
            this.fieldValues = listOf(entry())
        }

        assertThrows<ForbiddenException> { service.applyCreationTimeFields(exchangeId, dto) }

        verify(schemaAssignmentService, never()).setValues(any(), any(), any())
    }

    @Test
    fun `no schema supplied - nothing assigned`()
    {
        val schemaAssignmentService = mock<SchemaAssignmentService>()
        val service = makeService(schemaAssignmentService)

        service.applyCreationTimeFields(exchangeId, ExchangeInitiationDto())

        verify(schemaAssignmentService, never()).assignSchema(any(), any(), any(), any())
        verify(schemaAssignmentService, never()).setValues(any(), any(), any())
    }
}
