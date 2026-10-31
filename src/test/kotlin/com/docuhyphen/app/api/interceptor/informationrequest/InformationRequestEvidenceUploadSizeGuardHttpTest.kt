package com.docuhyphen.app.api.interceptor.informationrequest

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceSurface
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceUploadLimits
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.URI
import java.util.*

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceUploadSizeGuardHttpTest {
    @Inject
    lateinit var limits: InformationRequestEvidenceUploadLimits

    @Test
    fun `an anonymous upload declaring more than the anonymous ceiling is answered before any of its body is sent`() {
        val declared = limits.requestBodyCeiling(InformationRequestEvidenceSurface.NO_AUTH) + 1
        val path =
            "/no-auth/information-requests/${UUID.randomUUID()}/requirements/${UUID.randomUUID()}/evidence-artifacts"

        val answer = headersOnly(path, declared)

        assertTrue(answer.startsWith("HTTP/1.1 413"), answer.lineSequence().firstOrNull())
        assertTrue(answer.contains(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED), answer)
    }

    @Test
    fun `a signed-in replacement declaring more than the signed-in ceiling is answered before any of its body is sent`() {
        val declared = limits.requestBodyCeiling(InformationRequestEvidenceSurface.AUTHENTICATED) + 1
        val path = "/information-requests/${UUID.randomUUID()}/requirements/${UUID.randomUUID()}/evidence-artifacts/" +
                "${UUID.randomUUID()}/versions"

        val answer = headersOnly(path, declared)

        assertTrue(answer.startsWith("HTTP/1.1 413"), answer.lineSequence().firstOrNull())
        assertTrue(answer.contains(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED), answer)
    }

    @Test
    fun `an anonymous upload sent in chunks without a declared length is answered before any of its body is sent`() {
        val path =
            "/no-auth/information-requests/${UUID.randomUUID()}/requirements/${UUID.randomUUID()}/evidence-artifacts"

        val answer = headersOnly(path, null, InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LENGTH_REQUIRED)

        assertTrue(answer.startsWith("HTTP/1.1 411"), answer.lineSequence().firstOrNull())
        assertTrue(answer.contains(InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LENGTH_REQUIRED), answer)
    }

    private fun headersOnly(
        path: String,
        declaredBytes: Long?,
        expectedReason: String = InformationRequestErrorCatalog.EVIDENCE_UPLOAD_LIMIT_EXCEEDED,
    ): String {
        val host = URI(RestAssured.baseURI).host
        Socket(host, RestAssured.port).use { socket ->
            socket.soTimeout = 20_000
            val length = declaredBytes?.let { "Content-Length: $it\r\n" } ?: "Transfer-Encoding: chunked\r\n"
            val head = "POST $path HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "Content-Type: multipart/form-data; boundary=evidence-boundary\r\n" +
                    length +
                    "Connection: close\r\n" +
                    "\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val received = ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            val input = socket.getInputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                received.write(buffer, 0, read)
                if (received.toString(Charsets.UTF_8).contains(expectedReason)) break
            }
            return received.toString(Charsets.UTF_8)
        }
    }
}
