package com.docuhyphen.app.api.service.informationrequest.evidence

import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSObjectKey
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessRead
import org.apache.pdfbox.pdfparser.PDFParser
import org.apache.pdfbox.pdfparser.XrefTrailerResolver

class InformationRequestEvidencePdfParser(
    source: RandomAccessRead,
    private val usage: InformationRequestEvidenceDocumentUsage,
) : PDFParser(source, "", null, null, IOUtils.createMemoryOnlyStreamCache()) {
    init {
        xrefTrailerResolver = CountedCrossReferences(usage)
    }

    override fun parseObjectDynamically(objKey: COSObjectKey, requireExistingNotCompressedObj: Boolean): COSBase? {
        val parsed = super.parseObjectDynamically(objKey, requireExistingNotCompressedObj)
        if (parsed is COSStream) {
            usage.chargeStreamObjects(objKey, parsed.getLong(COSName.N, 0))
        }
        return parsed
    }

    private class CountedCrossReferences(private val usage: InformationRequestEvidenceDocumentUsage) :
        XrefTrailerResolver() {
        override fun nextXrefObj(startBytePos: Long, type: XRefType) {
            usage.chargeCrossReferenceSection()
            super.nextXrefObj(startBytePos, type)
        }

        override fun setXRef(objKey: COSObjectKey, offset: Long) {
            usage.chargeCrossReferenceEntry()
            super.setXRef(objKey, offset)
        }
    }
}
