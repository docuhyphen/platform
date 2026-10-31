package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.resource.informationrequest.template.operations.InformationRequestTemplateResourceOperations
import com.docuhyphen.app.api.resource.model.CloneInformationRequestTemplateRequest
import com.docuhyphen.app.api.resource.model.InformationRequestTemplateNewVersionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateLifecycleService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplatePublicationService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
        val resourceClass = InformationRequestTemplateResourceOperations::class.java
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

    @Test
    fun `no lifecycle operation is exposed as a generic status setter`()
    {
        val paths = InformationRequestTemplateResourceOperations::class.java.declaredMethods
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
    fun `commands state their preconditions and idempotency keys as headers`() {
        val methods = InformationRequestTemplateResourceOperations::class.java.declaredMethods.associateBy { it.name }
        fun headers(name: String) = methods.getValue(name).parameterAnnotations
            .flatMap { it.toList() }
            .filterIsInstance<HeaderParam>()
            .map { it.value }

        assertEquals(listOf("Idempotency-Key"), headers("create"))
        assertEquals(listOf("If-Match"), headers("replaceDraftConfiguration"))
        assertEquals(listOf("If-Match", "Idempotency-Key"), headers("publishDraft"))
        assertEquals(listOf("Idempotency-Key"), headers("createDraftVersion"))
        assertEquals(listOf("Idempotency-Key"), headers("retireVersion"))
        assertEquals(listOf("Idempotency-Key"), headers("clone"))
        assertEquals(emptyList<String>(), headers("get"))
    }

    @Test
    fun `create and get delegate to the authoring service and answer with the draft tag`()
    {
        val request = createRequest()
        whenever(authoringService.createTemplate(request, "create-key")).thenReturn(template)
        whenever(authoringService.getTemplate(templateId)).thenReturn(template)

        val created = resource.create(request, "create-key")
        val fetched = resource.get(templateId.toString())

        assertEquals(Response.Status.CREATED.statusCode, created.status)
        assertSame(template, created.entity)
        assertEquals(DRAFT_TAG, created.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.OK.statusCode, fetched.status)
        assertSame(template, fetched.entity)
        assertEquals(DRAFT_TAG, fetched.getHeaderString(HttpHeaders.ETAG))
    }

    @Test
    fun `a Template with no draft is answered without a tag`() {
        whenever(authoringService.getTemplate(templateId)).thenReturn(template.copy(draftETag = null))

        assertNull(resource.get(templateId.toString()).getHeaderString(HttpHeaders.ETAG))
    }

    @Test
    fun `draft configuration and publication pass the stated tag to the owning services`()
    {
        val configuration = configurationRequest()
        whenever(authoringService.replaceDraftConfiguration(any(), any(), any())).thenReturn(template)
        whenever(publicationService.publishTemplate(any(), any(), any())).thenReturn(template)

        val configured = resource.replaceDraftConfiguration(templateId.toString(), configuration, DRAFT_TAG)
        val published = resource.publishDraft(templateId.toString(), DRAFT_TAG, "publish-key")

        assertEquals(Response.Status.OK.statusCode, configured.status)
        assertSame(template, configured.entity)
        assertEquals(DRAFT_TAG, configured.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.OK.statusCode, published.status)
        assertSame(template, published.entity)
        verify(authoringService).replaceDraftConfiguration(
            templateId,
            configuration,
            CommandPrecondition.ExpectedRevision(DRAFT_TAG),
        )
        verify(publicationService).publishTemplate(
            templateId,
            CommandPrecondition.ExpectedRevision(DRAFT_TAG),
            "publish-key"
        )
    }

    @Test
    fun `a wildcard If-Match never stands in for the draft tag`() {
        whenever(authoringService.replaceDraftConfiguration(any(), any(), any())).thenReturn(template)
        whenever(publicationService.publishTemplate(any(), any(), any())).thenReturn(template)
        val configuration = configurationRequest()

        resource.replaceDraftConfiguration(templateId.toString(), configuration, "*")
        resource.publishDraft(templateId.toString(), " * ", "publish-key")

        verify(authoringService).replaceDraftConfiguration(templateId, configuration, CommandPrecondition.Absent)
        verify(publicationService).publishTemplate(templateId, CommandPrecondition.Absent, "publish-key")
    }

    @Test
    fun `a change that states no tag is refused as needing one and a stale one as out of date`() {
        whenever(authoringService.replaceDraftConfiguration(any(), any(), eq(CommandPrecondition.Absent)))
            .thenThrow(CommandPreconditionException.required(DRAFT_TAG))
        whenever(publicationService.publishTemplate(any(), any(), any()))
            .thenThrow(CommandPreconditionException.stale(DRAFT_TAG))

        val unstated = resource.replaceDraftConfiguration(templateId.toString(), configurationRequest(), null)
        val stale = resource.publishDraft(templateId.toString(), "\"${UUID.randomUUID()}:1\"", "publish-key")

        assertEquals(428, unstated.status)
        assertEquals(DRAFT_TAG, unstated.getHeaderString(HttpHeaders.ETAG))
        assertEquals("COMMAND_PRECONDITION_REQUIRED", (unstated.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, stale.status)
        assertEquals(DRAFT_TAG, stale.getHeaderString(HttpHeaders.ETAG))
        assertEquals("COMMAND_PRECONDITION_STALE", (stale.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a command without its idempotency key is refused before any service is asked`() {
        val responses = listOf(
            resource.create(createRequest(), null),
            resource.publishDraft(templateId.toString(), DRAFT_TAG, " "),
            resource.createDraftVersion(templateId.toString(), InformationRequestTemplateNewVersionRequest(1), null),
            resource.retireVersion(templateId.toString(), "1", null),
            resource.clone(templateId.toString(), CloneInformationRequestTemplateRequest(1, createRequest()), ""),
        )

        responses.forEach { response ->
            assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
            assertEquals("Idempotency-Key is required", (response.entity as ResponseError).errorMessage)
        }
        verifyNoInteractions(authoringService, publicationService, lifecycleService)
    }

    @Test
    fun `a reused idempotency key with a different command is a conflict`() {
        whenever(authoringService.createTemplate(any(), any())).thenThrow(CommandReceiptConflictException())

        val response = resource.create(createRequest(), "create-key")

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals("COMMAND_RECEIPT_FINGERPRINT_CONFLICT", (response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `version lifecycle operations require exact source and target version numbers`()
    {
        whenever(lifecycleService.createDraftVersion(templateId, 2, "version-key")).thenReturn(template)
        whenever(lifecycleService.retireVersion(templateId, 2, "retire-key")).thenReturn(template)
        whenever(lifecycleService.cloneTemplate(eq(templateId), eq(2), any(), eq("clone-key"))).thenReturn(template)

        val newVersion = resource.createDraftVersion(
            templateId.toString(),
            InformationRequestTemplateNewVersionRequest(2),
            "version-key",
        )
        val retired = resource.retireVersion(templateId.toString(), "2", "retire-key")
        val cloned = resource.clone(
            templateId.toString(),
            CloneInformationRequestTemplateRequest(2, createRequest()),
            "clone-key",
        )

        assertEquals(Response.Status.CREATED.statusCode, newVersion.status)
        assertSame(template, newVersion.entity)
        assertEquals(DRAFT_TAG, newVersion.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.OK.statusCode, retired.status)
        assertSame(template, retired.entity)
        assertEquals(Response.Status.CREATED.statusCode, cloned.status)
        assertSame(template, cloned.entity)
        verify(lifecycleService).createDraftVersion(templateId, 2, "version-key")
        verify(lifecycleService).retireVersion(templateId, 2, "retire-key")
        verify(lifecycleService).cloneTemplate(templateId, 2, createRequest(), "clone-key")
    }

    @Test
    fun `invalid path and query values are bad requests`()
    {
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.get("not-a-uuid").status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.list("unknown").status)
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.createDraftVersion(
                templateId.toString(),
                InformationRequestTemplateNewVersionRequest(0),
                "version-key"
            ).status,
        )
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.retireVersion(templateId.toString(), "0", "retire-key").status,
        )
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.retireVersion(templateId.toString(), "latest", "retire-key").status,
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
        whenever(publicationService.publishTemplate(eq(templateId), any(), any()))
            .thenThrow(
                InformationRequestTemplateValidationException(
                    "Requirement recorded-note states no prompt",
                    sectionKey = "collected-data",
                    requirementKey = "recorded-note",
                ),
            )
        whenever(authoringService.replaceDraftConfiguration(eq(templateId), any(), any()))
            .thenThrow(
                InformationRequestTemplateValidationException(
                    "Repeatable group entries names itself as its own parent",
                    groupKey = "entries",
                ),
            )

        val published = resource.publishDraft(templateId.toString(), DRAFT_TAG, "publish-key")
        val configured = resource.replaceDraftConfiguration(templateId.toString(), configurationRequest(), DRAFT_TAG)

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

    @Test
    fun `a condition rule refusal names the rule apart from any group`() {
        whenever(publicationService.publishTemplate(eq(templateId), any(), any()))
            .thenThrow(
                InformationRequestTemplateValidationException(
                    "Condition rule shared-key compares nothing",
                    conditionRuleKey = "shared-key",
                ),
            )

        val response = resource.publishDraft(templateId.toString(), DRAFT_TAG, "publish-key")

        val refusal = response.entity as InformationRequestTemplateRefusalDto
        assertEquals("shared-key", refusal.conditionRuleKey)
        assertNull(refusal.groupKey)
    }

    @Test
    fun `a storage rule the services did not anticipate answers 409 with a stable code instead of 500`() {
        whenever(authoringService.replaceDraftConfiguration(eq(templateId), any(), any())).thenThrow(
            jakarta.persistence.PersistenceException(
                "could not execute statement",
                java.sql.SQLException("check constraint refused the row", "23514"),
            ),
        )
        whenever(publicationService.publishTemplate(eq(templateId), any(), any())).thenThrow(
            RuntimeException(
                "transaction rolled back",
                jakarta.persistence.PersistenceException(
                    "could not execute statement",
                    java.sql.SQLException("trigger refused the row", "P0001"),
                ),
            ),
        )

        val configured = resource.replaceDraftConfiguration(templateId.toString(), configurationRequest(), DRAFT_TAG)
        val published = resource.publishDraft(templateId.toString(), DRAFT_TAG, "publish-key")

        listOf(configured, published).forEach { response ->
            assertEquals(Response.Status.CONFLICT.statusCode, response.status)
            assertEquals(
                InformationRequestErrorCatalog.CONSTRAINT_CONFLICT,
                (response.entity as ResponseError).reasonCode
            )
        }
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
        draftETag = DRAFT_TAG,
        createdAt = Timestamp.from(Instant.now()),
        updatedAt = Timestamp.from(Instant.now()),
    )

    private companion object {
        val DRAFT_TAG = "\"${UUID.randomUUID()}:4\""
    }
}
