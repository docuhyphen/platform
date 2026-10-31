package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.resource.informationrequest.evidence.InformationRequestEvidenceHttp
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestSubmissionEvidenceResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionEvidenceQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSubmissionEvidenceResource @Inject constructor(
    private val evidenceQueries: InformationRequestSubmissionEvidenceQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestSubmissionEvidenceResourceOperations {
    override fun download(id: String, packageId: String, versionId: String): Response {
        return try {
            content(id, packageId, versionId, InformationRequestEvidenceContentUse.DOWNLOAD)
        } catch (exception: Exception) {
            InformationRequestEvidenceHttp.refused(
                logger,
                "Information Request submitted evidence download failed",
                exception
            )
        }
    }

    override fun preview(id: String, packageId: String, versionId: String): Response {
        return try {
            content(id, packageId, versionId, InformationRequestEvidenceContentUse.PREVIEW)
        } catch (exception: Exception) {
            InformationRequestEvidenceHttp.refused(
                logger,
                "Information Request submitted evidence preview failed",
                exception
            )
        }
    }

    private fun content(
        id: String,
        packageId: String,
        versionId: String,
        use: InformationRequestEvidenceContentUse
    ): Response =
        InformationRequestEvidenceHttp.content(
            evidenceQueries.openContent(
                InformationRequestEvidenceHttp.uuid(id, "information request id"),
                InformationRequestEvidenceHttp.uuid(packageId, "submission package id"),
                InformationRequestEvidenceHttp.uuid(versionId, "evidence version id"),
                accessContextFactory.currentAuthenticated(),
                use,
            ),
        )

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestSubmissionEvidenceResource::class.java)
    }
}
