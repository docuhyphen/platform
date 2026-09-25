package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldValueSetETag
import com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory
import com.docuhyphen.app.api.service.fields.SchemaAssignmentFieldsFixture
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The Fields write surface of an Exchange, driven through the real engine rather than through a
 * service stubbed to refuse, so each status is produced by an actual precondition weighed against
 * actually stored answers.
 *
 * The surface requires the caller to state the version it read: a caller that states none is told to
 * start stating one, a caller whose version has been overtaken is told to read again, and neither
 * stores anything.
 */
class ExchangeFieldsConditionalWriteTest
{
    private val principal: PrincipalRef = PrincipalRef.user(UUID.randomUUID())

    private val fixture = SchemaAssignmentFieldsFixture(
        principal = principal,
        rootAnswers = mapOf(
            SchemaAssignmentFieldsFixture.Question.NOTE to
                SchemaAssignmentFieldsFixture.StoredAnswer("recorded note as read", principal),
        ),
    )

    private val resource = ExchangeFieldsResource(
        authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = AppUser() }
        },
        schemaAssignmentService = fixture.service,
        fieldsAccessContextFactory = mock<FieldsAccessContextFactory>().also {
            whenever(it.current()).thenReturn(fixture.access)
        },
    )

    /** The version the exchange's answers currently stand in, as a read of them would serve it. */
    private val versionAsRead: String get() = FieldValueSetETag.of(fixture.rootSet)

    private val exchangeId: String get() = fixture.resourceId.toString()

    @Test
    fun `a canonical save stating the version it read stores the answer and returns the new version`()
    {
        val stateBeforeSaving = versionAsRead

        val response = resource.patchValues(exchangeId, answering("second recorded answer"), stateBeforeSaving)

        assertEquals(200, response.status)
        assertEquals("second recorded answer", storedSecondNote())
        assertEquals(versionAsRead, response.getHeaderString("ETag"))
        assertNotEquals(
            stateBeforeSaving, response.getHeaderString("ETag"),
            "A save that stored a change hands back the version it produced, not the one it was given",
        )
    }

    @Test
    fun `a canonical save stating no version is refused for the missing one and stores nothing`()
    {
        val response = resource.patchValues(exchangeId, answering("second recorded answer"), null)

        assertEquals(428, response.status)
        assertEquals("FIELDS_PRECONDITION_REQUIRED", (response.entity as ResponseError).reasonCode)
        assertNull(storedSecondNote(), "A refused save stores nothing")
    }

    @Test
    fun `a canonical save stating a version the answers have moved past is refused as stale`()
    {
        val stateBothCallersRead = versionAsRead
        resource.patchValues(exchangeId, answering("answer of the first caller"), stateBothCallersRead)

        val response =
            resource.patchValues(exchangeId, answering("answer of the second caller"), stateBothCallersRead)

        assertEquals(412, response.status)
        assertEquals("FIELDS_PRECONDITION_STALE", (response.entity as ResponseError).reasonCode)
        assertEquals(
            versionAsRead, response.getHeaderString("ETag"),
            "The refusal carries the version that is current, so the caller can read again and retry",
        )
        assertEquals(
            "answer of the first caller", storedSecondNote(),
            "The overtaken save leaves the answer the earlier one stored",
        )
    }

    @Test
    fun `a canonical save may accept whichever version is current`()
    {
        val response = resource.patchValues(exchangeId, answering("second recorded answer"), "*")

        assertEquals(200, response.status)
        assertEquals("second recorded answer", storedSecondNote())
    }

    @Test
    fun `a canonical save stating a version it marks weak is refused`()
    {
        val response =
            resource.patchValues(exchangeId, answering("second recorded answer"), "W/$versionAsRead")

        assertEquals(412, response.status)
        assertNull(storedSecondNote())
    }

    @Test
    fun `a sparse save leaves an answer it was not sent untouched`()
    {
        resource.patchValues(exchangeId, answering("second recorded answer"), versionAsRead)
        resource.patchValues(exchangeId, SetFieldValuesRequest(listOf(fixture.options("first-option"))), versionAsRead)

        assertEquals(
            "recorded note as read", fixture.rootAnswer(fixture.noteContractId)?.textValue,
            "A sparse save changes only the answers it carries",
        )
        assertEquals("second recorded answer", storedSecondNote())
    }

    private fun answering(text: String) = SetFieldValuesRequest(
        listOf(FieldValueEntry(fixture.secondNoteContractId, JsonPrimitive(text))),
    )

    private fun storedSecondNote(): String? = fixture.rootAnswer(fixture.secondNoteContractId)?.textValue
}
