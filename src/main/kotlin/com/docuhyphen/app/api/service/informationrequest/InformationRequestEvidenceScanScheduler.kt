package com.docuhyphen.app.api.service.informationrequest

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestEvidenceScanScheduler @Inject constructor(
    private val assessmentService: InformationRequestEvidenceMalwareAssessmentService,
)
{
    @Scheduled(
        every = "\${app.information-request.evidence.scan.every:1m}",
        identity = "information-request-evidence-scan",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        if (!assessmentService.scannerConfigured()) return

        assessmentService.dueForScan().forEach { evidenceVersionId ->
            try
            {
                assessmentService.assess(evidenceVersionId)
            }
            catch (e: Exception)
            {
                logger.error(
                    "{}: evidenceVersionId={} reason={}",
                    InformationRequestEvidenceMalwareAssessmentService.SCAN_FAILED_MARKER,
                    evidenceVersionId,
                    e.javaClass.simpleName,
                )
            }
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestEvidenceScanScheduler::class.java)
    }
}
