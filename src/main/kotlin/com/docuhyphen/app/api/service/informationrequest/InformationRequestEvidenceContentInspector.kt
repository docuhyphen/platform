package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceInspectionFacts
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceMediaTypes
import jakarta.enterprise.context.ApplicationScoped
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException

@ApplicationScoped
class InformationRequestEvidenceContentInspector
{
    fun inspect(file: File): InformationRequestEvidenceInspectionFacts
    {
        val detected = InformationRequestEvidenceMediaTypes.detect(file)
        if (detected != PDF)
        {
            return InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = false, corrupt = false)
        }

        return try
        {
            Loader.loadPDF(file).use { document ->
                InformationRequestEvidenceInspectionFacts(detected, document.numberOfPages, encrypted = false, corrupt = false)
            }
        }
        catch (_: InvalidPasswordException)
        {
            InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = true, corrupt = false)
        }
        catch (_: IOException)
        {
            InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = false, corrupt = true)
        }
    }

    private companion object
    {
        const val PDF = "application/pdf"
    }
}
