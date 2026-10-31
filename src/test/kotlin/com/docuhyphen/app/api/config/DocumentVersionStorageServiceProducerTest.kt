package com.docuhyphen.app.api.config

import com.docuhyphen.app.api.service.storage.AwsS3DocumentVersionStorageService
import com.docuhyphen.app.api.service.storage.LocalDocumentVersionStorageService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.nio.file.Path

class DocumentVersionStorageServiceProducerTest {
    @TempDir
    lateinit var root: Path

    private val awsStorage = AwsS3DocumentVersionStorageService("version-bucket", mock())

    @Test
    fun `the local version store is selected by its configured name`() {
        val localStorage = LocalDocumentVersionStorageService(root.toString())

        assertSame(localStorage, DocumentVersionStorageServiceProducer("local", localStorage, awsStorage).produce())
    }

    @Test
    fun `the object store is selected by its configured name`() {
        val localStorage = LocalDocumentVersionStorageService(root.toString())

        assertSame(awsStorage, DocumentVersionStorageServiceProducer("aws", localStorage, awsStorage).produce())
    }

    @Test
    fun `an unknown version store name is refused`() {
        val localStorage = LocalDocumentVersionStorageService(root.toString())

        assertThrows(IllegalArgumentException::class.java)
        {
            DocumentVersionStorageServiceProducer("remote", localStorage, awsStorage).produce()
        }
    }

    @Test
    fun `the object store client is built for the configured region and closed when it is disposed`() {
        val producer = DocumentVersionS3ClientProducer("af-south-1")

        producer.s3Client().use { client ->
            assertEquals(Region.AF_SOUTH_1, client.serviceClientConfiguration().region())
        }

        val disposed = mock<S3Client>()
        producer.close(disposed)
        verify(disposed).close()
    }
}
