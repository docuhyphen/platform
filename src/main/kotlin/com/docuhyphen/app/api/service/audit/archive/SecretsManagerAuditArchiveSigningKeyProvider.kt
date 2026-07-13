package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * Secrets-Manager-backed [AuditArchiveSigningKeyProvider]: the private key
 * material lives as a JSON secret (`{"keyId","privateKeyPem","publicKeyPem"}`) in the existing
 * AWS Secrets Manager rather than KMS or an HSM. The public key is
 * the public key exposed for independent signature verification ([activePublicKeyPem]).
 */
@ApplicationScoped
@Aws
class SecretsManagerAuditArchiveSigningKeyProvider @Inject constructor(
    private val configService: AuditArchiveConfigService,
    private val secretsManagerService: AwsSecretsManagerService,
)
    : AuditArchiveSigningKeyProvider
{
    companion object
    {
        private const val ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }

    @Serializable
    private data class SigningSecret(
        val keyId: String,
        val privateKeyPem: String,
        val publicKeyPem: String,
        val historicalPublicKeys: Map<String, String> = emptyMap(),
    )

    private val lock = ReentrantLock()

    @Volatile
    private var cached: Pair<SigningSecret, KeyPair>? = null

    override fun keyId(): String = loadOrFetch().first.keyId

    override fun sign(data: ByteArray): ByteArray
    {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(loadOrFetch().second.private)
        signature.update(data)
        return signature.sign()
    }

    override fun verify(data: ByteArray, signature: ByteArray, keyId: String): Boolean
    {
        val (secret, keyPair) = loadOrFetch()
        val publicKey = if (keyId == secret.keyId)
        {
            keyPair.public
        }
        else
        {
            val pem = secret.historicalPublicKeys[keyId] ?: return false
            KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(decodePem(pem)))
        }
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(data)
        return verifier.verify(signature)
    }

    override fun activePublicKeyPem(): String = loadOrFetch().first.publicKeyPem

    private fun loadOrFetch(): Pair<SigningSecret, KeyPair>
    {
        cached?.let { return it }

        lock.lock()
        try
        {
            cached?.let { return it }

            val secretString = secretsManagerService.getSecretString(
                configService.getSigningSecretId(),
                configService.getSigningRegion(),
            )
            val secret = Json.decodeFromString(SigningSecret.serializer(), secretString)
            val privateKey = KeyFactory.getInstance(ALGORITHM)
                .generatePrivate(PKCS8EncodedKeySpec(decodePem(secret.privateKeyPem)))
            val publicKey = KeyFactory.getInstance(ALGORITHM)
                .generatePublic(X509EncodedKeySpec(decodePem(secret.publicKeyPem)))

            val result = secret to KeyPair(publicKey, privateKey)
            cached = result
            return result
        }
        finally
        {
            lock.unlock()
        }
    }

    private fun decodePem(pem: String): ByteArray
    {
        val base64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        return Base64.getDecoder().decode(base64)
    }
}
