package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceScanSettings
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceMalwareScanner
import com.docuhyphen.app.api.service.informationrequest.UnconfiguredInformationRequestEvidenceMalwareScanner
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Duration

@ApplicationScoped
class InformationRequestEvidenceScanProducer @Inject constructor(
    @ConfigProperty(name = "app.information-request.evidence.malware-scanner", defaultValue = "none")
    private val provider: String,

    @ConfigProperty(name = "app.information-request.evidence.scan.timeout", defaultValue = "PT30S")
    private val timeout: Duration,

    @ConfigProperty(name = "app.information-request.evidence.scan.maximum-signature-age", defaultValue = "PT24H")
    private val maximumSignatureAge: Duration,

    @ConfigProperty(name = "app.information-request.evidence.scan.reuse-window", defaultValue = "P30D")
    private val reuseWindow: Duration,

    @ConfigProperty(name = "app.information-request.evidence.scan.retry-after", defaultValue = "PT15M")
    private val retryAfter: Duration,

    @ConfigProperty(name = "app.information-request.evidence.scan.rescan-after", defaultValue = "P30D")
    private val rescanAfter: Duration,

    @ConfigProperty(name = "app.information-request.evidence.scan.batch-size", defaultValue = "50")
    private val batchSize: Int,
)
{
    @Produces
    @Singleton
    fun scanner(): InformationRequestEvidenceMalwareScanner =
        when (provider.trim().lowercase())
        {
            NO_SCANNER -> UnconfiguredInformationRequestEvidenceMalwareScanner
            else -> throw IllegalArgumentException("Unsupported evidence malware scanner: $provider")
        }

    @Produces
    @Singleton
    fun settings(): InformationRequestEvidenceScanSettings =
        InformationRequestEvidenceScanSettings(timeout, maximumSignatureAge, reuseWindow, retryAfter, rescanAfter, batchSize)

    private companion object
    {
        const val NO_SCANNER = "none"
    }
}
