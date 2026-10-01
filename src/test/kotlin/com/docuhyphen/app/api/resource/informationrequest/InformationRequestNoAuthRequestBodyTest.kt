package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestNoAuthRequestBodyTest
{
    @ParameterizedTest(name = "{0} {1} with body [{2}]")
    @MethodSource("malformedBodies")
    fun `a malformed body on a no-auth endpoint is refused as a bad request`(method: String, path: String, body: String)
    {
        val response = given()
            .contentType("application/json")
            .body(body)
            .request(method, path)
        val content = response.body.asString()

        assertEquals(400, response.statusCode, content)
        assertTrue(content.contains("errorMessage"), content)
        assertFalse(content.contains("com.docuhyphen"), content)
    }

    companion object
    {
        private const val ID = "7d1b0f7e-2c1a-4c1e-9d4a-3b6f8e2a9c10"

        private val ENDPOINTS = listOf(
            "PATCH" to "/no-auth/information-requests/$ID/responses",
            "POST" to "/no-auth/information-requests/$ID/group-occurrences",
            "PATCH" to "/no-auth/information-requests/$ID/group-occurrences/order",
            "POST" to "/no-auth/information-requests/$ID/accepted-fact-offers/$ID/recertifications",
            "POST" to "/no-auth/information-requests/$ID/requirements/$ID/attestations",
            "POST" to "/no-auth/information-requests/$ID/requirements/$ID/evidence-artifacts/$ID/withdrawals",
            "POST" to "/no-auth/information-request-access-links/sessions",
            "POST" to "/no-auth/information-requests/$ID/reviews/$ID/comments",
            "POST" to "/no-auth/information-requests/$ID/reviews/$ID/appeals",
            "POST" to "/no-auth/information-requests/$ID/submissions",
            "POST" to "/no-auth/information-requests/$ID/submissions/$ID/withdrawal",
        )

        private val BODIES = listOf("{}", "{\"", "[]", "")

        @JvmStatic
        fun malformedBodies(): Stream<Arguments> =
            ENDPOINTS.flatMap { (method, path) -> BODIES.map { body -> Arguments.of(method, path, body) } }.stream()
    }
}
