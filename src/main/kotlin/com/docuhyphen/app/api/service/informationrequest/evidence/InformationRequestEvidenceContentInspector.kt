package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceInspectionFacts
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMediaTypes
import jakarta.enterprise.context.ApplicationScoped
import java.io.File
import java.util.concurrent.Semaphore

@ApplicationScoped
class InformationRequestEvidenceContentInspector(
    private val archives: InformationRequestEvidenceArchiveInspection = InformationRequestEvidenceArchiveInspection(),
    private val compoundDocuments: InformationRequestEvidenceCompoundDocumentInspection =
        InformationRequestEvidenceCompoundDocumentInspection(),
    private val images: InformationRequestEvidenceImageInspection = InformationRequestEvidenceImageInspection(),
    private val documents: InformationRequestEvidenceDocumentInspection = InformationRequestEvidenceDocumentInspection(),
    private val inspections: Semaphore = Semaphore(MAXIMUM_CONCURRENT_INSPECTIONS, true),
)
{
    fun inspect(file: File): InformationRequestEvidenceInspectionFacts
    {
        val detected = InformationRequestEvidenceMediaTypes.detect(file)
        inspections.acquireUninterruptibly()
        try {
            return inspectAlone(file, detected)
        } finally
        {
            inspections.release()
        }
    }

    private fun inspectAlone(file: File, detected: String): InformationRequestEvidenceInspectionFacts {
        if (detected == PDF) {
            return documents.inspect(file, detected)
        }
        if (images.recognizes(detected)) {
            return images.inspect(file, detected)
        }

        val header = file.inputStream().use { input -> input.readNBytes(HEADER_BYTES) }
        return when {
            compoundDocuments.recognizes(header) -> compoundDocuments.inspect(file, detected)
            archives.recognizes(header) -> archives.inspect(file, detected)
            else -> InformationRequestEvidenceInspectionFacts(
                detected,
                pageCount = null,
                encrypted = false,
                corrupt = false
            )
        }
    }

    private companion object
    {
        const val PDF = "application/pdf"
        const val HEADER_BYTES = 8
        const val MAXIMUM_CONCURRENT_INSPECTIONS = 2
    }
}
