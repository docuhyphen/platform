package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestResponseTargetAuthorizationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: AuthorizationService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requests) }

    @Test
    fun `a hidden Requirement and an unknown Requirement give the same forbidden refusal`() {
        val request = support.fieldRequest()
        val services = runtime.build(request.requestId, centralAuthorization = authorization)
        requireHiddenFromContributor(request)

        val hidden = assertThrows(ForbiddenException::class.java) {
            support.saveNarrative(
                services,
                request,
                request.runtime.attestationRequirementId,
                "Neutral note",
                "hidden-target"
            )
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            support.saveNarrative(services, request, UUID.randomUUID(), "Neutral note", "unknown-target")
        }

        assertEquals(hidden.message, unknown.message)
    }

    @Test
    fun `a hidden or unknown Requirement named among confirmed clears is refused alike and nothing is saved`() {
        val request = support.fieldRequest()
        val services = runtime.build(request.requestId, centralAuthorization = authorization)
        val before = support.responseETag(request)

        val hidden = assertThrows(ForbiddenException::class.java) {
            confirmClear(services, request, request.runtime.attestationRequirementId, "hidden-clear")
        }
        val unknown = assertThrows(ForbiddenException::class.java) {
            confirmClear(services, request, UUID.randomUUID(), "unknown-clear")
        }

        assertEquals(hidden.message, unknown.message)
        assertEquals(before, support.responseETag(request))
    }

    @Test
    fun `control an assigned Requirement still accepts a sparse draft`() {
        val request = support.fieldRequest()
        val services = runtime.build(request.requestId, centralAuthorization = authorization)
        val before = support.responseETag(request)

        support.saveNarrative(services, request, request.answers.requirementId, "Neutral note", "assigned-target")

        assertTrue(before != support.responseETag(request))
    }

    private fun confirmClear(
        services: InformationRequestRuntimeServices,
        request: ConformanceRequest,
        clearedRequirementId: UUID,
        key: String,
    ) = QuarkusTransaction.requiringNew().call {
        services.responses.patch(
            PatchInformationRequestResponsesCommand(
                requestId = request.requestId,
                access = support.contributor(request),
                precondition = CommandPrecondition.ExpectedRevision(support.responseETag(request)),
                idempotencyKey = key,
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = request.answers.requirementId,
                        narrative = ResponseNarrativePatch.Set("Neutral note"),
                    ),
                ),
                confirmedHiddenResponseClears = setOf(clearedRequirementId),
            ),
        )
    }

    private fun requireHiddenFromContributor(request: ConformanceRequest) {
        val access = support.contributor(request)
        QuarkusTransaction.requiringNew().run {
            listOf(
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND
            ).forEach { action ->
                assertTrue(
                    authorization.authorize(
                        access.principal,
                        action,
                        ResourceRef.informationRequestRequirement(request.runtime.attestationRequirementId),
                        access.authorization,
                    ) is Decision.Deny,
                    action.toString(),
                )
            }
        }
    }
}
