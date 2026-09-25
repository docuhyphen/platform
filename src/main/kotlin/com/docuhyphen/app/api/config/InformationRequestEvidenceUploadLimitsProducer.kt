package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceUploadLimits
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class InformationRequestEvidenceUploadLimitsProducer @Inject constructor(
    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-file-bytes", defaultValue = "26214400")
    private val maximumFileBytes: Long,

    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-no-auth-file-bytes", defaultValue = "10485760")
    private val maximumNoAuthFileBytes: Long,

    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-request-files", defaultValue = "200")
    private val maximumRequestFiles: Long,

    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-request-bytes", defaultValue = "524288000")
    private val maximumRequestBytes: Long,

    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-party-files", defaultValue = "100")
    private val maximumPartyFiles: Long,

    @ConfigProperty(name = "app.information-request.evidence.upload.maximum-party-bytes", defaultValue = "262144000")
    private val maximumPartyBytes: Long,
)
{
    @Produces
    @Singleton
    fun uploadLimits(): InformationRequestEvidenceUploadLimits =
        InformationRequestEvidenceUploadLimits(
            maximumFileBytes = maximumFileBytes,
            maximumNoAuthFileBytes = maximumNoAuthFileBytes,
            maximumRequestFiles = maximumRequestFiles,
            maximumRequestBytes = maximumRequestBytes,
            maximumPartyFiles = maximumPartyFiles,
            maximumPartyBytes = maximumPartyBytes,
        )
}
