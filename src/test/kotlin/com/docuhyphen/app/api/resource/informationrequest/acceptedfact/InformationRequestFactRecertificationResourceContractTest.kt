package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestFactRecertificationDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertification
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestFactRecertificationResult
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestFactRecertificationView
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestFactRecertificationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthFactRecertificationResourceOperations
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestFactRecertificationResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val factId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))
    private val recertifications: InformationRequestFactRecertificationService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService =
        InformationRequestNoAuthAccessDoubles.current()
    private val resource = InformationRequestFactRecertificationResource(recertifications, accessContextFactory)
    private val noAuthResource = InformationRequestNoAuthFactRecertificationResource(recertifications, readAccessService)

    @Test
    fun `a wildcard If-Match on a recertification is sent to the service as a missing precondition`() {
        whenever(recertifications.recertify(any()))
            .thenThrow(com.docuhyphen.app.api.service.command.CommandPreconditionException.required("\"responses-4\""))

        val response = resource.recertify(
            requestId.toString(),
            factId.toString(),
            RecertifyInformationRequestAcceptedFactRequest(requirementId, assented = true),
            "*",
            "key-1",
        )

        assertEquals(428, response.status)
        verify(recertifications).recertify(
            org.mockito.kotlin.argThat { precondition == CommandPrecondition.Absent },
        )
    }

    private val recertification = InformationRequestFactRecertification().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirementId
        responseId = UUID.randomUUID()
        responseRevision = 4
        factId = this@InformationRequestFactRecertificationResourceContractTest.factId
        purposeKey = "profile.reuse"
        policyBasisKey = "policy.reuse"
        valueType = FieldValueType.SHORT_TEXT
        canonicalValue = "\"Synthetic value\""
        sourceInformationRequestId = UUID.randomUUID()
        sourcePackageId = UUID.randomUUID()
        sourceSubmissionItemId = UUID.randomUUID()
        sourceRequirementId = UUID.randomUUID()
        sourceFieldValueRevisionId = UUID.randomUUID()
        assentedByPrincipalKind = PrincipalKind.USER
        assentedByPrincipalId = UUID.randomUUID()
    }
    private val result = InformationRequestFactRecertificationResult(
        InformationRequestFactRecertificationView(recertification, listOf(UUID.randomUUID())),
        "\"responses-5\"",
    )

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(readAccessService.resolve(eq(requestId), eq("link-token"), anyOrNull())).thenReturn(noAuthAccess)
        whenever(recertifications.recertify(any())).thenReturn(result)
    }

    @Test
    fun `recertification is a sub-resource action of an offer on both access surfaces`()
    {
        assertEquals(
            "/information-requests/{id}/accepted-fact-offers/{factId}/recertifications",
            InformationRequestFactRecertificationResourceOperations::class.java.getAnnotation(Path::class.java).value,
        )
        assertEquals(
            "no-auth/information-requests/{id}/accepted-fact-offers/{factId}/recertifications",
            InformationRequestNoAuthFactRecertificationResourceOperations::class.java.getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `both surfaces send the same assent, precondition, and key and answer the response revision without source identifiers`()
    {
        val clears = setOf(UUID.randomUUID())
        val body = RecertifyInformationRequestAcceptedFactRequest(
            requirementId,
            assented = true,
            confirmedHiddenResponseClearRequirementIds = clears
        )
        val authenticated = resource.recertify(requestId.toString(), factId.toString(), body, "\"responses-4\"", "key-1")
        val linked = noAuthResource.recertify(requestId.toString(), factId.toString(), body, "link-token", null, "\"responses-4\"", "key-1")

        val commands = argumentCaptor<RecertifyInformationRequestAcceptedFactCommand>()
        verify(recertifications, org.mockito.kotlin.times(2)).recertify(commands.capture())
        assertEquals(listOf(access, noAuthAccess), commands.allValues.map { it.access })
        commands.allValues.forEach { command ->
            assertEquals(requestId, command.requestId)
            assertEquals(factId, command.factId)
            assertEquals(requirementId, command.requirementId)
            assertEquals(true, command.assented)
            assertEquals(CommandPrecondition.ExpectedRevision("\"responses-4\""), command.precondition)
            assertEquals("key-1", command.idempotencyKey)
            assertEquals(clears, command.confirmedHiddenResponseClears)
        }
        listOf(authenticated, linked).forEach { response ->
            assertEquals(201, response.status)
            assertEquals("\"responses-5\"", response.getHeaderString("ETag"))
            val dto = response.entity as InformationRequestFactRecertificationDto
            assertEquals(4, dto.responseRevision)
            assertEquals(JsonPrimitive("Synthetic value"), dto.value)
            val serialized = Json.encodeToString(InformationRequestFactRecertificationDto.serializer(), dto)
            assertFalse(serialized.contains(recertification.sourceInformationRequestId.toString()), serialized)
            assertFalse(serialized.contains("source"), serialized)
            assertFalse(serialized.contains("evidence"), serialized)
        }
    }

    @Test
    fun `an absent body, key, or precondition and an unavailable offer are refused`()
    {
        assertEquals(400, resource.recertify(requestId.toString(), factId.toString(), null, "\"responses-4\"", "key-1").status)
        assertEquals(
            400,
            resource.recertify(requestId.toString(), factId.toString(), RecertifyInformationRequestAcceptedFactRequest(requirementId, true), "\"r\"", null).status,
        )
        assertEquals(
            400,
            noAuthResource.recertify(requestId.toString(), factId.toString(), RecertifyInformationRequestAcceptedFactRequest(requirementId, true), null, null, "\"r\"", "key-1").status,
        )
        verify(recertifications, never()).recertify(any())

        val absent = argumentCaptor<RecertifyInformationRequestAcceptedFactCommand>()
        resource.recertify(requestId.toString(), factId.toString(), RecertifyInformationRequestAcceptedFactRequest(requirementId, true), null, "key-2")
        verify(recertifications).recertify(absent.capture())
        assertEquals(CommandPrecondition.Absent, absent.firstValue.precondition)

        whenever(recertifications.recertify(any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE, "No longer available"),
        )
        val refused = noAuthResource.recertify(
            requestId.toString(),
            factId.toString(),
            RecertifyInformationRequestAcceptedFactRequest(requirementId, true),
            "link-token",
            null,
            "\"r\"",
            "key-3",
        )
        assertEquals(409, refused.status)
        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE, (refused.entity as ResponseError).reasonCode)
    }
}
