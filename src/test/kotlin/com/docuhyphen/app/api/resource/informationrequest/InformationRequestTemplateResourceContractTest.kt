package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateLifecycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplatePublicationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateValidationException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * The Template administration resource exposes the service boundary without owning lifecycle or
 * authorization decisions. These tests pin only the HTTP shape, request parsing, service delegation,
 * and stable error mapping that clients can rely on.
 */
class InformationRequestTemplateResourceContractTest
{
    private val authoringService = mock<InformationRequestTemplateAuthoringService>()
    private val publicationService = mock<InformationRequestTemplatePublicationService>()
    private val lifecycleService = mock<InformationRequestTemplateLifecycleService>()
    private val resource = InformationRequestTemplateResource(
        authoringService,
        publicationService,
        lifecycleService,
    )
    private val templateId = UUID.randomUUID()
    private val template = templateDto(templateId)

    @Test
    fun `template administration uses resource based paths and verbs`()
    {
        val resourceClass = InformationRequestTemplateResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-request-templates", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
        assertFalse(methods.getValue("list").isAnnotationPresent(Path::class.java))
        assertTrue(methods.getValue("create").isAnnotationPresent(POST::class.java))
        assertFalse(methods.getValue("create").isAnnotationPresent(Path::class.java))
        assertEquals("/{id}", methods.getValue("get").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("replaceDraftConfiguration").isAnnotationPresent(PUT::class.java))
        assertEquals(
            "/{id}/draft/configuration",
            methods.getValue("replaceDraftConfiguration").getAnnotation(Path::class.java).value,
        )
        assertTrue(methods.getValue("publishDraft").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/draft/publication", methods.getValue("publishDraft").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("createDraftVersion").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/versions", methods.getValue("createDraftVersion").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("retireVersion").isAnnotationPresent(POST::class.java))
        assertEquals(
            "/{id}/versions/{versionNumber}/retirement",
            methods.getValue("retireVersion").getAnnotation(Path::class.java).value,
        )
        assertTrue(methods.getValue("clone").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/clones", methods.getValue("clone").getAnnotation(Path::class.java).value)
    }

    /**
     * Retirement is the creation of a subordinate lifecycle record, not the setting of a status
     * value. A status setter would let a client name any target state and would make the resource,
     * rather than the lifecycle service, the place where permitted transitions are decided.
     */
    @Test
    fun `no lifecycle operation is exposed as a generic status setter`()
    {
        val paths = InformationRequestTemplateResource::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(Path::class.java)?.value }

        assertTrue(paths.none { it.endsWith("/status") }, "Template lifecycle exposes a status setter")
    }

    @Test
    fun `list delegates the requested owner scope`()
    {
        whenever(authoringService.listTemplates(InformationRequestTemplateScopeKind.PERSONAL))
            .thenReturn(emptyList())

        val response = resource.list("personal")

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(authoringService).listTemplates(InformationRequestTemplateScopeKind.PERSONAL)
    }

    @Test
    fun `list leaves owner scope defaulting to the service when no scope is supplied`()
    {
        whenever(authoringService.listTemplates(null)).thenReturn(emptyList())

        val response = resource.list(null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(authoringService).listTemplates(null)
    }

    @Test
    fun `create and get delegate to the authoring service`()
    {
        val request = createRequest()
        whenever(authoringService.createTemplate(request)).thenReturn(template)
        whenever(authoringService.getTemplate(templateId)).thenReturn(template)

        val created = resource.create(request)
        val fetched = resource.get(templateId.toString())

        assertEquals(Response.Status.CREATED.statusCode, created.status)
        assertSame(template, created.entity)
        assertEquals(Response.Status.OK.statusCode, fetched.status)
        assertSame(template, fetched.entity)
    }

    @Test
    fun `draft configuration and publication delegate to the owning services`()
    {
        val configuration = configurationRequest()
        whenever(authoringService.replaceDraftConfiguration(templateId, configuration)).thenReturn(template)
        whenever(publicationService.publishTemplate(templateId)).thenReturn(template)

        val configured = resource.replaceDraftConfiguration(templateId.toString(), configuration)
        val published = resource.publishDraft(templateId.toString())

        assertEquals(Response.Status.OK.statusCode, configured.status)
        assertSame(template, configured.entity)
        assertEquals(Response.Status.OK.statusCode, published.status)
        assertSame(template, published.entity)
    }

    @Test
    fun `version lifecycle operations require exact source and target version numbers`()
    {
        whenever(lifecycleService.createDraftVersion(templateId, 2)).thenReturn(template)
        whenever(lifecycleService.retireVersion(templateId, 2)).thenReturn(template)
        whenever(lifecycleService.cloneTemplate(eq(templateId), eq(2), any())).thenReturn(template)

        val newVersion = resource.createDraftVersion(
            templateId.toString(),
            InformationRequestTemplateNewVersionRequest(2),
        )
        val retired = resource.retireVersion(templateId.toString(), "2")
        val cloned = resource.clone(
            templateId.toString(),
            CloneInformationRequestTemplateRequest(2, createRequest()),
        )

        assertEquals(Response.Status.CREATED.statusCode, newVersion.status)
        assertSame(template, newVersion.entity)
        assertEquals(Response.Status.OK.statusCode, retired.status)
        assertSame(template, retired.entity)
        assertEquals(Response.Status.CREATED.statusCode, cloned.status)
        assertSame(template, cloned.entity)
        verify(lifecycleService).createDraftVersion(templateId, 2)
        verify(lifecycleService).retireVersion(templateId, 2)
        verify(lifecycleService).cloneTemplate(templateId, 2, createRequest())
    }

    @Test
    fun `invalid path and query values are bad requests`()
    {
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.get("not-a-uuid").status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.list("unknown").status)
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.createDraftVersion(templateId.toString(), InformationRequestTemplateNewVersionRequest(0)).status,
        )
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.retireVersion(templateId.toString(), "0").status,
        )
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.retireVersion(templateId.toString(), "latest").status,
        )
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(authoringService.getTemplate(templateId))
            .thenThrow(InformationRequestTemplateValidationException("Invalid Template"))
            .thenThrow(IllegalStateException("Version cannot move"))
            .thenThrow(IllegalArgumentException("Template not found"))
            .thenThrow(ForbiddenException("Denied"))
            .thenThrow(UnauthorizedException("Not authenticated"))
            .thenThrow(RuntimeException("boom"))

        assertMapped(Response.Status.BAD_REQUEST, "Invalid Template")
        assertMapped(Response.Status.CONFLICT, "Version cannot move")
        assertMapped(Response.Status.NOT_FOUND, "Template not found")
        assertMapped(Response.Status.FORBIDDEN, "Denied")
        assertMapped(Response.Status.UNAUTHORIZED, "Not authenticated")
        assertMapped(Response.Status.INTERNAL_SERVER_ERROR, "Request failed")
    }

    @Test
    fun `a validation refusal names the part of the document it refuses`()
    {
        whenever(publicationService.publishTemplate(templateId))
            .thenThrow(
                InformationRequestTemplateValidationException(
                    "Requirement recorded-note states no prompt",
                    sectionKey = "collected-data",
                    requirementKey = "recorded-note",
                ),
            )
        whenever(authoringService.replaceDraftConfiguration(eq(templateId), any()))
            .thenThrow(
                InformationRequestTemplateValidationException(
                    "Repeatable group entries names itself as its own parent",
                    groupKey = "entries",
                ),
            )

        val published = resource.publishDraft(templateId.toString())
        val configured = resource.replaceDraftConfiguration(templateId.toString(), configurationRequest())

        assertEquals(Response.Status.BAD_REQUEST.statusCode, published.status)
        val publicationRefusal = published.entity as InformationRequestTemplateRefusalDto
        assertEquals("Requirement recorded-note states no prompt", publicationRefusal.errorMessage)
        assertEquals("INFORMATION_REQUEST_TEMPLATE_INVALID", publicationRefusal.reasonCode)
        assertEquals("collected-data", publicationRefusal.sectionKey)
        assertEquals("recorded-note", publicationRefusal.requirementKey)
        assertEquals(null, publicationRefusal.groupKey)
        assertEquals(null, publicationRefusal.reviewStageKey)
        val configurationRefusal = configured.entity as InformationRequestTemplateRefusalDto
        assertEquals(Response.Status.BAD_REQUEST.statusCode, configured.status)
        assertEquals("entries", configurationRefusal.groupKey)
        assertEquals(null, configurationRefusal.requirementKey)
    }

    private fun assertMapped(status: Response.Status, message: String)
    {
        val response = resource.get(templateId.toString())
        assertEquals(status.statusCode, response.status)
        val errorMessage = when (val entity = response.entity)
        {
            is InformationRequestTemplateRefusalDto -> entity.errorMessage
            else -> (entity as ResponseError).errorMessage
        }
        assertEquals(message, errorMessage)
    }

    private fun createRequest() = CreateInformationRequestTemplateRequest(
        namespace = "process",
        templateKey = "collection-pattern",
        displayName = "Collection pattern",
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL,
    )

    private fun configurationRequest() = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            InformationRequestTemplateSectionRequest(
                sectionKey = "collected-data",
                title = "Collected data",
                requirements = listOf(
                    InformationRequestTemplateRequirementRequest(
                        requirementKey = "recorded-note",
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "State the recorded note",
                        collectedFieldDefinitionId = UUID.randomUUID(),
                    ),
                ),
            ),
        ),
    )

    private fun templateDto(id: UUID) = InformationRequestTemplateDto(
        id = id,
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL,
        scopeUserId = UUID.randomUUID(),
        namespace = "process",
        templateKey = "collection-pattern",
        displayName = "Collection pattern",
        status = InformationRequestTemplateStatus.DRAFT,
        createdAt = Timestamp.from(Instant.now()),
        updatedAt = Timestamp.from(Instant.now()),
    )
}
