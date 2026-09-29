package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubjectDto
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.model.informationrequest.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubjectReference
import com.docuhyphen.app.api.resource.model.AssignInformationRequestSubjectRequest
import com.docuhyphen.app.api.resource.model.InformationRequestSubjectReferenceRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubjectService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestSubjectResourceContractTest
{
    private val subjectService = mock<InformationRequestSubjectService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val partyResource = InformationRequestSubjectPartyResource(subjectService, accessContextFactory)
    private val listResource = InformationRequestSubjectResource(subjectService)
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `a request's subject is assigned under its parties path and the owner's subjects are listed apart`()
    {
        val subjectPath = InformationRequestSubjectPartyResource::class.java.getAnnotation(Path::class.java).value
        val listPath = InformationRequestSubjectResource::class.java.getAnnotation(Path::class.java).value
        val assignMethod = InformationRequestSubjectPartyResource::class.java.declaredMethods.single { it.name == "assign" }
        val listMethod = InformationRequestSubjectResource::class.java.declaredMethods.single { it.name == "list" }

        assertEquals("/information-requests/{id}/subjects", subjectPath)
        assertEquals("/information-request-subjects", listPath)
        assertTrue(assignMethod.isAnnotationPresent(POST::class.java))
        assertTrue(listMethod.isAnnotationPresent(GET::class.java))
    }

    @Test
    fun `assigning a subject passes its kind, reference, precondition, and key and answers the parties ETag`()
    {
        whenever(subjectService.assignSubject(any())).thenReturn(
            InformationRequestPartyAssignmentResult(
                party = InformationRequestParty().apply {
                    informationRequestId = requestId
                    roleKey = InformationRequestShareRoleKey.SUBJECT
                    subjectIdentityRefId = UUID.randomUUID()
                },
                partiesETag = "\"parties-3\"",
                partyETag = "\"party-1\"",
            ),
        )

        val assigned = partyResource.assign(
            requestId.toString(),
            AssignInformationRequestSubjectRequest(
                subjectKind = SubjectKind.RECORD,
                reference = InformationRequestSubjectReferenceRequest("Records office", "Account", "A-100"),
            ),
            "\"parties-2\"",
            "assign-subject",
        )
        val blankReference = partyResource.assign(
            requestId.toString(),
            AssignInformationRequestSubjectRequest(
                subjectKind = SubjectKind.RECORD,
                reference = InformationRequestSubjectReferenceRequest("Records office", " ", "A-100"),
            ),
            "\"parties-2\"",
            "assign-blank",
        )

        assertEquals(Response.Status.CREATED.statusCode, assigned.status)
        assertEquals("\"parties-3\"", assigned.getHeaderString("ETag"))
        assertEquals(InformationRequestShareRoleKey.SUBJECT, (assigned.entity as InformationRequestPartyDto).roleKey)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, blankReference.status)
        verify(subjectService).assignSubject(
            AssignInformationRequestSubjectCommand(
                requestId = requestId,
                subjectKind = SubjectKind.RECORD,
                reference = InformationRequestSubjectReference("Records office", "Account", "A-100"),
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"parties-2\"")),
                idempotencyKey = "assign-subject",
            ),
        )
    }

    @Test
    fun `the owner's subjects are listed and a caller who may not manage privacy is refused`()
    {
        val subject = InformationRequestSubjectDto(UUID.randomUUID(), SubjectKind.PERSON, emptyList(), Timestamp.from(Instant.now()))
        whenever(subjectService.listForOwner()).thenReturn(listOf(subject)).thenThrow(ForbiddenException("Access denied"))

        val listed = listResource.list()
        val refused = listResource.list()

        assertEquals(Response.Status.OK.statusCode, listed.status)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(subject), listed.entity as List<InformationRequestSubjectDto>)
        assertEquals(Response.Status.FORBIDDEN.statusCode, refused.status)
        verify(subjectService, never()).assignSubject(any())
    }
}
