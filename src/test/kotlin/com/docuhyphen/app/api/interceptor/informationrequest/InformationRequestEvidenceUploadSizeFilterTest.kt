package com.docuhyphen.app.api.interceptor.informationrequest

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestEvidenceUploadSizeFilterTest {
    private val limits = InformationRequestEvidenceUploadLimits(
        maximumFileBytes = 1_000_000,
        maximumNoAuthFileBytes = 400_000,
        maximumRequestFiles = 50,
        maximumRequestBytes = 50_000_000,
        maximumPartyFiles = 25,
        maximumPartyBytes = 25_000_000,
    )
    private val filter = InformationRequestEvidenceUploadSizeFilter(limits)
    private val uploadPath =
        "/information-requests/${UUID.randomUUID()}/requirements/${UUID.randomUUID()}/evidence-artifacts"
    private val replacementPath = "$uploadPath/${UUID.randomUUID()}/versions"
    private val noAuthUploadPath = "/no-auth$uploadPath"
    private val noAuthReplacementPath = "no-auth$replacementPath"

    @Test
    fun `the guard is applied before a request is matched to a resource`() {
        assertTrue(InformationRequestEvidenceUploadSizeFilter::class.java.isAnnotationPresent(PreMatching::class.java))
        assertTrue(InformationRequestEvidenceUploadSizeFilter::class.java.isAnnotationPresent(Provider::class.java))
    }

    @Test
    fun `an upload or replacement declaring more than its surface allows is refused before its body is read`() {
        val authenticatedOver = limits.requestBodyCeiling(InformationRequestEvidenceSurface.AUTHENTICATED) + 1
        val noAuthOver = limits.requestBodyCeiling(InformationRequestEvidenceSurface.NO_AUTH) + 1

        listOf(
            request("POST", uploadPath, authenticatedOver),
            request("POST", replacementPath, authenticatedOver),
            request("POST", noAuthUploadPath, noAuthOver),
            request("POST", noAuthReplacementPath, noAuthOver),
            request("POST", "$noAuthUploadPath;session=abc", noAuthOver),
            request(
                "POST",
                "/information-requests/./${UUID.randomUUID()}/requirements/${UUID.randomUUID()}//evidence-artifacts/",
                authenticatedOver
            ),
        ).forEach { context ->
            filter.filter(context)

            val refusal = argumentCaptor<Response>().also { verify(context).abortWith(it.capture()) }.firstValue
            assertEquals(413, refusal.status)
            assertEquals(
                InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED,
                (refusal.entity as ResponseError).reasonCode,
            )
        }
    }

    @Test
    fun `the refusal states the file limit in readable units rather than bytes`() {
        val signedIn =
            request("POST", uploadPath, limits.requestBodyCeiling(InformationRequestEvidenceSurface.AUTHENTICATED) + 1)
        val anonymous =
            request("POST", noAuthUploadPath, limits.requestBodyCeiling(InformationRequestEvidenceSurface.NO_AUTH) + 1)

        filter.filter(signedIn)
        filter.filter(anonymous)

        assertEquals("An evidence file is at most 976.6 KiB", refusalMessage(signedIn))
        assertEquals("An evidence file is at most 390.6 KiB", refusalMessage(anonymous))
    }

    @Test
    fun `an anonymous upload is held to the stricter ceiling that a signed-in upload of the same size passes`() {
        val between = limits.requestBodyCeiling(InformationRequestEvidenceSurface.NO_AUTH) + 1
        val anonymous = request("POST", noAuthUploadPath, between)
        val signedIn = request("POST", uploadPath, between)

        filter.filter(anonymous)
        filter.filter(signedIn)

        verify(anonymous).abortWith(any())
        verify(signedIn, never()).abortWith(any())
    }

    @Test
    fun `an upload or replacement that does not declare its length is refused before its body is read`() {
        listOf(
            request("POST", uploadPath, null),
            request("POST", replacementPath, null),
            request("POST", noAuthUploadPath, null),
            requestDeclaring("POST", noAuthReplacementPath, "unstated"),
        ).forEach { context ->
            filter.filter(context)

            val refusal = argumentCaptor<Response>().also { verify(context).abortWith(it.capture()) }.firstValue
            assertEquals(411, refusal.status)
            assertEquals(
                InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LENGTH_REQUIRED,
                (refusal.entity as ResponseError).reasonCode,
            )
        }
    }

    @Test
    fun `uploads within the ceiling, reads, and other routes pass untouched`() {
        val within = limits.requestBodyCeiling(InformationRequestEvidenceSurface.NO_AUTH)
        val oversized = limits.requestBodyCeiling(InformationRequestEvidenceSurface.AUTHENTICATED) + 1

        listOf(
            request("POST", noAuthUploadPath, within),
            request("GET", "$replacementPath/${UUID.randomUUID()}/content", oversized),
            request("POST", "$uploadPath/${UUID.randomUUID()}/withdrawals", oversized),
            request("POST", "$uploadPath/${UUID.randomUUID()}/withdrawals", null),
            request("POST", "/exchanges/${UUID.randomUUID()}/documents", null),
        ).forEach { context ->
            filter.filter(context)

            verify(context, never()).abortWith(any())
        }
    }

    private fun refusalMessage(context: ContainerRequestContext): String? {
        val refusal = argumentCaptor<Response>().also { verify(context).abortWith(it.capture()) }.firstValue
        return (refusal.entity as ResponseError).errorMessage
    }

    private fun request(method: String, path: String, contentLength: Long?): ContainerRequestContext =
        requestDeclaring(method, path, contentLength?.toString())

    private fun requestDeclaring(method: String, path: String, contentLength: String?): ContainerRequestContext {
        val uriInfo = mock<UriInfo>()
        whenever(uriInfo.path).thenReturn(path)
        val context = mock<ContainerRequestContext>()
        whenever(context.method).thenReturn(method)
        whenever(context.uriInfo).thenReturn(uriInfo)
        whenever(context.getHeaderString(HttpHeaders.CONTENT_LENGTH)).thenReturn(contentLength)
        return context
    }
}
