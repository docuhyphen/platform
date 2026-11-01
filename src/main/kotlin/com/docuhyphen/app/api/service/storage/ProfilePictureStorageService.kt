package com.docuhyphen.app.api.service.storage

import java.io.File

interface ProfilePictureStorageService
{
    fun store(file: File, key: String)
    fun load(key: String): ByteArray?
    fun delete(key: String)
}

