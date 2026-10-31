package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@ApplicationScoped
@Local
class LocalAuditArchiveStorage @Inject constructor(
    private val configService: AuditArchiveConfigService,
) : AuditArchiveStorage
{
    override fun putObject(key: String, bytes: ByteArray)
    {
        val path = resolve(key)
        if (Files.exists(path))
        {
            throw AuditArchiveObjectAlreadyExistsException(key)
        }
        Files.createDirectories(path.parent)
        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW).use { it.write(bytes) }
    }

    override fun getObject(key: String): ByteArray
    {
        val path = resolve(key)
        if (!Files.exists(path))
        {
            throw AuditArchiveObjectNotFoundException(key)
        }
        return Files.readAllBytes(path)
    }

    override fun objectExists(key: String): Boolean = Files.exists(resolve(key))

    override fun listKeysWithPrefix(prefix: String): List<String>
    {
        val root = Path.of(configService.getLocalDirectory())
        val prefixPath = resolve(prefix)
        if (!Files.isDirectory(prefixPath))
        {
            return emptyList()
        }
        Files.walk(prefixPath).use { paths ->
            return paths
                .filter { Files.isRegularFile(it) }
                .map { decodeKey(root.relativize(it).toString().replace('\\', '/')) }
                .toList()
        }
    }

    private fun resolve(key: String): Path = Path.of(configService.getLocalDirectory()).resolve(encodeKey(key))

    private fun encodeKey(key: String): String = buildString {
        key.forEach { character ->
            if (character in FILE_NAME_RESERVED_CHARACTERS) {
                append('%').append("%02X".format(character.code))
            } else {
                append(character)
            }
        }
    }

    private fun decodeKey(encoded: String): String = buildString {
        var index = 0
        while (index < encoded.length) {
            val character = encoded[index]
            if (character == '%' && index + 2 < encoded.length) {
                append(encoded.substring(index + 1, index + 3).toInt(16).toChar())
                index += 3
            } else {
                append(character)
                index++
            }
        }
    }

    companion object {
        private const val FILE_NAME_RESERVED_CHARACTERS = "%<>:\"\\|?*"
    }
}
