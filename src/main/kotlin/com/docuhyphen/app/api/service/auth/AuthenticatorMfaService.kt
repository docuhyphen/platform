package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.dto.AuthenticatorEnrollmentDto
import com.docuhyphen.app.api.model.dto.MfaConfigurationDto
import com.docuhyphen.app.api.model.AuthenticatorMfaDtoMapper
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthenticatorEnrollment
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.GOOGLE_AUTHENTICATOR
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.MICROSOFT_AUTHENTICATOR
import com.docuhyphen.app.api.repository.AuthenticatorEnrollmentRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
class AuthenticatorMfaService(
    private val enrollmentRepository: AuthenticatorEnrollmentRepository,
    private val appUserService: AppUserService,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private const val ISSUER = "DocuHyphen"
        private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        private const val CODE_PERIOD_SECONDS = 30L
        private const val CODE_DIGITS = 6
        private val secureRandom = SecureRandom()
    }

    fun getConfiguration(appUser: AppUser): MfaConfigurationDto =
        AuthenticatorMfaDtoMapper.toConfigurationDto(appUser)

    @Transactional
    fun startEnrollment(appUser: AppUser, providerValue: String?): AuthenticatorEnrollmentDto
    {
        val provider = runCatching { MultifactorAuthenticationType.valueOf(providerValue.orEmpty()) }
            .getOrNull()
            ?.takeIf { it.isAuthenticator() }
            ?: throw IllegalArgumentException("Choose Google Authenticator or Microsoft Authenticator.")

        enrollmentRepository.deleteForUser(appUser.id)
        val secret = generateSecret()
        val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        val enrollment = AuthenticatorEnrollment().apply {
            this.appUser = appUser
            this.provider = provider
            this.secretEncrypted = encrypt(secret)
            this.expiresAt = Timestamp.from(expiresAt)
        }
        enrollmentRepository.save(enrollment)

        return AuthenticatorMfaDtoMapper.toEnrollmentDto(
            enrollment = enrollment,
            secret = secret,
            otpauthUri = buildOtpAuthUri(appUser.email, secret),
        )
    }

    @Transactional
    fun completeEnrollment(
        appUser: AppUser,
        enrollmentId: UUID,
        code: String?,
        emailFallbackEnabled: Boolean,
    ): MfaConfigurationDto
    {
        val enrollment = enrollmentRepository.findForUser(enrollmentId, appUser.id)
            ?: throw IllegalArgumentException("Authenticator enrollment was not found.")
        if (enrollment.expiresAt.before(Timestamp.from(Instant.now())))
        {
            enrollmentRepository.delete(enrollment)
            throw IllegalArgumentException("Authenticator enrollment has expired. Start again.")
        }

        val secret = decrypt(enrollment.secretEncrypted)
        if (!verifyCode(secret, code))
        {
            throw IllegalArgumentException("The authenticator code is not valid.")
        }

        appUser.mfaType = enrollment.provider
        appUser.authenticatorSecretEncrypted = enrollment.secretEncrypted
        appUser.emailMfaFallbackEnabled = emailFallbackEnabled
        appUserService.update(appUser)
        enrollmentRepository.delete(enrollment)
        return getConfiguration(appUser)
    }

    @Transactional
    fun updateEmailFallback(appUser: AppUser, enabled: Boolean): MfaConfigurationDto
    {
        if (!appUser.mfaType.isAuthenticator())
        {
            throw IllegalArgumentException("Email fallback can only be configured for authenticator-app MFA.")
        }
        appUser.emailMfaFallbackEnabled = enabled
        appUserService.update(appUser)
        return getConfiguration(appUser)
    }

    @Transactional
    fun removeAuthenticator(appUser: AppUser): MfaConfigurationDto
    {
        appUser.mfaType = MultifactorAuthenticationType.EMAIL
        appUser.authenticatorSecretEncrypted = null
        appUser.emailMfaFallbackEnabled = false
        appUserService.update(appUser)
        enrollmentRepository.deleteForUser(appUser.id)
        return getConfiguration(appUser)
    }

    fun verifyUserCode(appUser: AppUser, code: String?): Boolean
    {
        val encryptedSecret = appUser.authenticatorSecretEncrypted ?: return false
        return verifyCode(decrypt(encryptedSecret), code)
    }

    private fun verifyCode(secret: String, code: String?): Boolean
    {
        val normalizedCode = code?.trim()?.takeIf { it.matches(Regex("\\d{6}")) } ?: return false
        val currentCounter = Instant.now().epochSecond / CODE_PERIOD_SECONDS
        return (-1L..1L).any { offset ->
            MessageDigest.isEqual(
                generateCode(secret, currentCounter + offset).toByteArray(StandardCharsets.US_ASCII),
                normalizedCode.toByteArray(StandardCharsets.US_ASCII),
            )
        }
    }

    internal fun generateCode(secret: String, counter: Long): String
    {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decodeBase32(secret), "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = 1_000_000
        return (binary % modulus).toString().padStart(CODE_DIGITS, '0')
    }

    private fun generateSecret(): String
    {
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    private fun buildOtpAuthUri(email: String, secret: String): String
    {
        val label = urlEncode("$ISSUER:$email")
        val issuer = urlEncode(ISSUER)
        return "otpauth://totp/$label?secret=$secret&issuer=$issuer&algorithm=SHA1&digits=$CODE_DIGITS&period=$CODE_PERIOD_SECONDS"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun encrypt(value: String): String
    {
        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKeySpec(), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(nonce + encrypted)
    }

    private fun decrypt(value: String): String
    {
        val payload = Base64.getDecoder().decode(value)
        require(payload.size > 12) { "Invalid encrypted authenticator secret." }
        val nonce = payload.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKeySpec(), GCMParameterSpec(128, nonce))
        return String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }

    private fun encryptionKeySpec(): SecretKeySpec
    {
        val encryptionKey = "docuhyphen-authenticator-mfa:${configurationService.getJwtSecret()}"
        return SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(encryptionKey.toByteArray(StandardCharsets.UTF_8)),
            "AES",
        )
    }

    private fun encodeBase32(bytes: ByteArray): String
    {
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5)
            {
                result.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) result.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 31])
        return result.toString()
    }

    private fun decodeBase32(value: String): ByteArray
    {
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        value.uppercase().filterNot { it == '=' || it.isWhitespace() }.forEach { character ->
            val index = BASE32_ALPHABET.indexOf(character)
            require(index >= 0) { "Invalid authenticator secret." }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8)
            {
                output.add(((buffer shr (bitsLeft - 8)) and 0xff).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }
}
