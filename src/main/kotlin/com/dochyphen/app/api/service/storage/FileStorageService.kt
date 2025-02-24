package com.dochyphen.app.api.service.storage

import java.io.File

interface FileStorageService
{
    fun uploadDocument(file: File, key: String): String
    fun downloadDocument(key: String): File
    fun downloadDocumentsAsZip(keys: List<String>): File
}