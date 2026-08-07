package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.storage.ProfilePictureStorageService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.io.File

/**
 * Handles the storage lifecycle of a user's profile picture (avatar): uploading a new
 * image, streaming the current image back, and removing it. The binary is kept in the
 * dedicated profile-picture storage location while the owning user row only holds the
 * storage key. Persistence of the user row is delegated to [AppUserService] so this
 * service never touches the user repository directly.
 */
@RequestScoped
class AppUserAvatarService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val profilePictureStorageService: ProfilePictureStorageService,
)
{
    companion object
    {
        private const val MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024

        // Image formats accepted for an avatar, mapped to the content type used when
        // streaming the stored image back to the browser.
        private val ALLOWED_EXTENSIONS: Map<String, String> = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
        )
    }

    data class AvatarContent(val bytes: ByteArray, val contentType: String)
    {
        override fun equals(other: Any?): Boolean
        {
            if (this === other) return true
            if (other !is AvatarContent) return false
            return contentType == other.contentType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
    }

    fun uploadAvatar(file: File, extension: String?)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("No authenticated user")

        val normalizedExtension = extension?.lowercase()?.trimStart('.')?.trim()
        if (normalizedExtension.isNullOrBlank() || !ALLOWED_EXTENSIONS.containsKey(normalizedExtension))
        {
            throw IllegalArgumentException("Unsupported image type. Allowed types: ${ALLOWED_EXTENSIONS.keys.joinToString(", ")}")
        }

        if (file.length() > MAX_AVATAR_SIZE_BYTES)
        {
            throw IllegalArgumentException("Image is too large. Maximum size is 5 MB")
        }

        val newKey = "avatars/${appUser.id}.$normalizedExtension"
        val previousKey = appUser.avatarStorageKey

        profilePictureStorageService.store(file, newKey)

        // Remove any previously stored image whose extension differs, otherwise it is
        // overwritten in place and there is nothing to clean up.
        if (previousKey != null && previousKey != newKey)
        {
            runCatching { profilePictureStorageService.delete(previousKey) }
        }

        appUser.avatarStorageKey = newKey
        appUserService.update(appUser)
    }

    fun getAvatar(): AvatarContent?
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("No authenticated user")

        val key = appUser.avatarStorageKey ?: return null
        val bytes = profilePictureStorageService.load(key) ?: return null
        val extension = key.substringAfterLast('.', "")
        val contentType = ALLOWED_EXTENSIONS[extension] ?: "application/octet-stream"

        return AvatarContent(bytes, contentType)
    }

    fun deleteAvatar()
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("No authenticated user")

        val key = appUser.avatarStorageKey ?: return

        runCatching { profilePictureStorageService.delete(key) }

        appUser.avatarStorageKey = null
        appUserService.update(appUser)
    }
}


