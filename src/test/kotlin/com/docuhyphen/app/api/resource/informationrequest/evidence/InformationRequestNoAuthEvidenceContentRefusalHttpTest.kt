package com.docuhyphen.app.api.resource.informationrequest.evidence

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.noauth.NoAuthSessionSqlScenario
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.informationrequest.noauth.revokeAccessSession
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.response.Response
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestNoAuthEvidenceContentRefusalHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [DOWNLOAD, PREVIEW])
    fun `a file read whose session was revoked answers its refusal as JSON`(use: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        dataSource.revokeAccessSession(issued.session.id)

        val response = readFile(scenario, issued.sessionToken, use)

        assertJsonRefusal(response, 409, InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [DOWNLOAD, PREVIEW])
    fun `a file read whose session expired answers its refusal as JSON`(use: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_access_session SET expires_at = now() - interval '1 second' WHERE id = ?",
                issued.session.id,
            )
        }

        val response = readFile(scenario, issued.sessionToken, use)

        assertJsonRefusal(response, 409, InformationRequestErrorCatalog.ACCESS_SESSION_EXPIRED)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = [DOWNLOAD, PREVIEW])
    fun `a file read of a file the session cannot see answers its refusal as JSON`(use: String) {
        val scenario = NoAuthSessionSqlScenario(dataSource, links, sessions)
        val issued = scenario.issueSession()

        val response = readFile(scenario, issued.sessionToken, use)

        assertJsonRefusal(response, 404, InformationRequestErrorCatalog.NOT_FOUND)
    }

    private fun readFile(scenario: NoAuthSessionSqlScenario, sessionToken: String, use: String): Response =
        given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, scenario.rawToken)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, sessionToken)
            .header(HttpHeaders.ACCEPT, BROWSER_ACCEPT)
            .get(
                "/no-auth/information-requests/${scenario.requestId}/requirements/" +
                        "${scenario.runtime.documentRequirementId}/evidence-artifacts/${UUID.randomUUID()}/versions/" +
                        "${UUID.randomUUID()}/$use",
            )

    private fun assertJsonRefusal(response: Response, status: Int, reasonCode: String) {
        val body = response.body.asString()
        assertEquals(status, response.statusCode, body)
        val refusal = Json.parseToJsonElement(body).jsonObject
        assertEquals(reasonCode, refusal["reasonCode"]?.jsonPrimitive?.content, body)
        assertTrue(!refusal["errorMessage"]?.jsonPrimitive?.content.isNullOrBlank(), body)
    }

    private companion object {
        const val DOWNLOAD = "content"
        const val PREVIEW = "preview"
        const val BROWSER_ACCEPT = "application/json, text/plain, */*"
    }
}
