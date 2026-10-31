package com.docuhyphen.app.api.resource.informationrequest.evidence

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.noauth.NoAuthSessionSqlScenario
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestEvidenceStateChangeHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["withdrawals", "removals"])
    fun `an oversized reason answers a stable client refusal before the artifact is changed`(action: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        val path = "/no-auth/information-requests/${scenario.requestId}/requirements/" +
                "${scenario.runtime.documentRequirementId}/evidence-artifacts/${UUID.randomUUID()}/$action"

        val response = given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, scenario.rawToken)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, issued.sessionToken)
            .header("If-Match", "\"evidence-revision\"")
            .header("Idempotency-Key", "oversized-reason-${UUID.randomUUID()}")
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"${"x".repeat(513)}\"}")
            .post(path)

        assertEquals(400, response.statusCode, response.asString())
        assertEquals(
            "INFORMATION_REQUEST_EVIDENCE_REQUEST_INVALID",
            response.jsonPath().getString("reasonCode"),
            response.asString(),
        )
        assertEquals("A reason is at most 512 characters", response.jsonPath().getString("errorMessage"))
    }
}
