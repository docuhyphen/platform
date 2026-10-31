package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.informationrequest.occurrence.InformationRequestGroupOccurrenceResource
import com.docuhyphen.app.api.resource.informationrequest.response.InformationRequestResponseResource
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestFieldsRefusalResourceTest {
    private val requestId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
    private val accessFactory = mock<InformationRequestAccessContextFactory>().also {
        whenever(it.currentAuthenticated()).thenReturn(access)
    }
    private val noAuthAccess = InformationRequestNoAuthAccessDoubles.current().also {
        whenever(it.resolve(requestId, TOKEN, SESSION)).thenReturn(access)
    }

    @ParameterizedTest
    @ValueSource(strings = [WORKSPACE, OCCURRENCE, RESPONSES, NO_AUTH_WORKSPACE, NO_AUTH_RESPONSES])
    fun `a Field value the Fields engine refuses answers 400 with a stable code on every request resource`(route: String) {
        val response = refusedBy(route, FieldValidationException("Must be at most 5 characters"))

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, route)
        assertEquals(
            InformationRequestErrorCatalog.FIELD_VALUE_INVALID,
            (response.entity as ResponseError).reasonCode,
            route
        )
        assertEquals("Must be at most 5 characters", (response.entity as ResponseError).errorMessage, route)
    }

    @ParameterizedTest
    @ValueSource(strings = [WORKSPACE, OCCURRENCE, RESPONSES, NO_AUTH_WORKSPACE, NO_AUTH_RESPONSES])
    fun `a stale Field precondition answers 412 with the current ETag on every request resource`(route: String) {
        val response = refusedBy(route, FieldsPreconditionException.stale(CURRENT_ETAG))

        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, response.status, route)
        assertEquals(CURRENT_ETAG, response.getHeaderString("ETag"), route)
        assertEquals("FIELDS_PRECONDITION_STALE", (response.entity as ResponseError).reasonCode, route)
    }

    private fun refusedBy(route: String, refusal: Exception): Response {
        val workspaces = mock<InformationRequestResponseWorkspaceService>().also {
            whenever(it.load(any(), any(), any())).thenThrow(refusal)
        }
        val occurrences = mock<InformationRequestGroupOccurrenceService>().also {
            whenever(it.add(any())).thenThrow(refusal)
        }
        val drafts = mock<InformationRequestResponseDraftService>().also {
            whenever(it.patch(any())).thenThrow(refusal)
        }
        val noAuth = InformationRequestNoAuthRequestResource(noAuthAccess, mock(), drafts, mock(), workspaces)
        return when (route) {
            WORKSPACE -> InformationRequestResource(mock(), mock(), accessFactory, workspaces, mock(), mock(), mock())
                .responseWorkspace(requestId.toString(), null)

            OCCURRENCE -> InformationRequestGroupOccurrenceResource(occurrences, accessFactory)
                .add(
                    requestId.toString(),
                    CreateInformationRequestGroupOccurrenceRequest("entries"),
                    RESPONSE_ETAG,
                    KEY
                )

            RESPONSES -> InformationRequestResponseResource(drafts, accessFactory)
                .patch(requestId.toString(), saving(), RESPONSE_ETAG, KEY)

            NO_AUTH_WORKSPACE -> noAuth.responseWorkspace(requestId.toString(), TOKEN, SESSION)

            else -> noAuth.patchResponses(requestId.toString(), saving(), TOKEN, RESPONSE_ETAG, KEY, SESSION)
        }
    }

    private fun saving() = PatchInformationRequestResponsesRequest(
        listOf(InformationRequestResponsePatchRequest(requirementId = UUID.randomUUID(), narrative = "Recorded note")),
    )

    private companion object {
        const val WORKSPACE = "authenticated workspace"
        const val OCCURRENCE = "authenticated group occurrence"
        const val RESPONSES = "authenticated responses"
        const val NO_AUTH_WORKSPACE = "access-link workspace"
        const val NO_AUTH_RESPONSES = "access-link responses"
        const val RESPONSE_ETAG = "\"responses:1\""
        const val CURRENT_ETAG = "\"field-set:current\""
        const val KEY = "fields-refusal-command"
        const val TOKEN = "bootstrap"
        const val SESSION = "session"
    }
}
