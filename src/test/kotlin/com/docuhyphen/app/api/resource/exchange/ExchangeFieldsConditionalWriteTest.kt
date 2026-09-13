package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.DeprecatedFieldsWriteUsage
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The two Fields write surfaces of an Exchange, driven through the real engine rather than through a
 * service stubbed to refuse, so each status is produced by an actual precondition weighed against
 * actually stored answers.
 *
 * The canonical surface requires the caller to state the version it read: a caller that states none
 * is told to start stating one, a caller whose version has been overtaken is told to read again, and
 * neither stores anything. The superseded surface behaves as it always has for a caller that states
 * nothing, enforces a version when one is stated, names its successor, and has every call recorded
 * so the compatibility window can be measured.
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

    private val deprecatedWriteUsage = mock<DeprecatedFieldsWriteUsage>()

    private val resource = ExchangeFieldsResource(
        authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = AppUser() }
        },
        schemaAssignmentService = fixture.service,
        fieldsAccessContextFactory = mock<FieldsAccessContextFactory>().also {
            whenever(it.current()).thenReturn(fixture.access)
        },
        deprecatedWriteUsage = deprecatedWriteUsage,
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
    fun `a superseded save stating no version is still accepted and recorded as unconditioned`()
    {
        val response = resource.setValues(exchangeId, answering("second recorded answer"), null)

        assertEquals(200, response.status)
        assertEquals("second recorded answer", storedSecondNote())
        verify(deprecatedWriteUsage).record(fixture.resource, principal, false)
    }

    @Test
    fun `a superseded save has a stated version enforced and is recorded as conditioned`()
    {
        val stateBothCallersRead = versionAsRead
        resource.setValues(exchangeId, answering("answer of the first caller"), null)

        val response =
            resource.setValues(exchangeId, answering("answer of the second caller"), stateBothCallersRead)

        assertEquals(412, response.status)
        assertEquals("FIELDS_PRECONDITION_STALE", (response.entity as ResponseError).reasonCode)
        assertEquals("answer of the first caller", storedSecondNote())
        verify(deprecatedWriteUsage).record(fixture.resource, principal, true)
    }

    @Test
    fun `only the superseded surface reports itself superseded`()
    {
        val superseded = resource.setValues(exchangeId, answering("second recorded answer"), null)
        val canonical = resource.patchValues(exchangeId, answering("third recorded answer"), versionAsRead)

        assertEquals("true", superseded.getHeaderString("Deprecation"))
        assertEquals(
            "</exchanges/${fixture.resourceId}/fields>; rel=\"successor-version\"",
            superseded.getHeaderString("Link"),
            "A client is told where the surface it should be using lives",
        )
        assertNull(canonical.getHeaderString("Deprecation"))
        assertNull(canonical.getHeaderString("Link"))
        verify(deprecatedWriteUsage, never()).record(fixture.resource, principal, true)
    }

    @Test
    fun `a superseded call naming no resolvable resource reflects nothing back to its caller`()
    {
        val response = resource.setValues("not-an-exchange", answering("second recorded answer"), null)

        assertEquals(400, response.status)
        assertEquals("true", response.getHeaderString("Deprecation"))
        assertNull(
            response.getHeaderString("Link"),
            "The successor is named from the resolved identifier, so nothing the caller sent comes back",
        )
    }

    @Test
    fun `both surfaces leave an answer they were not sent untouched`()
    {
        resource.patchValues(exchangeId, answering("second recorded answer"), versionAsRead)
        resource.setValues(exchangeId, SetFieldValuesRequest(listOf(fixture.options("first-option"))), null)

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
