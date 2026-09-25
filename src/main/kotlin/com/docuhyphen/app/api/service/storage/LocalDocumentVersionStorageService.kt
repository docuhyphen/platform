package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.exception.DocumentVersionObjectKeyInUseException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigest
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.qualifier.Local
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption

@Local
@ApplicationScoped
class LocalDocumentVersionStorageService @Inject constructor(
    @ConfigProperty(name = "document.version.storage.local.root-directory")
    private val rootDirectory: String,
) : DocumentVersionStorageService
{
    override fun writeNewVersion(
        key: String,
        file: File,
        expected: DocumentVersionContentDigest,
    ): ObjectStoreDocumentVersionLocator
    {
        val locator = ObjectStoreDocumentVersionLocator(key)
        val target = contentFile(locator).toPath()

        target.parent?.let { Files.createDirectories(it) }

        val written = try
        {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                file.inputStream().use { input -> DocumentVersionContentDigests.copy(input, output) }
            }
        }
        catch (_: FileAlreadyExistsException)
        {
            throw DocumentVersionObjectKeyInUseException(key)
        }
        catch (exception: Exception)
        {
            Files.deleteIfExists(target)
            throw exception
        }

        if (written != expected)
        {
            Files.deleteIfExists(target)
            throw DocumentVersionContentDigestMismatchException(key)
        }

        return locator
    }

    override fun openVersion(locator: ObjectStoreDocumentVersionLocator): File
    {
        val target = contentFile(locator)

        if (!target.isFile)
        {
            throw DocumentVersionContentNotFoundException(locator.value)
        }

        return target
    }

    private fun contentFile(locator: ObjectStoreDocumentVersionLocator): File =
        File(rootDirectory, locator.value)
}
