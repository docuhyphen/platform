package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.whenever
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class DocumentVersionStoragePermissionContractTest {
    @TempDir
    lateinit var uploads: Path

    private val template: List<String> = Files.readAllLines(Path.of("infra", "cloudformation.yml"))

    @Test
    fun `the task role grants every object store action that version storage performs on its bucket`() {
        val bucket = versionStorageBucket()
        val granted = taskRoleGrants()

        val missing = actionsPerformedByVersionStorage(bucket).filterNot { required -> grants(granted, required) }

        assertTrue(missing.isEmpty()) { "ECSTaskRole in infra/cloudformation.yml does not grant $missing" }
    }

    @Test
    fun `version storage disposes of every stored version and delete marker, which needs version permissions`() {
        val required = actionsPerformedByVersionStorage(versionStorageBucket()).map { it.first }.toSet()

        assertTrue("s3:ListBucketVersions" in required) { "$required" }
        assertTrue("s3:DeleteObjectVersion" in required) { "$required" }
    }

    @Test
    fun `version storage resolves its bucket from the variables the task definition sets`() {
        val properties = Properties().apply {
            DocumentVersionStoragePermissionContractTest::class.java.getResourceAsStream("/application.properties")
                .use { stream -> load(requireNotNull(stream) { "application.properties is not on the classpath" }) }
        }

        assertEquals(
            "\${$VERSION_BUCKET_VARIABLE:\${file.storage.aws.bucket}}",
            properties.getProperty("document.version.storage.aws.bucket"),
        )
        assertTrue(properties.getProperty("file.storage.aws.bucket").startsWith("\${$FILE_BUCKET_VARIABLE:"))
        assertEquals("DocumentsBucket", versionStorageBucket())
    }

    private fun actionsPerformedByVersionStorage(bucket: String): Set<Pair<String, String>> {
        val client = mock<S3Client>()
        whenever(client.getObject(any<GetObjectRequest>(), any<ResponseTransformer<GetObjectResponse, Any>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<ResponseTransformer<GetObjectResponse, Any>>(1).transform(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(ByteArrayInputStream("stored record".toByteArray())),
                )
            }
        whenever(client.listObjectVersions(any<ListObjectVersionsRequest>())).thenReturn(
            ListObjectVersionsResponse.builder()
                .versions(ObjectVersion.builder().key(KEY).versionId("version-1").build())
                .deleteMarkers(DeleteMarkerEntry.builder().key(KEY).versionId("marker-1").build())
                .isTruncated(false)
                .build(),
        )
        val storage = AwsS3DocumentVersionStorageService("documents", client)
        val source = Files.createTempFile(uploads, "record", ".pdf").toFile().apply { writeText("stored record") }

        storage.writeNewVersion(KEY, source, DocumentVersionContentDigests.of(source))
        storage.openVersion(ObjectStoreDocumentVersionLocator(KEY)).close()
        storage.deleteVersion(ObjectStoreDocumentVersionLocator(KEY))

        return mockingDetails(client).invocations
            .map { invocation -> requiredGrant(invocation.arguments.first(), bucket) }
            .toSet()
    }

    private fun requiredGrant(request: Any, bucket: String): Pair<String, String> =
        when (request) {
            is PutObjectRequest -> "s3:PutObject" to "$bucket/*"
            is GetObjectRequest -> "s3:GetObject" to "$bucket/*"
            is ListObjectVersionsRequest -> "s3:ListBucketVersions" to bucket
            is DeleteObjectRequest ->
                (if (request.versionId() == null) "s3:DeleteObject" else "s3:DeleteObjectVersion") to "$bucket/*"

            else -> fail("No IAM action is mapped for ${request::class.simpleName}; add it here and to ECSTaskRole")
        }

    private fun grants(granted: Set<Pair<String, String>>, required: Pair<String, String>): Boolean =
        granted.any { (action, resource) ->
            action in setOf(required.first, "s3:*", "*") && resource in setOf(required.second, "*")
        }

    private fun versionStorageBucket(): String {
        val reference = environmentValue(VERSION_BUCKET_VARIABLE) ?: environmentValue(FILE_BUCKET_VARIABLE)
        val bucket = reference?.let { REFERENCE.matchEntire(it)?.groupValues?.get(1) }
        return requireNotNull(bucket) { "The task definition sets no version storage bucket reference: $reference" }
    }

    private fun environmentValue(name: String): String? {
        val index = template.indexOfFirst { it.trim() == "- Name: $name" }
        return if (index < 0) null else template[index + 1].trim().removePrefix("Value:").trim()
    }

    private fun taskRoleGrants(): Set<Pair<String, String>> {
        val start = template.indexOf("  ECSTaskRole:")
        assertTrue(start >= 0) { "infra/cloudformation.yml declares no ECSTaskRole" }
        val end = (start + 1 until template.size).firstOrNull { RESOURCE_START.matches(template[it]) } ?: template.size
        val grants = mutableSetOf<Pair<String, String>>()
        var statement: PolicyStatement? = null
        var listKey: String? = null
        fun close() {
            statement?.takeIf { it.effect == "Allow" }?.let { grants += it.grants() }
            statement = null
            listKey = null
        }
        template.subList(start + 1, end).forEach { line ->
            val text = line.trim()
            if (text.isEmpty() || text.startsWith("#")) return@forEach
            val indent = line.length - line.trimStart().length
            val effect = EFFECT.matchEntire(text)
            val current = statement
            when {
                effect != null -> {
                    close()
                    statement = PolicyStatement(effect.groupValues[1], indent + 2)
                }

                current == null -> Unit
                indent < current.keyColumn -> close()
                indent == current.keyColumn -> {
                    val value = text.substringAfter(":").trim()
                    listKey = text.substringBefore(":").takeIf { value.isEmpty() }
                    if (value.isNotEmpty()) current.add(text.substringBefore(":"), value)
                }

                text.startsWith("- ") -> listKey?.let { key -> current.add(key, text.removePrefix("- ").trim()) }
            }
        }
        close()
        return grants
    }

    private class PolicyStatement(val effect: String, val keyColumn: Int) {
        private val values = mutableMapOf<String, MutableList<String>>()

        fun add(key: String, value: String) {
            values.getOrPut(key) { mutableListOf() } += value.trim('"', '\'')
        }

        fun grants(): Set<Pair<String, String>> =
            values["Action"].orEmpty().flatMap { action ->
                values["Resource"].orEmpty().map { resource -> action to logicalResource(resource) }
            }.toSet()
    }

    private companion object {
        const val KEY = "document-versions/record/record_v1.pdf"
        const val VERSION_BUCKET_VARIABLE = "DOCUMENT_VERSION_STORAGE_AWS_BUCKET"
        const val FILE_BUCKET_VARIABLE = "FILE_STORAGE_AWS_BUCKET"
        val RESOURCE_START = Regex("^  \\S.*")
        val EFFECT = Regex("^- Effect: (\\w+)$")
        val REFERENCE = Regex("^!Ref (\\w+)$")
        val ATTRIBUTE = Regex("^!GetAtt (\\w+)\\.Arn$")
        val SUBSTITUTION = Regex("^!Sub \\$\\{(\\w+)\\.Arn}(.*)$")

        fun logicalResource(resource: String): String =
            ATTRIBUTE.matchEntire(resource)?.let { it.groupValues[1] }
                ?: SUBSTITUTION.matchEntire(resource)?.let { it.groupValues[1] + it.groupValues[2] }
                ?: resource
    }
}
