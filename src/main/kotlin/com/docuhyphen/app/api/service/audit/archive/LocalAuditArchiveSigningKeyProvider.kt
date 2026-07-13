package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * Local-filesystem [AuditArchiveSigningKeyProvider]: bootstraps a single RSA-2048 key pair on
 * first use and persists it as PEM under [AuditArchiveConfigService.getLocalSigningDirectory].
 * This is the default so the archiver/verifier round trip works out of the box in dev/CI; a real
 * deployment should set `app.audit.archive.signing.provider=aws` to use
 * [SecretsManagerAuditArchiveSigningKeyProvider] instead.
 */
@ApplicationScoped
@Local
class LocalAuditArchiveSigningKeyProvider @Inject constructor(
    private val configService: AuditArchiveConfigService,
)
    : AuditArchiveSigningKeyProvider
{
    companion object
    {
        private const val ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val DEFAULT_KEY_ID = "local-dev-key-1"
    }

    private val lock = ReentrantLock()

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    override fun keyId(): String = activeKeyId()

    override fun sign(data: ByteArray): ByteArray
    {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(loadOrCreateKeyPair().private)
        signature.update(data)
        return signature.sign()
    }

    override fun verify(data: ByteArray, signature: ByteArray, keyId: String): Boolean
    {
        val publicKey = if (keyId == activeKeyId())
        {
            loadOrCreateKeyPair().public
        }
        else
        {
            val publicPath = Path.of(configService.getLocalSigningDirectory()).resolve("$keyId.public.pem")
            if (!Files.exists(publicPath)) return false
            readPublicKey(Files.readString(publicPath))
        }
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(data)
        return verifier.verify(signature)
    }

    override fun activePublicKeyPem(): String = toPem("PUBLIC KEY", loadOrCreateKeyPair().public.encoded)

    private fun loadOrCreateKeyPair(): KeyPair
    {
        cachedKeyPair?.let { return it }

        lock.lock()
        try
        {
            cachedKeyPair?.let { return it }

            val dir = Path.of(configService.getLocalSigningDirectory())
            Files.createDirectories(dir)
            val keyId = activeKeyId()
            val privatePath = dir.resolve("$keyId.private.pem")
            val publicPath = dir.resolve("$keyId.public.pem")

            val keyPair = if (Files.exists(privatePath) && Files.exists(publicPath))
            {
                val privateKey = readPrivateKey(Files.readString(privatePath))
                val publicKey = readPublicKey(Files.readString(publicPath))
                KeyPair(publicKey, privateKey)
            }
            else
            {
                val generator = KeyPairGenerator.getInstance(ALGORITHM)
                generator.initialize(2048)
                val generated = generator.generateKeyPair()
                Files.writeString(privatePath, toPem("PRIVATE KEY", generated.private.encoded))
                Files.writeString(publicPath, toPem("PUBLIC KEY", generated.public.encoded))
                generated
            }

            cachedKeyPair = keyPair
            return keyPair
        }
        finally
        {
            lock.unlock()
        }
    }

    private fun activeKeyId(): String = runCatching { configService.getSigningKeyId() }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_KEY_ID

    private fun readPrivateKey(pem: String): PrivateKey
    {
        val bytes = decodePem(pem)
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun readPublicKey(pem: String): PublicKey
    {
        val bytes = decodePem(pem)
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun toPem(label: String, encoded: ByteArray): String
    {
        val base64 = Base64.getEncoder().encodeToString(encoded)
        val chunked = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$chunked\n-----END $label-----\n"
    }

    private fun decodePem(pem: String): ByteArray
    {
        val base64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        return Base64.getDecoder().decode(base64)
    }
}
