package com.docuhyphen.app.api.model.document

import java.io.File
import java.io.OutputStream

class DocumentVersionContent(
    private val handle: DocumentVersionReadHandle,
    val fileName: String,
) : AutoCloseable {
    val file: File get() = handle.file

    fun transferTo(output: OutputStream) {
        use { file.inputStream().use { input -> input.copyTo(output) } }
    }

    override fun close() = handle.close()
}
