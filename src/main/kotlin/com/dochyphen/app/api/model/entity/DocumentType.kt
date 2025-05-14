package com.dochyphen.app.api.model.entity

enum class DocumentType
{
    PDF,
    DOCX,
    DOC,
    XLSX,
    XLS,
    PPTX,
    PPT,
    PNG,
    JPG;

    companion object
    {
        fun fromFileExtension(extension: String): DocumentType?
        {
            return when (extension.lowercase())
            {
                ".pdf" -> PDF
                ".docx" -> DOCX
                ".doc" -> DOC
                ".xlsx" -> XLSX
                ".xls" -> XLS
                ".pptx" -> PPTX
                ".ppt" -> PPT
                ".png" -> PNG
                ".jpg" -> JPG
                else -> null
            }
        }

        fun toFileExtension(documentType: DocumentType): String
        {
            return when (documentType)
            {
                PDF -> ".pdf"
                DOCX -> ".docx"
                DOC -> ".doc"
                XLSX -> ".xlsx"
                XLS -> ".xls"
                PPTX -> ".pptx"
                PPT -> ".ppt"
                PNG -> ".png"
                JPG -> ".jpg"
            }
        }
    }
}