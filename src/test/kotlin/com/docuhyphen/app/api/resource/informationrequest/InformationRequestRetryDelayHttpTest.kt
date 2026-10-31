package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRetryDelayHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `an access challenge serializes its retry delay for browser clients`() {
        val limiter = mock<AuthRateLimitService>()
        whenever(limiter.retryAfterSeconds(any(), any())).thenReturn(37L)
        QuarkusMock.installMockForType(limiter, AuthRateLimitService::class.java)
        val response = given().contentType("application/json").header("X-Request-Access-Token", "unknown-access-token")
            .post("/no-auth/information-request-access-links/challenges")
        assertEquals(429, response.statusCode, response.asString())
        assertEquals("37", response.header("Retry-After"))
        assertEquals(37L, response.jsonPath().get<Number?>("retryAfterSeconds")?.toLong())
    }

    @Test
    fun `an export refusal serializes its retry delay through the common command mapper`() {
        val exports = mock<InformationRequestRecordExportService>()
        whenever(exports.create(any())).thenThrow(
            InformationRequestRateLimitedException(
                InformationRequestErrorCatalog.EXPORT_LIMIT_REACHED, 43L, "Try again later",
            )
        )
        QuarkusMock.installMockForType(exports, InformationRequestRecordExportService::class.java)
        val userId = dataSource.connection.use { SubmissionRuntimeSqlFixture(it).template.userId }
        val caller = InformationRequestSignedInCaller.signIn(appUsers, userId)
        val response = caller.command("limited-export").body("{}")
            .post("/information-requests/${UUID.randomUUID()}/record-exports")
        assertEquals(429, response.statusCode, response.asString())
        assertEquals("43", response.header("Retry-After"))
        assertEquals(43L, response.jsonPath().get<Number?>("retryAfterSeconds")?.toLong())
    }
}
