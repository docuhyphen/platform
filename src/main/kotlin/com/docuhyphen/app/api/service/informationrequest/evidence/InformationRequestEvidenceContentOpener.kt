package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMediaTypes
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceRelease
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class InformationRequestEvidenceContentOpener @Inject constructor(
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val accessAudit: InformationRequestEvidenceAccessAudit,
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
) {
    fun open(
        requestId: UUID,
        access: RequestAccessContext,
        use: InformationRequestEvidenceContentUse,
        release: (RequestAccessContext) -> InformationRequestEvidenceRelease,
    ): InformationRequestEvidenceContent {
        val released = noAuthAccess.readCurrent(requestId, access, release)
        val documentVersion =
            documentVersionRecordingService.findVersion(released.documentVersionId) ?: throw contentUnavailable()
        val content = try {
            documentVersionRecordingService.open(documentVersion)
        } catch (_: DocumentVersionContentIntegrityException) {
            throw integrityFailed(released, DIGEST_MISMATCH)
        } catch (_: DocumentVersionContentNotFoundException) {
            throw integrityFailed(released, CONTENT_MISSING)
        }

        return try {
            val mediaType = InformationRequestEvidenceMediaTypes.detect(content.file)
            val inline = use == InformationRequestEvidenceContentUse.PREVIEW
            if (inline && !InformationRequestEvidenceMediaTypes.isInlineSafe(mediaType)) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.EVIDENCE_PREVIEW_UNAVAILABLE,
                    "This evidence cannot be previewed; download it instead",
                )
            }

            val confirmed = release(noAuthAccess.confirmCurrent(requestId, released.access))
            accessAudit.record(confirmed, use)
            InformationRequestEvidenceContent(
                content = content,
                fileName = released.version.declaredFileName ?: content.fileName,
                mediaType = mediaType,
                inline = inline,
            )
        } catch (failure: Exception) {
            content.close()
            throw failure
        }
    }

    fun contentUnavailable() =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.EVIDENCE_CONTENT_UNAVAILABLE,
            "This evidence content is not available",
        )

    private fun integrityFailed(
        released: InformationRequestEvidenceRelease,
        reason: String
    ): InformationRequestLifecycleException {
        logger.error(
            "$INTEGRITY_FAILED_MARKER reason=$reason informationRequestId=${released.request.id} " +
                    "evidenceVersionId=${released.version.id} documentVersionId=${released.documentVersionId}",
        )
        return contentUnavailable()
    }

    companion object {
        const val INTEGRITY_FAILED_MARKER = "INFORMATION_REQUEST_EVIDENCE_CONTENT_INTEGRITY_FAILED"
        private const val DIGEST_MISMATCH = "DIGEST_MISMATCH"
        private const val CONTENT_MISSING = "CONTENT_MISSING"
        private val logger = LoggerFactory.getLogger(InformationRequestEvidenceContentOpener::class.java)
    }
}
