package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceDocumentLimits
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceInspectionFacts
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.collections.ArrayDeque

class InformationRequestEvidenceDocumentInspection(
    private val limits: InformationRequestEvidenceDocumentLimits = InformationRequestEvidenceDocumentLimits(),
) {
    fun inspect(file: File, detected: String): InformationRequestEvidenceInspectionFacts {
        val usage = InformationRequestEvidenceDocumentUsage(limits)
        val pages = try {
            InformationRequestEvidencePdfDecoding.metered(usage) {
                RandomAccessReadBufferedFile(file).use { source ->
                    InformationRequestEvidencePdfParser(source, usage).parse().use(::pagesHeld)
                }
            }
        } catch (_: InvalidPasswordException) {
            return if (usage.exceeded) corrupt(detected) else encrypted(detected)
        } catch (_: IOException) {
            return corrupt(detected)
        } catch (_: RuntimeException) {
            return corrupt(detected)
        }
        if (usage.exceeded || pages == null) {
            return corrupt(detected)
        }
        return InformationRequestEvidenceInspectionFacts(detected, pages, encrypted = false, corrupt = false)
    }

    private fun pagesHeld(document: PDDocument): Int? {
        val root = document.documentCatalog.cosObject.getCOSDictionary(COSName.PAGES) ?: return null
        val visited = Collections.newSetFromMap(IdentityHashMap<COSDictionary, Boolean>())
        val pending = ArrayDeque(listOf(root))
        var pages = 0
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (!visited.add(node)) continue
            val kids = node.getCOSArray(COSName.KIDS)
            if (kids == null) {
                if (node.getCOSName(COSName.TYPE) != COSName.PAGES) pages += 1
                continue
            }
            (0 until kids.size()).mapNotNull { kids.getObject(it) as? COSDictionary }.forEach(pending::addLast)
        }
        return pages
    }

    private fun encrypted(detected: String) =
        InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = true, corrupt = false)

    private fun corrupt(detected: String) =
        InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = false, corrupt = true)
}
