package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.user.AvatarContent
import com.docuhyphen.app.api.service.storage.ProfilePictureStorageService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.io.File
import java.util.*

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

        private val ALLOWED_EXTENSIONS: Map<String, String> = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
        )
    }

    @Transactional
    fun uploadAvatar(file: File, extension: String?)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("No authenticated user")

        val normalizedExtension = extension?.lowercase()?.trimStart('.')?.trim()
        if (normalizedExtension.isNullOrBlank() || !ALLOWED_EXTENSIONS.containsKey(normalizedExtension))
        {
            throw IllegalArgumentException(
                "Unsupported image type. Allowed types: ${
                    ALLOWED_EXTENSIONS.keys.joinToString(
                        ", "
                    )
                }"
            )
        }

        if (file.length() > MAX_AVATAR_SIZE_BYTES)
        {
            throw IllegalArgumentException("Image is too large. Maximum size is 5 MB")
        }

        val newKey = "avatars/${appUser.id}.$normalizedExtension"
        val previousKey = appUser.avatarStorageKey

        profilePictureStorageService.store(file, newKey)

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

    fun getAvatar(userId: UUID): AvatarContent?
    {
        authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("No authenticated user")

        val user = appUserService.getById(userId) ?: return null
        val key = user.avatarStorageKey ?: return null
        val bytes = profilePictureStorageService.load(key) ?: return null
        val extension = key.substringAfterLast('.', "")
        val contentType = ALLOWED_EXTENSIONS[extension] ?: "application/octet-stream"

        return AvatarContent(bytes, contentType)
    }

    @Transactional
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
