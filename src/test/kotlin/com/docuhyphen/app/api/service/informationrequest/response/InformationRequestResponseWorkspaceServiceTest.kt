package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.fields.FieldValueReadCommand
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestWorkspacePurpose
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestResponseEditLock
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestSupportingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.response.InformationRequestResponseRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestNoAuthRequestResource
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestResource
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAuthorActionService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceDeploymentPolicy
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestSupportingEvidenceLinkService
import com.docuhyphen.app.api.service.informationrequest.execution.ExecutionStandingTestDoubles
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFirstViewService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupAuthorizationService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestResponseWorkspaceServiceTest
{
    @Test
    fun `false unknown and missing condition results cannot expose retained Fields through a fresh workspace`()
    {
        for (state in listOf(InformationRequestConditionEvaluationState.FALSE, InformationRequestConditionEvaluationState.UNKNOWN, null))
        {
            val fixture = Fixture()
            whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(state?.let {
                listOf(InformationRequestConditionEvaluationProjection("conditional-data", 1, it,
                    sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"))
            }.orEmpty())
            val result = fixture.service.load(fixture.request.id, fixture.access)
            assertFalse(result.responses.any { it.fieldValues.any { field -> field.value == JsonPrimitive("retained-secret") } })
            assertFalse(result.schemaAssignment?.fields.orEmpty().any { it.value == JsonPrimitive("retained-secret") })
            assertTrue(result.responses.any { it.informationRequestRequirementId == fixture.visible.id })
        }
    }

    @Test
    fun `a hidden envelope cannot be replaced with its retained Field values even while its condition is true`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]")))
        whenever(fixture.responses.findAllForRequest(fixture.request.id)).thenReturn(listOf(InformationRequestResponse().apply {
            informationRequestId = fixture.request.id
            informationRequestRequirementId = fixture.hidden.id
            activeInResponse = false
        }))
        val result = fixture.service.load(fixture.request.id, fixture.access)
        assertFalse(result.responses.any { it.fieldValues.any { field -> field.value == JsonPrimitive("retained-secret") } })
        assertFalse(result.schemaAssignment?.fields.orEmpty().any { it.value == JsonPrimitive("retained-secret") })
    }

    @Test
    fun `the same collected Field stays visible in one occurrence while another is hidden`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))
        whenever(fixture.fields.getAssignment(any<FieldValueReadCommand>())).thenAnswer {
            val command = it.getArgument<FieldValueReadCommand>(0)
            responseFieldProjection(fixture.request.id, listOf(fixture.hiddenFieldId to
                if (command.valueSet == FieldValueSetRef.Occurrence("items[1]")) "visible-answer" else "retained-secret"))
        }
        val result = fixture.service.load(fixture.request.id, fixture.access)
        assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
        assertEquals(JsonPrimitive("visible-answer"), result.responses.single().fieldValues.single().value)
        assertTrue(result.schemaAssignment!!.fields.isEmpty())
    }

    @Test
    fun `a removed occurrence's requirement is excluded from the workspace though its row still exists`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val result = fixture.service.load(fixture.request.id, fixture.access)

        assertTrue(result.occurrences.isEmpty())
        assertFalse(result.responses.any { it.informationRequestRequirementId == fixture.hidden.id })
        assertFalse(result.responses.any { it.informationRequestRequirementId == fixture.visible.id })
    }

    @Test
    fun `each answer carries the lock its submission places on it and an open answer carries none`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.lockService.editLocks(eq(fixture.request.id), any())).thenAnswer {
            val asked = it.getArgument<Collection<UUID>>(1)
            assertTrue(fixture.visible.id in asked) { "the locks are read for the answers shown" }
            mapOf(fixture.visible.id to InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION)
        }

        val result = fixture.service.load(fixture.request.id, fixture.access)

        assertEquals(
            InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION,
            result.responses.single { it.informationRequestRequirementId == fixture.visible.id }.editLock,
        )
        assertTrue(
            result.responses.filter { it.informationRequestRequirementId != fixture.visible.id }
                .all { it.editLock == null },
        )
    }

    @Test
    fun `a Document answer carries how many files are attached to it and other answers carry no count`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(
            fixture.evidenceQueries.attachedFileCounts(
                eq(fixture.request.id),
                any(),
                eq(fixture.access)
            )
        ).thenAnswer {
            it.getArgument<Collection<UUID>>(1).associateWith { 2 }
        }

        val fieldOnly = fixture.service.load(fixture.request.id, fixture.access)
        assertNull(fieldOnly.responses.single { it.informationRequestRequirementId == fixture.visible.id }.evidenceFileCount)

        fixture.updateTemplate { version ->
            version.copy(sections = version.sections.map { section ->
                section.copy(requirements = section.requirements.map { requirement ->
                    if (requirement.id == fixture.visible.sourceTemplateBindingId)
                        requirement.copy(
                            requirementType = InformationRequestRequirementType.DOCUMENT,
                            collectedFieldDefinitionId = null
                        )
                    else requirement
                })
            })
        }
        val withDocument = fixture.service.load(fixture.request.id, fixture.access)

        assertEquals(
            2,
            withDocument.responses.single { it.informationRequestRequirementId == fixture.visible.id }.evidenceFileCount
        )
    }

    @Test
    fun `the workspace states whether this deployment accepts evidence uploads and scans them for malware`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())

        whenever(fixture.evidenceUpload.uploadAvailable()).thenReturn(true)
        whenever(fixture.evidenceUpload.malwareScanningConfigured()).thenReturn(true)
        val scanned = fixture.service.load(fixture.request.id, fixture.access)
        assertTrue(scanned.evidenceUploadAvailable)
        assertTrue(scanned.evidenceMalwareScanning)

        whenever(fixture.evidenceUpload.uploadAvailable()).thenReturn(false)
        whenever(fixture.evidenceUpload.malwareScanningConfigured()).thenReturn(false)
        val unscanned = fixture.service.load(fixture.request.id, fixture.access)
        assertFalse(unscanned.evidenceUploadAvailable)
        assertFalse(unscanned.evidenceMalwareScanning)
    }

    @Test
    fun `a management read is refused to a caller who does not manage the request and never records a first view`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())

        fixture.managesRequest(false)
        assertThrows(ForbiddenException::class.java) {
            fixture.service.load(fixture.request.id, fixture.access, InformationRequestWorkspacePurpose.MANAGEMENT)
        }
        fixture.managesRequest(true)
        val managed =
            fixture.service.load(fixture.request.id, fixture.access, InformationRequestWorkspacePurpose.MANAGEMENT)
        verify(fixture.firstViews, never()).recordIfFirst(any(), any())
        fixture.service.load(fixture.request.id, fixture.access)

        assertEquals(fixture.request.id, managed.request.id)
        verify(fixture.firstViews).recordIfFirst(fixture.request.id, fixture.access)
    }

    @Test
    fun `the workspace states why changes are paused only to a caller who manages the request`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.standing.standingOf(eq(fixture.request), anyOrNull())).thenReturn(
            InformationRequestExecutionStanding(
                InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            ),
        )

        whenever(
            fixture.authorization.authorize(
                fixture.access.principal,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                ResourceRef.informationRequest(fixture.request.id),
                fixture.access.authorization,
            ),
        ).thenReturn(Decision.Deny("NOT_MANAGER", "not a manager"))
        val participant = fixture.service.load(fixture.request.id, fixture.access).executionStanding
        whenever(
            fixture.authorization.authorize(
                fixture.access.principal,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                ResourceRef.informationRequest(fixture.request.id),
                fixture.access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val manager = fixture.service.load(fixture.request.id, fixture.access).executionStanding

        assertEquals(InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED, participant.kind)
        assertEquals(null, participant.reason)
        assertEquals(InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED, manager.kind)
        assertEquals(InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED, manager.reason)
    }

    @Test
    fun `both access surfaces disclose owner identifiers only to a caller who manages the request and the Template identifiers to every reader`() {
        val fixture = Fixture()
        fixture.request.ownerOrganizationId = UUID.randomUUID()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())

        fixture.managesRequest(false)
        val participantHeaders = fixture.loadRequests() + fixture.loadWorkspaces().map { it.request }
        val participantTemplates = fixture.loadWorkspaces().map { it.templateVersion }
        fixture.managesRequest(true)
        val managerHeaders = fixture.loadRequests() + fixture.loadWorkspaces().map { it.request }
        val managerTemplates = fixture.loadWorkspaces().map { it.templateVersion }

        assertEquals(4, participantHeaders.size)
        participantHeaders.forEach {
            assertEquals(null, it.ownerOrganizationId)
            assertEquals(null, it.ownerUserId)
            assertEquals(fixture.request.templateVersionId, it.templateVersionId)
            assertEquals(fixture.request.ownerType, it.ownerType)
        }
        managerHeaders.forEach {
            assertEquals(fixture.request.ownerOrganizationId, it.ownerOrganizationId)
            assertEquals(fixture.request.templateVersionId, it.templateVersionId)
        }
        assertEquals(
            managerTemplates.map { it.id to it.templateDefinitionId },
            participantTemplates.map { it.id to it.templateDefinitionId })
        participantTemplates.forEach { assertEquals(fixture.request.templateVersionId, it.id) }
    }

    @Test
    fun `a participant's workspace leaves out the Template's reuse purpose and content hash that managers see`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        val version = requireNotNull(fixture.versions.findById(fixture.request.templateVersionId))
        val authored = fixture.templates.loadVersion(version)
        whenever(fixture.templates.loadVersion(version)).thenReturn(
            authored.copy(contentHashSha256 = "a".repeat(64), factReusePurposeKey = "reuse.records"),
        )

        fixture.managesRequest(false)
        val participant = fixture.loadWorkspaces().map { it.templateVersion }
        fixture.managesRequest(true)
        val manager = fixture.loadWorkspaces().map { it.templateVersion }

        participant.forEach {
            assertEquals(null, it.contentHashSha256)
            assertEquals(null, it.factReusePurposeKey)
            assertEquals(null, it.schemaVersionId)
        }
        manager.forEach {
            assertEquals("a".repeat(64), it.contentHashSha256)
            assertEquals("reuse.records", it.factReusePurposeKey)
        }
    }

    @Test
    fun `the workspace offers a supplemental request only to a caller who may ask for one`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.authorization.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "not granted"))
        val respondent = fixture.service.load(fixture.request.id, fixture.access)

        whenever(
            fixture.authorization.authorize(
                fixture.access.principal,
                Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT,
                ResourceRef.informationRequest(fixture.request.id),
                fixture.access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val requester = fixture.service.load(fixture.request.id, fixture.access)

        assertFalse(respondent.canRequestSupplement)
        assertTrue(requester.canRequestSupplement)
    }

    @Test
    fun `both workspace surfaces carry the request's title`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.titleReader.titleOf(fixture.request)).thenReturn("Periodic records request")

        fixture.loadWorkspaces().forEach { assertEquals("Periodic records request", it.title) }
    }

    @Test
    fun `both workspace surfaces name the respondent they were read for, and another respondent gets another key`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        val account = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        whenever(fixture.query.findById(fixture.request.id, account)).thenReturn(fixture.request)

        val keys = fixture.loadWorkspaces().map { it.respondentKey }
        val accountKey = fixture.service.load(fixture.request.id, account).respondentKey

        assertTrue(keys.all { it.isNotBlank() })
        assertEquals(listOf(InformationRequestRespondentKey.of(fixture.access.principal)), keys.distinct())
        assertEquals(InformationRequestRespondentKey.of(account.principal), accountKey)
        assertNotEquals(keys.first(), accountKey)
    }

    @Test
    fun `supporting evidence links are projected only between requirements the caller may see`()
    {
        val fixture = Fixture()
        val templateLink = InformationRequestTemplateBindingEvidenceLink().apply {
            templateBindingId = fixture.visible.sourceTemplateBindingId
            supportingTemplateBindingId = fixture.hidden.sourceTemplateBindingId
            templateVersionId = fixture.request.templateVersionId
        }
        whenever(fixture.templateLinks.findForVersion(fixture.request.templateVersionId)).thenReturn(listOf(templateLink))
        fixture.storedLinks += InformationRequestSupportingEvidenceLink().apply {
            informationRequestId = fixture.request.id
            supportedRequirementId = fixture.visible.id
            supportingRequirementId = fixture.hidden.id
            templateEvidenceLinkId = templateLink.id
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]")))

        val disclosed = fixture.service.load(fixture.request.id, fixture.access)

        assertEquals(
            listOf(fixture.visible.id to fixture.hidden.id),
            disclosed.supportingEvidenceLinks.map { it.supportedRequirementId to it.supportingRequirementId },
        )

        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        val redacted = fixture.service.load(fixture.request.id, fixture.access)

        assertTrue(redacted.supportingEvidenceLinks.isEmpty())
    }

    @Test
    fun `workspace configuration omits requirements and occurrence details the caller cannot view`()
    {
        val fixture = Fixture()
        fixture.hidden.occurrencePath = "protected-items[0]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("protected-rule", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = setOf("protected-source"), fieldDefinitionIds = emptySet(),
                occurrencePath = "protected-items[0]")))

        val result = fixture.service.load(fixture.request.id, fixture.access)
        val requirements = result.templateVersion.sections.flatMap { it.requirements }

        assertEquals(listOf("item-${fixture.visible.id}"), requirements.map { it.requirementKey })
        assertFalse(result.templateVersion.groups.any { it.groupKey == "protected-items" })
        assertFalse(result.occurrences.any { it.occurrencePath == "protected-items[0]" })
        assertFalse(result.templateVersion.conditionRules.any { it.ruleKey == "protected-rule" })
        assertFalse(requirements.single().supportingEvidenceRequirementKeys.contains("item-${fixture.hidden.id}"))
        assertFalse(result.request.conditionEvaluations.any { it.ruleKey == "protected-rule" })
        assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
    }

    @Test
    fun `protected requirements the caller could see after verifying again are counted but not disclosed`() {
        val fixture = Fixture()
        fixture.requireRecentVerificationFor(fixture.hidden)
        fixture.conditionHolds(InformationRequestConditionEvaluationState.TRUE)
        val verifiedAccess = fixture.access.copy(authorization = AuthorizationContext(mfaSatisfied = true))
        whenever(fixture.query.findById(fixture.request.id, verifiedAccess)).thenReturn(fixture.request)

        val locked = fixture.service.load(fixture.request.id, fixture.access)
        val verified = fixture.service.load(fixture.request.id, verifiedAccess)

        assertEquals(1, locked.requirementsAwaitingStepUp)
        assertEquals(listOf(fixture.visible.id), locked.responses.map { it.informationRequestRequirementId })
        assertEquals(
            listOf("item-${fixture.visible.id}"),
            locked.templateVersion.sections.flatMap { it.requirements }.map { it.requirementKey })
        assertEquals(0, verified.requirementsAwaitingStepUp)
        assertEquals(
            setOf(fixture.hidden.id, fixture.visible.id),
            verified.responses.map { it.informationRequestRequirementId }.toSet()
        )
    }

    @Test
    fun `requirements that are inactive or denied for other reasons are not counted as awaiting verification`() {
        val fixture = Fixture()
        fixture.requireRecentVerificationFor(fixture.hidden)
        fixture.conditionHolds(InformationRequestConditionEvaluationState.FALSE)

        val inactive = fixture.service.load(fixture.request.id, fixture.access)

        fixture.conditionHolds(InformationRequestConditionEvaluationState.TRUE)
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<ResourceRef>(2).id == fixture.hidden.id) Decision.Deny(
                Decision.REASON_NO_GRANT,
                "Not assigned"
            ) else Decision.Allow()
        }
        val denied = fixture.service.load(fixture.request.id, fixture.access)

        assertEquals(0, inactive.requirementsAwaitingStepUp)
        assertEquals(0, denied.requirementsAwaitingStepUp)
    }

    @Test
    fun `workspace configuration keeps authorized zero occurrence group controls without exposing unmaterialized requirements`()
    {
        val fixture = Fixture()

        val result = fixture.service.load(fixture.request.id, fixture.access)
        val requirements = result.templateVersion.sections.flatMap { it.requirements }

        assertTrue(result.templateVersion.groups.any { it.groupKey == "optional-items" })
        assertFalse(result.occurrences.any { it.sourceTemplateGroupId == fixture.zeroGroupId })
        assertFalse(requirements.any { it.requirementKey == fixture.zeroRequirementKey })
        verify(fixture.groupAuthorization).authorizeGroupCreation(
            fixture.access,
            fixture.request,
            listOf(fixture.zeroBinding),
            listOf(fixture.zeroBinding),
        )
    }

    @Test
    fun `a container group whose own bindings are all in optional child groups offers its control by its subtree`() {
        val fixture = Fixture()
        fixture.updateTemplate { template ->
            template.copy(
                groups = template.groups.map { group ->
                    if (group.id == fixture.zeroGroupId) group.copy(parentGroupKey = "protected-items") else group
                },
            )
        }

        fixture.service.load(fixture.request.id, fixture.access)

        verify(fixture.groupAuthorization).authorizeGroupCreation(
            fixture.access,
            fixture.request,
            emptyList(),
            listOf(fixture.zeroBinding),
        )
    }

    @Test
    fun `both workspace surfaces name only the groups the caller may add entries to`() {
        val fixture = Fixture()

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf("optional-items"), result.creatableGroupKeys)
        }

        doThrow(ForbiddenException("Denied")).whenever(fixture.groupAuthorization)
            .authorizeGroupCreation(eq(fixture.access), eq(fixture.request), any(), eq(listOf(fixture.zeroBinding)))
        fixture.loadWorkspaces().forEach { result ->
            assertEquals(emptyList<String>(), result.creatableGroupKeys)
        }
    }

    @Test
    fun `review stages and their ordering reach only a caller who manages the request on both access surfaces`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        fixture.updateTemplate { template ->
            template.copy(
                reviewStageOrdering = InformationRequestReviewStageOrdering.PARALLEL,
                sections = template.sections + InformationRequestTemplateSectionDto(
                    UUID.randomUUID(), "withheld-section", "Withheld",
                    requirements = listOf(
                        InformationRequestTemplateRequirementDto(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "withheld-entry",
                            InformationRequestRequirementType.FIELD,
                            "Provide withheld data",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.REQUIRED,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            reviewPolicy = InformationRequestReviewPolicy.REQUIRED
                        ),
                    )
                ),
                reviewStages = listOf(
                    InformationRequestTemplateReviewStageDto(
                        id = UUID.randomUUID(),
                        stageKey = "entry-check-stage",
                        title = "Entry check stage",
                        aggregation = InformationRequestReviewAggregation.QUORUM,
                        quorumCount = 2,
                        minimumReviewerCount = 3,
                        tieResolution = InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
                        overridePermitted = true,
                        excludesResponseParties = true,
                        excludesPriorReviewers = true,
                        sectionKeys = listOf("data", "withheld-section"),
                    ),
                ),
            )
        }

        fixture.managesRequest(false)
        val participantJson = fixture.loadWorkspaces()
            .map {
                kotlinx.serialization.json.Json.encodeToString(
                    InformationRequestResponseWorkspaceDto.serializer(),
                    it
                )
            }
        fixture.managesRequest(true)
        val managerWorkspaces = fixture.loadWorkspaces()

        assertEquals(2, participantJson.size)
        participantJson.forEach { json ->
            assertFalse(json.contains("entry-check-stage"), json)
            assertFalse(json.contains("Entry check stage"), json)
            assertFalse(json.contains("withheld-section"), json)
            assertFalse(json.contains("PARALLEL"), json)
            assertFalse(json.contains("REQUIRE_OVERRIDE"), json)
        }
        managerWorkspaces.forEach { workspace ->
            assertEquals(listOf("entry-check-stage"), workspace.templateVersion.reviewStages.map { it.stageKey })
            assertEquals(InformationRequestReviewStageOrdering.PARALLEL, workspace.templateVersion.reviewStageOrdering)
        }
    }

    @Test
    fun `a Requirement that keeps a condition rule without being conditional stays visible while that rule is false`() {
        val fixture = Fixture()
        fixture.updateTemplate { template ->
            template.copy(
                sections = template.sections.map { section ->
                    section.copy(requirements = section.requirements.map {
                        if (it.id == fixture.hidden.sourceTemplateBindingId) it.copy(requiredness = InformationRequestRequiredness.REQUIRED) else it
                    })
                },
            )
        }
        fixture.conditionHolds(InformationRequestConditionEvaluationState.FALSE)

        fixture.loadWorkspaces().forEach { workspace ->
            assertTrue(workspace.responses.any { it.informationRequestRequirementId == fixture.hidden.id })
            val requirement = workspace.templateVersion.sections.flatMap { it.requirements }
                .single { it.id == fixture.hidden.sourceTemplateBindingId }
            assertEquals(null, requirement.conditionalRuleKey)
        }
    }

    @Test
    fun `workspace omits a denied sibling occurrence even when its group has a visible occurrence`()
    {
        val fixture = Fixture()
        fixture.hidden.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertEquals(listOf("items[0]"), result.occurrences.map { it.occurrencePath })
        }
    }

    @Test
    fun `workspace omits condition results from a denied sibling that uses the same rule`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertEquals(listOf("items[1]"), result.request.conditionEvaluations.map { it.occurrencePath })
        }
    }

    @Test
    fun `workspace keeps the ancestors of an authorized nested occurrence without disclosing sibling branches`()
    {
        val fixture = Fixture()
        fixture.visible.occurrencePath = "items[0]/entries[0]"
        fixture.hidden.occurrencePath = "items[1]/entries[0]"
        fixture.updateTemplate { template -> template.copy(
            sections = template.sections.map { section -> section.copy(
                requirements = section.requirements.map { it.copy(occurrenceAnchorKey = "entries") }) },
            groups = listOf(
                InformationRequestTemplateGroupDto(fixture.visibleGroupId, "items", minOccurrences = 0),
                InformationRequestTemplateGroupDto(fixture.hiddenGroupId, "entries", parentGroupKey = "items", minOccurrences = 0),
            ),
        ) }
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        val parent = fixture.occurrence("items[0]", fixture.visibleGroupId)
        val sibling = fixture.occurrence("items[1]", fixture.visibleGroupId)
        val child = fixture.occurrence(fixture.visible.occurrencePath, fixture.hiddenGroupId, parent.id)
        val deniedChild = fixture.occurrence(fixture.hidden.occurrencePath, fixture.hiddenGroupId, sibling.id)
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(parent, sibling, child, deniedChild))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(parent.id, child.id), result.occurrences.map { it.id })
            assertEquals(parent.id, result.occurrences.last().parentOccurrenceId)
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
        }
    }

    @Test
    fun `redacting an undisclosed condition does not activate its hidden response`()
    {
        val fixture = Fixture()
        fixture.updateTemplate { template -> template.copy(
            conditionRules = template.conditionRules.map { rule -> rule.copy(predicates = listOf(
                InformationRequestTemplateConditionPredicateDto(UUID.randomUUID(),
                    sourceRequirementKey = "undisclosed-source", operator = FieldOperator.EQUALS,
                    value = JsonPrimitive("restricted")),
            )) },
        ) }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = setOf("undisclosed-source"), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]")))

        fixture.loadWorkspaces().forEach { result ->
            assertTrue(result.templateVersion.conditionRules.isEmpty())
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertFalse(result.schemaAssignment!!.bindings.any { it.fieldDefinitionId == fixture.hiddenFieldId })
        }
    }

    @Test
    fun `request lookups omit condition results from a denied sibling on both access surfaces`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))

        fixture.loadRequests().forEach { result ->
            assertEquals(listOf("items[1]"), result.conditionEvaluations.map { it.occurrencePath })
        }
    }

    @Test
    fun `authorized optional child controls retain their empty parent occurrence`()
    {
        val fixture = Fixture()
        fixture.updateTemplate { template -> template.copy(groups = template.groups.map { group ->
            if (group.id == fixture.zeroGroupId) group.copy(parentGroupKey = "items") else group
        }) }
        val parent = fixture.occurrence("items[0]", fixture.visibleGroupId)
        whenever(fixture.requirements.findForRequest(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(listOf(parent))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(parent.id), result.occurrences.map { it.id })
            assertTrue(result.templateVersion.groups.any { it.id == fixture.zeroGroupId })
            assertTrue(result.responses.isEmpty())
        }

        doThrow(io.quarkus.security.ForbiddenException("Denied")).whenever(fixture.groupAuthorization)
            .authorizeGroupCreation(eq(fixture.access), eq(fixture.request), any(), eq(listOf(fixture.zeroBinding)))
        fixture.loadWorkspaces().forEach { result ->
            assertTrue(result.occurrences.isEmpty())
            assertTrue(result.templateVersion.groups.isEmpty())
        }
    }

    @Test
    fun `a parent occurrence holding another party's answers is still shown to the party that adds its nested rows`() {
        val fixture = Fixture()
        fixture.updateTemplate { template ->
            template.copy(groups = template.groups.map { group ->
                if (group.id == fixture.zeroGroupId) group.copy(parentGroupKey = "items") else group
            })
        }
        val parent = fixture.occurrence("items[0]", fixture.visibleGroupId)
        whenever(fixture.requirements.findForRequest(fixture.request.id)).thenReturn(listOf(fixture.hidden))
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(listOf(parent))
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            if (it.getArgument<ResourceRef>(2).id == fixture.hidden.id) Decision.Deny(
                Decision.REASON_NO_GRANT,
                "Denied"
            ) else Decision.Allow()
        }

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(parent.id), result.occurrences.map { it.id })
            assertTrue(result.responses.none { it.informationRequestRequirementId == fixture.hidden.id })
        }
    }

    @Test
    fun `a readable source in another branch cannot disclose a condition driven by a denied local source`()
    {
        val fixture = Fixture()
        val readableSource = InformationRequestRequirement().apply {
            informationRequestId = fixture.request.id
            sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
            sourceTemplateRequirementId = fixture.hidden.sourceTemplateRequirementId
            occurrencePath = "items[1]"
        }
        whenever(fixture.requirements.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.hidden, fixture.visible, readableSource))
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        fixture.updateTemplate { template -> template.copy(
            sections = template.sections.map { section -> section.copy(requirements = section.requirements.map {
                it.copy(
                    conditionalRuleKey = if (it.id == fixture.visible.sourceTemplateBindingId) "conditional-data" else null,
                    requiredness = if (it.id == fixture.visible.sourceTemplateBindingId) InformationRequestRequiredness.CONDITIONAL
                    else it.requiredness,
                )
            }) },
            conditionRules = listOf(InformationRequestTemplateConditionRuleDto(UUID.randomUUID(), "conditional-data", 1,
                predicates = listOf(InformationRequestTemplateConditionPredicateDto(UUID.randomUUID(),
                    fieldDefinitionId = fixture.hiddenFieldId, operator = FieldOperator.EQUALS, value = JsonPrimitive("restricted"))))),
        ) }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = setOf(fixture.hiddenFieldId), occurrencePath = "items[0]")))

        fixture.loadWorkspaces().forEach { result ->
            assertTrue(result.request.conditionEvaluations.isEmpty())
            assertTrue(fixture.visible.id in result.responses.map { it.informationRequestRequirementId })
        }
        fixture.loadRequests().forEach { assertTrue(it.conditionEvaluations.isEmpty()) }

        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf("items[0]"), result.request.conditionEvaluations.map { it.occurrencePath })
        }
        fixture.loadRequests().forEach { result ->
            assertEquals(listOf("items[0]"), result.conditionEvaluations.map { it.occurrencePath })
        }
    }

    @Test
    fun `the workspace says whether the Exchange was accepted so answers can be saved`() {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.parentState.snapshot(fixture.request.exchangeId))
            .thenReturn(InformationRequestParentSnapshot(ExchangeStatus.INITIATED))

        assertFalse(fixture.service.load(fixture.request.id, fixture.access).exchangeAccepted)

        whenever(fixture.parentState.snapshot(fixture.request.exchangeId))
            .thenReturn(InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED))

        assertTrue(fixture.service.load(fixture.request.id, fixture.access).exchangeAccepted)
    }

    private class Fixture
    {
        val request = InformationRequest().apply { exchangeId = UUID.randomUUID(); templateVersionId = UUID.randomUUID() }
        val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        val hiddenFieldId = UUID.randomUUID()
        val visibleFieldId = UUID.randomUUID()
        val zeroFieldId = UUID.randomUUID()
        val visibleGroupId = UUID.randomUUID()
        val hiddenGroupId = UUID.randomUUID()
        val zeroGroupId = UUID.randomUUID()
        val hidden = requirement()
        val visible = requirement()
        val zeroRequirementId = UUID.randomUUID()
        val zeroRequirementKey = "item-$zeroRequirementId"
        val zeroBinding = InformationRequestTemplateRequirementBinding().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = zeroRequirementId
            templateSectionId = UUID.randomUUID()
            prompt = "Provide optional data"
            occurrenceAnchorKey = "optional-items"
            collectedFieldDefinitionId = zeroFieldId
        }
        val conditions = mock<InformationRequestConditionEvaluationService>()
        val responses = mock<InformationRequestResponseRepository>()
        val query = mock<InformationRequestQueryService>()
        val versions = mock<InformationRequestTemplateVersionRepository>()
        val templates = mock<InformationRequestTemplateProjectionLoader>()
        val requirements = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val fields = mock<SchemaAssignmentService>()
        val authorization = mock<AuthorizationService>()
        val groupAuthorization = mock<InformationRequestGroupAuthorizationService>()
        val storedLinks = mutableListOf<InformationRequestSupportingEvidenceLink>()
        val linkRepository = mock<InformationRequestSupportingEvidenceLinkRepository> {
            on { findForRequest(any()) }.thenAnswer { storedLinks.toList() }
        }
        val templateLinks = mock<InformationRequestTemplateBindingEvidenceLinkRepository>()
        val supportingLinks =
            InformationRequestSupportingEvidenceLinkService(
                templateLinks,
                requirements,
                linkRepository,
                occurrenceRepository
            )
        val evidenceUpload = mock<InformationRequestEvidenceDeploymentPolicy>()
        val titleReader = mock<InformationRequestTitleReader>()
        val standing = mock<InformationRequestExecutionStandingService>()
        val lockService = mock<InformationRequestSubmissionLockService>()
        val evidenceQueries = mock<InformationRequestEvidenceQueryService>()
        val firstViews = mock<InformationRequestFirstViewService>()
        val parentState = mock<ExchangeLifecycleStateService>()
        val service = InformationRequestResponseWorkspaceService(
            query, versions, templates, occurrenceRepository, requirements,
            responses,
            bindingRepository,
            fields,
            authorization,
            conditions,
            groupAuthorization,
            supportingLinks,
            evidenceUpload,
            firstViews,
            titleReader,
            standing,
            InformationRequestHeaderProjectionService(authorization, ExecutionStandingTestDoubles.active()),
            lockService,
            evidenceQueries,
            InformationRequestAuthorActionService(authorization, parentState),
            parentState,
        )

        fun requireRecentVerificationFor(requirement: InformationRequestRequirement) {
            whenever(authorization.authorize(any(), any(), any(), any())).thenAnswer {
                val protectedTarget = it.getArgument<ResourceRef>(2).id == requirement.id
                if (protectedTarget && !it.getArgument<AuthorizationContext>(3).mfaSatisfied)
                    Decision.Deny(Decision.REASON_NO_GRANT, "Recent verification required")
                else
                    Decision.Allow()
            }
        }

        fun managesRequest(manages: Boolean) {
            whenever(
                authorization.authorize(
                    access.principal,
                    Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                    ResourceRef.informationRequest(request.id),
                    access.authorization,
                ),
            ).thenReturn(if (manages) Decision.Allow() else Decision.Deny(Decision.REASON_NO_GRANT, "not a manager"))
        }

        fun conditionHolds(state: InformationRequestConditionEvaluationState) {
            whenever(conditions.evaluate(request.id)).thenReturn(
                listOf(
                    InformationRequestConditionEvaluationProjection(
                        "conditional-data", 1, state,
                        sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"
                    )
                )
            )
        }

        fun updateTemplate(transform: (InformationRequestTemplateVersionDto) -> InformationRequestTemplateVersionDto)
        {
            val version = versions.findById(request.templateVersionId)!!
            val updated = transform(templates.loadVersion(version))
            whenever(templates.loadVersion(version)).thenReturn(updated)
        }

        fun occurrence(path: String, groupId: UUID, parentId: UUID? = null) = InformationRequestGroupOccurrence().apply {
            informationRequestId = request.id
            sourceTemplateGroupId = groupId
            occurrencePath = path
            parentOccurrenceId = parentId
        }

        fun loadWorkspaces(): List<InformationRequestResponseWorkspaceDto>
        {
            val accessFactory = mock<InformationRequestAccessContextFactory>()
            whenever(accessFactory.currentAuthenticated()).thenReturn(access)
            val authenticated =
                InformationRequestResource(
                    mock(),
                    mock(),
                    accessFactory,
                    service,
                    mock(),
                    mock(),
                    InformationRequestHeaderProjectionService(authorization, ExecutionStandingTestDoubles.active())
                )
            val resolver = InformationRequestNoAuthAccessDoubles.current()
            whenever(resolver.resolve(request.id, "bootstrap", "session")).thenReturn(access)
            val noAuth = InformationRequestNoAuthRequestResource(resolver, mock(), mock(), mock(), service)
            return listOf(
                authenticated.responseWorkspace(request.id.toString(), null),
                noAuth.responseWorkspace(request.id.toString(), "bootstrap", "session"),
            ).map { response ->
                assertEquals(200, response.status)
                response.entity as InformationRequestResponseWorkspaceDto
            }
        }

        fun loadRequests(): List<InformationRequestDto>
        {
            val accessFactory = mock<InformationRequestAccessContextFactory>()
            whenever(accessFactory.currentAuthenticated()).thenReturn(access)
            val authenticated =
                InformationRequestResource(
                    mock(),
                    mock(),
                    accessFactory,
                    service,
                    mock(),
                    mock(),
                    InformationRequestHeaderProjectionService(authorization, ExecutionStandingTestDoubles.active())
                )
            val resolver = InformationRequestNoAuthAccessDoubles.current()
            whenever(resolver.resolve(request.id, "bootstrap", "session")).thenReturn(access)
            val noAuth = InformationRequestNoAuthRequestResource(resolver, mock(), mock(), mock(), service)
            return listOf(
                authenticated.get(request.id.toString()),
                noAuth.get(request.id.toString(), "bootstrap", "session"),
            ).map { response ->
                assertEquals(200, response.status)
                response.entity as InformationRequestDto
            }
        }

        init
        {
            val version = InformationRequestTemplateVersion().apply { id = request.templateVersionId }
            whenever(titleReader.titleOf(any())).thenReturn("Collection request")
            whenever(query.findById(request.id, access)).thenReturn(request)
            whenever(versions.findById(version.id)).thenReturn(version)
            whenever(templates.loadVersion(version)).thenReturn(InformationRequestTemplateVersionDto(version.id,
                UUID.randomUUID(), 1, InformationRequestTemplateStatus.PUBLISHED,
                sections = listOf(InformationRequestTemplateSectionDto(UUID.randomUUID(), "data", "Data",
                    requirements = listOf(
                        template(hidden, hiddenFieldId, "conditional-data"),
                        template(visible, visibleFieldId, null),
                        InformationRequestTemplateRequirementDto(
                            zeroBinding.id,
                            zeroRequirementId,
                            zeroRequirementKey,
                            InformationRequestRequirementType.FIELD,
                            "Provide optional data",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.OPTIONAL,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                            occurrenceAnchorKey = "optional-items",
                            collectedFieldDefinitionId = zeroFieldId,
                        ),
                    ))),
                groups = listOf(
                    InformationRequestTemplateGroupDto(visibleGroupId, "items", minOccurrences = 0),
                    InformationRequestTemplateGroupDto(hiddenGroupId, "protected-items", minOccurrences = 0),
                    InformationRequestTemplateGroupDto(zeroGroupId, "optional-items", minOccurrences = 0),
                ),
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleDto(
                        UUID.randomUUID(),
                        "conditional-data",
                        1,
                    ),
                    InformationRequestTemplateConditionRuleDto(
                        UUID.randomUUID(),
                        "protected-rule",
                        1,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateDto(
                                UUID.randomUUID(),
                                sourceRequirementKey = "protected-source",
                                operator = FieldOperator.EQUALS,
                                value = JsonPrimitive("protected-literal"),
                            ),
                        ),
                    ),
                ),
                createdAt = Timestamp.from(Instant.now())))
            whenever(bindingRepository.findOrdered(version.id)).thenReturn(listOf(zeroBinding))
            whenever(requirements.findForRequest(request.id)).thenReturn(listOf(hidden, visible))
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                requirements.findForRequest(request.id).map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            sourceTemplateGroupId = if (path.startsWith("protected-items[")) hiddenGroupId else visibleGroupId
                            occurrencePath = path
                        }
                    }
            }
            whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
            whenever(standing.standingOf(any(), anyOrNull())).thenReturn(
                InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE),
            )
            whenever(fields.getAssignment(any<FieldValueReadCommand>())).thenReturn(
                responseFieldProjection(request.id, listOf(hiddenFieldId to "retained-secret", visibleFieldId to "visible-answer")))
        }

        private fun requirement() = InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateBindingId = UUID.randomUUID()
            sourceTemplateRequirementId = UUID.randomUUID()
            occurrencePath = "items[0]"
        }

        private fun template(requirement: InformationRequestRequirement, fieldId: UUID, rule: String?) =
            InformationRequestTemplateRequirementDto(requirement.sourceTemplateBindingId, requirement.sourceTemplateRequirementId,
                "item-${requirement.id}", InformationRequestRequirementType.FIELD, "Provide data",
                responseMode = InformationRequestResponseMode.PROVIDE,
                requiredness = if (rule == null) InformationRequestRequiredness.OPTIONAL else InformationRequestRequiredness.CONDITIONAL,
                contributorRole = InformationRequestContributorRole.CONTRIBUTOR, reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                conditionalRuleKey = rule, occurrenceAnchorKey = occurrenceAnchorKey(requirement),
                collectedFieldDefinitionId = fieldId,
                supportingEvidenceRequirementKeys = if (requirement == visible) listOf("item-${hidden.id}") else emptyList())

        private fun occurrenceAnchorKey(requirement: InformationRequestRequirement): String? =
            if (InformationRequestOccurrencePath.isRoot(requirement.occurrencePath))
                null
            else
                requirement.occurrencePath.substringBefore("[")
    }
}

internal fun responseFieldProjection(requestId: UUID, values: List<Pair<UUID, String>>): SchemaAssignmentDto
{
    val bindings = values.mapIndexed { index, (id, _) -> SchemaFieldBindingDto(id, id, id, "process", "field-$index",
        "Process data", FieldValueType.SHORT_TEXT, index, isRequired = false, isReadOnly = false,
        visibility = FieldDataClassification.PUBLIC, constraints = FieldConstraints()) }
    return SchemaAssignmentDto(UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, requestId, UUID.randomUUID(),
        UUID.randomUUID(), "process", "Process data", 1, SchemaAssignmentSource.API, Timestamp.from(Instant.now()),
        bindings = bindings, fields = values.map { (id, value) -> FieldValueDto(id, id, "process", "data", "Data",
            FieldValueType.SHORT_TEXT, false, JsonPrimitive(value)) }, etag = "\"fields-1\"")
}
