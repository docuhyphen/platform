package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.model.HiddenTargetRoute
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.noauth.NoAuthSessionSqlScenario
import com.docuhyphen.app.api.service.informationrequest.noauth.RequestAccessSessionService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestHiddenRequirementHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var links: ShareLinkService

    @Inject
    lateinit var sessions: RequestAccessSessionService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requests) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    fun `a signed-in respondent cannot tell a Requirement hidden from them from one that does not exist`(name: String) {
        val route = ROUTES.getValue(name)
        val prepared = signedIn()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.runtime.contributorUserId)

        val hidden =
            send(route, caller.authorized(), SIGNED_IN, prepared.requestId, prepared.runtime.attestationRequirementId)
        val unknown = send(route, caller.authorized(), SIGNED_IN, prepared.requestId, UUID.randomUUID())

        assertEquals(403, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(hidden.asString(), unknown.asString())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    fun `a respondent on an access link cannot tell a Requirement hidden from them from one that does not exist`(name: String) {
        val route = ROUTES.getValue(name)
        val prepared = accessLink()

        val hidden = send(
            route,
            prepared.linked(),
            NO_AUTH,
            prepared.scenario.requestId,
            prepared.scenario.runtime.attestationRequirementId
        )
        val unknown = send(route, prepared.linked(), NO_AUTH, prepared.scenario.requestId, UUID.randomUUID())

        assertEquals(403, hidden.statusCode, hidden.asString())
        assertEquals(hidden.statusCode, unknown.statusCode, unknown.asString())
        assertEquals(hidden.asString(), unknown.asString())
    }

    @Test
    fun `control a signed-in respondent still answers its own Requirement and lists its own files`() {
        val prepared = signedIn()
        val caller = InformationRequestSignedInCaller.signIn(appUsers, prepared.runtime.contributorUserId)

        val answered = send(ANSWER, caller.authorized(), SIGNED_IN, prepared.requestId, prepared.answers.requirementId)
        val listed =
            send(LIST_FILES, caller.authorized(), SIGNED_IN, prepared.requestId, prepared.runtime.documentRequirementId)

        assertEquals(200, answered.statusCode, answered.asString())
        assertEquals(200, listed.statusCode, listed.asString())
    }

    @Test
    fun `control a respondent on an access link still answers its own Requirement and lists its own files`() {
        val prepared = accessLink()
        val requirementId = prepared.scenario.runtime.documentRequirementId

        val answered = send(ANSWER, prepared.linked(), NO_AUTH, prepared.scenario.requestId, requirementId)
        val listed = send(LIST_FILES, prepared.linked(), NO_AUTH, prepared.scenario.requestId, requirementId)

        assertEquals(200, answered.statusCode, answered.asString())
        assertEquals(200, listed.statusCode, listed.asString())
    }

    private fun send(
        route: HiddenTargetRoute,
        spec: RequestSpecification,
        prefix: String,
        requestId: UUID,
        target: UUID
    ): Response {
        val headed = spec
            .header("Idempotency-Key", "hidden-target-${UUID.randomUUID()}")
            .header("If-Match", responseETag(requestId))
        val prepared = when {
            route.multipart -> headed.multiPart("file", uploadFile(), "application/pdf")
            route.body != null -> headed.contentType(ContentType.JSON).body(route.body(target.toString(), OTHER))
            else -> headed.contentType(ContentType.JSON)
        }
        return prepared.request(route.verb, route.path(prefix, requestId, target.toString(), OTHER))
    }

    private fun uploadFile() = Files.createTempFile("request-target-", ".pdf").toFile().apply {
        writeText("%PDF-1.4")
        deleteOnExit()
    }

    private fun responseETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.responsesOf(requireNotNull(requests.findById(requestId))) }

    private fun signedIn(): ConformanceRequest = signedInRequest ?: support.fieldRequest().also { signedInRequest = it }

    private fun accessLink(): AccessLinkFixture =
        accessLinkRequest ?: NoAuthSessionSqlScenario(dataSource, links, sessions).let { scenario ->
            AccessLinkFixture(scenario, scenario.issueSession().sessionToken)
        }.also { accessLinkRequest = it }

    private class AccessLinkFixture(val scenario: NoAuthSessionSqlScenario, private val sessionToken: String) {
        fun linked(): RequestSpecification = given()
            .header(InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER, scenario.rawToken)
            .header(InformationRequestCommandHttp.SESSION_TOKEN_HEADER, sessionToken)
    }

    companion object {
        private var signedInRequest: ConformanceRequest? = null
        private var accessLinkRequest: AccessLinkFixture? = null

        private val OTHER: UUID = UUID.randomUUID()
        private const val SIGNED_IN = ""
        private const val NO_AUTH = "/no-auth"
        private const val FILES = "/information-requests/{id}/requirements/{target}/evidence-artifacts"

        private val ANSWER = HiddenTargetRoute(
            "PATCH",
            "/information-requests/{id}/responses",
            "{\"patches\":[{\"requirementId\":\"{target}\",\"narrative\":\"Neutral note\"}]}",
        )
        private val LIST_FILES = HiddenTargetRoute("GET", FILES)

        private val ROUTES: Map<String, HiddenTargetRoute> = linkedMapOf(
            "PATCH responses" to ANSWER,
            "POST attestations" to HiddenTargetRoute(
                "POST",
                "/information-requests/{id}/requirements/{target}/attestations",
                "{\"decision\":\"ASSENTED\"}",
            ),
            "GET files" to LIST_FILES,
            "POST files" to HiddenTargetRoute("POST", FILES, multipart = true),
            "GET file" to HiddenTargetRoute("GET", "$FILES/{other}"),
            "POST file removals" to HiddenTargetRoute("POST", "$FILES/{other}/removals", "{}"),
            "POST file versions" to HiddenTargetRoute("POST", "$FILES/{other}/versions", multipart = true),
            "GET file content" to HiddenTargetRoute("GET", "$FILES/{other}/versions/{other}/content"),
            "GET file preview" to HiddenTargetRoute("GET", "$FILES/{other}/versions/{other}/preview"),
            "POST file withdrawals" to HiddenTargetRoute("POST", "$FILES/{other}/withdrawals", "{}"),
        )

        @JvmStatic
        fun routes(): List<String> = ROUTES.keys.toList()
    }
}
