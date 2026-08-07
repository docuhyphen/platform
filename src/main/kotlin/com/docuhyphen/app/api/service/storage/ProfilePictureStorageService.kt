package com.docuhyphen.app.api.service.storage

import java.io.File

/**
 * Stores and retrieves user profile pictures (avatars). Backed by a dedicated
 * storage location that is separate from document and thumbnail storage so that
 * avatar objects can be managed and secured independently.
 */
interface ProfilePictureStorageService
{
    fun store(file: File, key: String)
    fun load(key: String): ByteArray?
    fun delete(key: String)
}

