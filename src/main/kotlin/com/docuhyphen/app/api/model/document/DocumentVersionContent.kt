package com.docuhyphen.app.api.model.document

import java.io.File

data class DocumentVersionContent(
    val file: File,
    val fileName: String,
)
