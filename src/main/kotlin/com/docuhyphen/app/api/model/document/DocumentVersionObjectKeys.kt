package com.docuhyphen.app.api.model.document

import java.util.UUID

object DocumentVersionObjectKeys
{
    private const val KEY_PREFIX = "document-versions"
    private const val FALLBACK_SEGMENT = "version-content"
    private val UNSUPPORTED_CHARACTER = Regex("[^A-Za-z0-9._-]")
    private val NAMING_CHARACTER = Regex("[A-Za-z0-9]")

    fun allocate(documentId: UUID, versionId: UUID, fileName: String): ObjectStoreDocumentVersionLocator =
        ObjectStoreDocumentVersionLocator("$KEY_PREFIX/$documentId/$versionId/${segmentFor(fileName)}")

    private fun segmentFor(fileName: String): String
    {
        val reduced = UNSUPPORTED_CHARACTER.replace(fileName.trim(), "_")

        return if (NAMING_CHARACTER.containsMatchIn(reduced)) reduced else FALLBACK_SEGMENT
    }
}
