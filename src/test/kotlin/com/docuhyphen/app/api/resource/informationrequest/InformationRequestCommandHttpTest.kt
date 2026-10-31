package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestItemRefusalDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.FieldsPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import jakarta.persistence.PersistenceException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.*

class InformationRequestCommandHttpTest {
    private val readAccessService = InformationRequestNoAuthAccessDoubles.current()
    private val requestId = UUID.randomUUID()
    private val resolved = RequestAccessContext(
        PrincipalRef.participant(UUID.randomUUID()),
        AuthorizationContext(mfaSatisfied = true, sessionRef = "resolved-session"),
    )

    @Test
    fun `a no-auth read is made with the access the session recheck holds current`() {
        val current = resolved.copy(authorization = AuthorizationContext(sessionRef = "current-session"))
        whenever(readAccessService.resolve(requestId, "bootstrap", "session-secret")).thenReturn(resolved)
        doAnswer { it.getArgument<(RequestAccessContext) -> Any?>(2).invoke(current) }
            .whenever(readAccessService).readCurrent<Any?>(eq(requestId), eq(resolved), any())
        val reads = mutableListOf<RequestAccessContext>()

        val response = InformationRequestCommandHttp.withNoAuthRead(
            readAccessService,
            requestId.toString(),
            "bootstrap",
            "session-secret",
        ) { _, access ->
            reads += access
            Response.ok().build()
        }

        assertEquals(200, response.status)
        assertEquals(listOf(current), reads)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["23514", "23505", "23503", "23502", "22001", "P0001"])
    fun `a change a stored rule refuses answers 409 with the constraint code`(sqlState: String) {
        val refusal = RuntimeException(
            "transaction rolled back",
            PersistenceException("could not execute statement", SQLException("refused by a stored rule", sqlState)),
        )

        val response = InformationRequestCommandHttp.refused(logger, "Command failed", refusal)

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(InformationRequestErrorCatalog.CONSTRAINT_CONFLICT, (response.entity as ResponseError).reasonCode)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["08006", "40001", "40P01", "55P03", "57014", "none"])
    fun `a storage failure that no stored rule raised still answers 500`(sqlState: String) {
        val failure = PersistenceException(
            "could not execute statement",
            SQLException("the database could not complete the statement", sqlState.takeUnless { it == "none" }),
        )

        val response = InformationRequestCommandHttp.refused(logger, "Command failed", failure)

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
        assertNull((response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a Field value the Fields engine refuses answers 400 with a stable code`() {
        val response = InformationRequestCommandHttp.refused(
            logger,
            "Command failed",
            FieldValidationException("Must be at most 5 characters")
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals(InformationRequestErrorCatalog.FIELD_VALUE_INVALID, (response.entity as ResponseError).reasonCode)
        assertEquals("Must be at most 5 characters", (response.entity as ResponseError).errorMessage)
    }

    @Test
    fun `a missing or stale Field precondition answers 428 or 412 with the current ETag`() {
        val missing = InformationRequestCommandHttp.refused(
            logger,
            "Command failed",
            FieldsPreconditionException.required("\"current\"")
        )
        val stale = InformationRequestCommandHttp.refused(
            logger,
            "Command failed",
            FieldsPreconditionException.stale("\"current\"")
        )

        assertEquals(428, missing.status)
        assertEquals(412, stale.status)
        assertEquals("\"current\"", stale.getHeaderString("ETag"))
        assertEquals("FIELDS_PRECONDITION_STALE", (stale.entity as ResponseError).reasonCode)
    }

    @Test
    fun `an item refusal keeps the problems it names`() {
        val requirementId = UUID.randomUUID()
        val refusal = InformationRequestItemRefusalException(
            reasonCode = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
            message = "This change would remove an answer given once",
            problems = listOf(
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.SUBMISSION_LOCKED,
                    message = "This answer is given once",
                    requirementId = requirementId,
                ),
            ),
        )

        val response = InformationRequestCommandHttp.refused(logger, "Command failed", refusal)

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            listOf(requirementId),
            (response.entity as InformationRequestItemRefusalDto).problems.map { it.requirementId })
    }

    @Test
    fun `a persistence failure without a database state still answers 500`() {
        val response = InformationRequestCommandHttp.refused(
            logger,
            "Command failed",
            PersistenceException("no transaction is in progress"),
        )

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
        assertNull((response.entity as ResponseError).reasonCode)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestCommandHttpTest::class.java)
    }
}
