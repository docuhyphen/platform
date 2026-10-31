package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceDocumentLimits
import org.apache.pdfbox.cos.COSObjectKey

class InformationRequestEvidenceDocumentUsage(private val limits: InformationRequestEvidenceDocumentLimits) {
    var exceeded: Boolean = false
        private set

    private var decodedBytes = 0L
    private var crossReferenceEntries = 0L
    private var crossReferenceSections = 0L
    private var streamObjects = 0L
    private val countedStreams = HashSet<COSObjectKey>()

    fun chargeDecodedBytes(bytes: Long) {
        decodedBytes += bytes
        refuseWhen(decodedBytes > limits.maximumDecodedBytes)
    }

    fun chargeCrossReferenceEntry() {
        refuseWhen(++crossReferenceEntries > limits.maximumCrossReferenceEntries)
    }

    fun chargeCrossReferenceSection() {
        refuseWhen(++crossReferenceSections > limits.maximumCrossReferenceSections)
    }

    fun chargeStreamObjects(stream: COSObjectKey, declaredObjects: Long) {
        if (countedStreams.add(stream)) {
            streamObjects += maxOf(0L, declaredObjects)
        }
        refuseWhen(streamObjects > limits.maximumStreamObjects)
    }

    fun refuseImageDecoding(): Nothing = refuse()

    private fun refuseWhen(overLimit: Boolean) {
        if (exceeded || overLimit) {
            refuse()
        }
    }

    private fun refuse(): Nothing {
        exceeded = true
        throw InformationRequestEvidenceDocumentLimitException()
    }
}

internal class InformationRequestEvidenceDocumentLimitException :
    RuntimeException("The document needs more resources than its inspection allows", null, false, false)
