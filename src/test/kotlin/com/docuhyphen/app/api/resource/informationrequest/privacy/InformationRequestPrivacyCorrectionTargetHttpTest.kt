package com.docuhyphen.app.api.resource.informationrequest.privacy

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.resource.model.InformationRequestItemCorrectionRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestPrivacyCorrectionTargetHttpTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var authorization: DefaultAuthorizationService

    @Test
    fun `a privacy correction never distinguishes an unknown submitted item from one outside the subject`() {
        val operator = UUID.randomUUID()
        val subject = UUID.randomUUID()
        val item = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { configured ->
                configured.insertUser(operator)
                val submission = UUID.randomUUID()
                configured.insertPackage(submission, 1)
                configured.insertItem(
                    item, submission, configured.documentRequirementId, configured.documentRevisionId,
                    configured.documentBindingId, "DOCUMENT", configured.documentResponseId,
                )
                execute(
                    connection,
                    "INSERT INTO subject_identity_ref (id, owner_type, owner_user_id, subject_kind, created_at) VALUES (?, 'USER', ?, 'RECORD', now())",
                    subject, operator,
                )
            }
        }
        assertTrue(QuarkusTransaction.requiringNew().call {
            authorization.authorize(
                PrincipalRef.user(operator), Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(fixture.requestId), AuthorizationContext(),
            ) is Decision.Deny
        })
        val caller = InformationRequestSignedInCaller.signIn(appUsers, operator)
        val responses = listOf(item, UUID.randomUUID()).map { target ->
            val body = RecordInformationRequestPrivacyRequestRequest(
                subject.toString(), InformationRequestPrivacyRequestKind.CORRECTION, "record-accuracy", "owner-policy",
                correction = InformationRequestItemCorrectionRequest(
                    target.toString(),
                    narrative = "Corrected record",
                    reasonCode = "RECORD_CORRECTION"
                ),
            )
            caller.command("privacy-correction-${UUID.randomUUID()}")
                .body(Json.encodeToString(RecordInformationRequestPrivacyRequestRequest.serializer(), body))
                .post("/information-request-privacy-requests")
        }
        responses.forEach { response ->
            assertEquals(201, response.statusCode, response.asString())
            assertEquals("REFUSED", response.jsonPath().getString("state"), response.asString())
            assertEquals(
                InformationRequestErrorCatalog.NOT_FOUND,
                response.jsonPath().getString("refusalCode"),
                response.asString()
            )
        }
        assertEquals(
            responses[1].jsonPath().getString("refusalDetail"),
            responses[0].jsonPath().getString("refusalDetail"),
        )
    }
}
