package com.docuhyphen.app.api.service.informationrequest

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class InformationRequestEvidenceDeploymentPolicy @Inject constructor(
    @ConfigProperty(name = "app.information-request.evidence.upload.enabled", defaultValue = "true")
    private val uploadEnabled: Boolean,
    @ConfigProperty(name = "app.information-request.evidence.malware-scan.required", defaultValue = "false")
    private val scanRequired: Boolean,
    private val scanner: InformationRequestEvidenceMalwareScanner,
)
{
    fun requireUploadAvailable()
    {
        unavailableReason()?.let { reason ->
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_UNAVAILABLE, reason)
        }
    }

    fun uploadAvailable(): Boolean = unavailableReason() == null

    fun malwareScanRequired(): Boolean = scanRequired

    fun malwareScanningConfigured(): Boolean = scanner.engine() != null

    private fun unavailableReason(): String? =
        when
        {
            !uploadEnabled -> "Evidence upload is not enabled in this deployment"
            scanRequired && scanner.engine()?.productionEligible != true ->
                "Evidence upload needs a configured production malware scanner"
            else -> null
        }
}
