package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Local-filesystem [AuditArchiveStorage], the default in dev/CI and the fallback when Secrets
 * Manager/S3 are not configured. Uses the same
 * local-directory approach so this phase's archive/verify round trip is fully exercisable without
 * AWS credentials.
 */
@ApplicationScoped
@Local
class LocalAuditArchiveStorage @Inject constructor(
    private val configService: AuditArchiveConfigService,
)
    : AuditArchiveStorage
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
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toList()
        }
    }

    private fun resolve(key: String): Path = Path.of(configService.getLocalDirectory()).resolve(key)
}
