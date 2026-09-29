package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestFactRecertificationDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertification
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFactRecertificationResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFactRecertificationView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.model.informationrequest.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.resource.model.RecertifyInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestFactRecertificationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestFactRecertificationResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val factId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))
    private val recertifications: InformationRequestFactRecertificationService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService = mock()
    private val resource = InformationRequestFactRecertificationResource(recertifications, accessContextFactory)
    private val noAuthResource = InformationRequestNoAuthFactRecertificationResource(recertifications, readAccessService)
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
        whenever(readAccessService.resolve(eq("link-token"), anyOrNull())).thenReturn(InformationRequestNoAuthAccess(noAuthAccess, requestId))
        whenever(recertifications.recertify(any())).thenReturn(result)
    }

    @Test
    fun `recertification is a sub-resource action of an offer on both access surfaces`()
    {
        assertEquals(
            "/information-requests/{id}/accepted-fact-offers/{factId}/recertifications",
            InformationRequestFactRecertificationResource::class.java.getAnnotation(Path::class.java).value,
        )
        assertEquals(
            "no-auth/information-requests/{id}/accepted-fact-offers/{factId}/recertifications",
            InformationRequestNoAuthFactRecertificationResource::class.java.getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `both surfaces send the same assent, precondition, and key and answer the response revision without source identifiers`()
    {
        val body = RecertifyInformationRequestAcceptedFactRequest(requirementId, assented = true)
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
