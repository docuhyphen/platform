package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentVersion

object DocumentVersionStorageLocatorMapper
{
    fun read(version: DocumentVersion): DocumentVersionStorageLocator =
        DocumentVersionStorageLocators.resolve(version.storageLocatorKind, version.storageLocator)
}
