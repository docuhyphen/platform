package com.docuhyphen.app.api.interceptor.informationrequest

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceSizes
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAbuseLog
import jakarta.inject.Inject
import jakarta.ws.rs.HttpMethod
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

@Provider
@PreMatching
class InformationRequestEvidenceUploadSizeFilter @Inject constructor(
    private val limits: InformationRequestEvidenceUploadLimits,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        if (requestContext.method != HttpMethod.POST) return
        val surface = surfaceOf(requestContext.uriInfo.path) ?: return
        val declared = requestContext.getHeaderString(HttpHeaders.CONTENT_LENGTH)?.trim()?.toLongOrNull()
        if (declared == null) {
            InformationRequestAbuseLog.refused(
                InformationRequestAbuseControl.EVIDENCE_UPLOAD_LIMIT,
                "surface=$surface declaredBytes=none",
            )
            requestContext.abortWith(
                refusal(
                    Response.Status.LENGTH_REQUIRED,
                    "An evidence upload states its size in Content-Length",
                    InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LENGTH_REQUIRED,
                ),
            )
            return
        }
        if (declared <= limits.requestBodyCeiling(surface)) return

        InformationRequestAbuseLog.refused(
            InformationRequestAbuseControl.EVIDENCE_UPLOAD_LIMIT,
            "surface=$surface declaredBytes=$declared",
        )
        requestContext.abortWith(
            refusal(
                Response.Status.REQUEST_ENTITY_TOO_LARGE,
                "An evidence file is at most ${InformationRequestEvidenceSizes.describe(limits.fileLimit(surface))}",
                InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED,
            ),
        )
    }

    private fun refusal(status: Response.Status, message: String, reasonCode: String): Response =
        Response.status(status)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(ResponseError(message, reasonCode))
            .build()

    private fun surfaceOf(path: String): InformationRequestEvidenceSurface? {
        val segments = segmentsOf(path)
        val noAuth = segments.firstOrNull() == NO_AUTH_SEGMENT
        val route = if (noAuth) segments.drop(1) else segments
        val evidenceUpload = route.size in setOf(UPLOAD_SEGMENTS, REPLACEMENT_SEGMENTS) &&
                route[0] == REQUESTS_SEGMENT &&
                route[2] == REQUIREMENTS_SEGMENT &&
                route[4] == ARTIFACTS_SEGMENT &&
                (route.size == UPLOAD_SEGMENTS || route[6] == VERSIONS_SEGMENT)
        if (!evidenceUpload) return null
        return if (noAuth) InformationRequestEvidenceSurface.NO_AUTH else InformationRequestEvidenceSurface.AUTHENTICATED
    }

    private fun segmentsOf(path: String): List<String> {
        val resolved = ArrayDeque<String>()
        path.replace('\\', '/').split('/').map { it.substringBefore(';') }.forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> resolved.removeLastOrNull()
                else -> resolved.addLast(segment)
            }
        }
        return resolved.toList()
    }

    private companion object {
        const val NO_AUTH_SEGMENT = "no-auth"
        const val REQUESTS_SEGMENT = "information-requests"
        const val REQUIREMENTS_SEGMENT = "requirements"
        const val ARTIFACTS_SEGMENT = "evidence-artifacts"
        const val VERSIONS_SEGMENT = "versions"
        const val UPLOAD_SEGMENTS = 5
        const val REPLACEMENT_SEGMENTS = 7
    }
}
