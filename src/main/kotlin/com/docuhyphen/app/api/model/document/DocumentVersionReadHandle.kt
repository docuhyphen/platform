package com.docuhyphen.app.api.model.document

import java.io.File
import java.nio.file.Path

class DocumentVersionReadHandle private constructor(
    val file: File,
    private val temporaryDirectory: Path?,
) : AutoCloseable {
    override fun close() {
        temporaryDirectory?.toFile()?.deleteRecursively()
    }

    companion object {
        fun storedFile(file: File): DocumentVersionReadHandle = DocumentVersionReadHandle(file, null)

        fun temporaryCopy(file: File, directory: Path): DocumentVersionReadHandle =
            DocumentVersionReadHandle(file, directory)
    }
}
