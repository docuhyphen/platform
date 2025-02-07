package com.dochyphen.app.api.service

import java.io.File

interface FileStorageService
{
    fun uploadDocument(file: File, key: String): String
    fun downloadDocument(key: String): File
}