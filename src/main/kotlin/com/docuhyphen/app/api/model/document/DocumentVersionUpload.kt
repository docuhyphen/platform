package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.io.File

data class DocumentVersionUpload(
    val fileName: String,
    val file: File,
    val creator: PrincipalRef,
    val encryptionMode: DocumentEncryptionMode,
    val expectedDigest: DocumentVersionContentDigest,
)
