package com.docuhyphen.app.api.model.document

enum class DocumentVersionStorageProvider
{
    OBJECT_STORE,
}

enum class DocumentVersionLocatorKind
{
    OBJECT_KEY,
}

sealed interface DocumentVersionStorageLocator
{
    val provider: DocumentVersionStorageProvider
    val kind: DocumentVersionLocatorKind
    val value: String
}

data class ObjectStoreDocumentVersionLocator(
    override val value: String,
) : DocumentVersionStorageLocator
{
    override val provider = DocumentVersionStorageProvider.OBJECT_STORE

    override val kind = DocumentVersionLocatorKind.OBJECT_KEY

    init
    {
        require(value.isNotBlank()) { "A stored document version locator must not be blank" }
        require(value == value.trim()) { "An object key must not be surrounded by whitespace" }
        require(!value.startsWith(KEY_SEPARATOR)) { "An object key must not start with a separator" }
        require(!value.endsWith(KEY_SEPARATOR)) { "An object key must not end with a separator" }
        require(!value.contains(WINDOWS_SEPARATOR)) { "An object key must not contain a filesystem separator" }
        require(!value.contains(DRIVE_MARKER)) { "An object key must not name a filesystem drive" }

        val segments = value.split(KEY_SEPARATOR)
        require(segments.none { it.isEmpty() }) { "An object key must not contain an empty segment" }
        require(segments.none { it == CURRENT_SEGMENT || it == PARENT_SEGMENT })
        {
            "An object key must not contain a relative traversal segment"
        }
    }

    private companion object
    {
        const val KEY_SEPARATOR = "/"
        const val WINDOWS_SEPARATOR = "\\"
        const val DRIVE_MARKER = ":"
        const val CURRENT_SEGMENT = "."
        const val PARENT_SEGMENT = ".."
    }
}

object DocumentVersionStorageLocators
{
    fun resolve(kind: DocumentVersionLocatorKind, value: String): DocumentVersionStorageLocator =
        when (kind)
        {
            DocumentVersionLocatorKind.OBJECT_KEY -> ObjectStoreDocumentVersionLocator(value)
        }
}
