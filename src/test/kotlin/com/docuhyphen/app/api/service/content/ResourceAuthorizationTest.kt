package com.docuhyphen.app.api.service.content

import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.dto.UpdateCommunicationRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.Communication
import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.model.entity.SequenceDefinition
import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.BlueprintParticipantDefaultRepository
import com.docuhyphen.app.api.repository.CommunicationRepository
import com.docuhyphen.app.api.repository.DocumentLibraryRepository
import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.repository.VariableDefinitionRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.communication.CommunicationService
import com.docuhyphen.app.api.service.variable.SequenceDefinitionService
import com.docuhyphen.app.api.service.variable.VariableDefinitionService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Central authorization gates for content service read/write paths.
 *
 * Rules verified:
 *  - PERSONAL scope: only creator can read/write; APP_ADMIN bypasses ownership check.
 *  - ORG scope: auth service gate required (BLUEPRINT_VIEW/EDIT, COMMUNICATION_VIEW/EDIT,
 *    SEQUENCE_VIEW/EDIT, VARIABLE_EDIT); APP_ADMIN bypasses.
 *  - APP/PLATFORM scope: reads open to all authenticated users; writes forbidden (clone instead).
 *  - Unauthenticated principal (null from factory) throws ForbiddenException before any access check.
 */
class ResourceAuthorizationTest
{
    private val userId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun makeFactory(context: AuthorizationContext = AuthorizationContext.ANONYMOUS): AuthorizationContextFactory
    {
        val f = mock<AuthorizationContextFactory>()
        whenever(f.currentPrincipal()).thenReturn(principal)
        whenever(f.currentContext()).thenReturn(context)
        return f
    }

    private fun makeUnauthFactory(): AuthorizationContextFactory
    {
        val f = mock<AuthorizationContextFactory>()
        whenever(f.currentPrincipal()).thenReturn(null)
        return f
    }

    private fun makeAuthService(vararg allowed: Action): AuthorizationService
    {
        val svc = mock<AuthorizationService>()
        whenever(svc.authorize(any(), any(), any(), any())).thenAnswer { inv ->
            val action = inv.getArgument<Action>(1)
            if (action in allowed) Decision.Allow() else Decision.Deny("test-deny", "denied in test")
        }
        return svc
    }

    private fun makeRoleService(
        appAdmin: Boolean = false,
        orgAdmin: Boolean = false,
    ): UserRoleService
    {
        val svc = mock<UserRoleService>()
        whenever(svc.isAppAdmin(any())).thenReturn(appAdmin)
        whenever(svc.isOrgAdminIn(any(), any())).thenReturn(orgAdmin)
        return svc
    }

    // =========================================================================
    // CommunicationService — PERSONAL / ORG / PLATFORM gates
    // =========================================================================

    private fun makeCommunication(
        scope: CommunicationScope,
        createdBy: UUID? = userId,
    ): Communication = Communication().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.name = "Test Comm"
        this.subject = "Subject"
        this.body = "Body"
    }

    private fun makeCommunicationService(
        authSvc: AuthorizationService = makeAuthService(),
        factory: AuthorizationContextFactory = makeFactory(),
        roleService: UserRoleService = makeRoleService(),
        repo: CommunicationRepository = mock(),
    ): CommunicationService = CommunicationService(
        repository = repo,
        appUserRepository = mock(),
        organizationRepository = mock(),
        interpolator = mock(),
        authorizationService = authSvc,
        authorizationContextFactory = factory,
        userRoleService = roleService,
    )

    @Test
    fun `CommunicationService getTemplate - unauthenticated throws ForbiddenException`()
    {
        val comm = makeCommunication(CommunicationScope.ORG)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(factory = makeUnauthFactory(), repo = repo)
        assertThrows<ForbiddenException> { svc.getTemplate(comm.id) }
    }

    @Test
    fun `CommunicationService getTemplate - PERSONAL non-owner denied`()
    {
        val comm = makeCommunication(CommunicationScope.PERSONAL, createdBy = otherUserId)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(repo = repo)
        assertThrows<ForbiddenException> { svc.getTemplate(comm.id) }
    }

    @Test
    fun `CommunicationService getTemplate - PERSONAL owner can access`()
    {
        val comm = makeCommunication(CommunicationScope.PERSONAL, createdBy = userId)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(repo = repo)
        val dto = svc.getTemplate(comm.id)
        assertEquals("Test Comm", dto.name)
    }

    @Test
    fun `CommunicationService getTemplate - ORG COMMUNICATION_VIEW denied`()
    {
        val comm = makeCommunication(CommunicationScope.ORG)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> { svc.getTemplate(comm.id) }
    }

    @Test
    fun `CommunicationService getTemplate - ORG COMMUNICATION_VIEW allowed`()
    {
        val comm = makeCommunication(CommunicationScope.ORG)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(authSvc = makeAuthService(Action.COMMUNICATION_VIEW), repo = repo)
        val dto = svc.getTemplate(comm.id)
        assertEquals("Test Comm", dto.name)
    }

    @Test
    fun `CommunicationService getTemplate - PLATFORM open to any authenticated user`()
    {
        val comm = makeCommunication(CommunicationScope.PLATFORM)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        // No actions allowed — still accessible because PLATFORM read is open
        val svc = makeCommunicationService(authSvc = makeAuthService(), repo = repo)
        val dto = svc.getTemplate(comm.id)
        assertEquals("Test Comm", dto.name)
    }

    @Test
    fun `CommunicationService getTemplate - APP_ADMIN bypasses PERSONAL ownership check`()
    {
        val comm = makeCommunication(CommunicationScope.PERSONAL, createdBy = otherUserId)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(roleService = makeRoleService(appAdmin = true), repo = repo)
        val dto = svc.getTemplate(comm.id)
        assertEquals("Test Comm", dto.name)
    }

    @Test
    fun `CommunicationService updateTemplate - PERSONAL non-owner denied`()
    {
        val comm = makeCommunication(CommunicationScope.PERSONAL, createdBy = otherUserId)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateTemplate(comm.id, UpdateCommunicationRequest(name = "New Name"))
        }
    }

    @Test
    fun `CommunicationService updateTemplate - ORG COMMUNICATION_EDIT denied`()
    {
        val comm = makeCommunication(CommunicationScope.ORG)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateTemplate(comm.id, UpdateCommunicationRequest(name = "New Name"))
        }
    }

    @Test
    fun `CommunicationService updateTemplate - PLATFORM forbidden clone instead`()
    {
        val comm = makeCommunication(CommunicationScope.PLATFORM)
        val repo = mock<CommunicationRepository>()
        whenever(repo.findById(comm.id)).thenReturn(comm)

        val svc = makeCommunicationService(repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateTemplate(comm.id, UpdateCommunicationRequest(name = "New Name"))
        }
    }

    // =========================================================================
    // SequenceDefinitionService — ORG-only scope with no-context guard
    // =========================================================================

    private fun makeSequence(): SequenceDefinition = SequenceDefinition().apply {
        this.organizationId = UUID.randomUUID()
        this.name = "Test Seq"
        this.key = "TEST_KEY"
    }

    private fun makeSequenceService(
        authSvc: AuthorizationService = makeAuthService(),
        factory: AuthorizationContextFactory = makeFactory(),
        roleService: UserRoleService = makeRoleService(),
        repo: SequenceDefinitionRepository = mock(),
    ): SequenceDefinitionService = SequenceDefinitionService(
        repository = repo,
        authorizationService = authSvc,
        authorizationContextFactory = factory,
        userRoleService = roleService,
    )

    @Test
    fun `SequenceDefinitionService listSequences - no active org returns empty list`()
    {
        // ANONYMOUS context has no activeOrgId
        val svc = makeSequenceService(factory = makeFactory(AuthorizationContext.ANONYMOUS))
        assertEquals(emptyList<Any>(), svc.listSequences())
    }

    @Test
    fun `SequenceDefinitionService getSequence - SEQUENCE_VIEW denied`()
    {
        val seq = makeSequence()
        val repo = mock<SequenceDefinitionRepository>()
        whenever(repo.findById(seq.id)).thenReturn(seq)

        val svc = makeSequenceService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> { svc.getSequence(seq.id) }
    }

    @Test
    fun `SequenceDefinitionService getSequence - SEQUENCE_VIEW allowed`()
    {
        val seq = makeSequence()
        val repo = mock<SequenceDefinitionRepository>()
        whenever(repo.findById(seq.id)).thenReturn(seq)

        val svc = makeSequenceService(authSvc = makeAuthService(Action.SEQUENCE_VIEW), repo = repo)
        val dto = svc.getSequence(seq.id)
        assertEquals("Test Seq", dto.name)
    }

    @Test
    fun `SequenceDefinitionService getSequence - APP_ADMIN bypasses access check`()
    {
        val seq = makeSequence()
        val repo = mock<SequenceDefinitionRepository>()
        whenever(repo.findById(seq.id)).thenReturn(seq)

        val svc = makeSequenceService(
            authSvc = makeAuthService(),
            roleService = makeRoleService(appAdmin = true),
            repo = repo,
        )
        val dto = svc.getSequence(seq.id)
        assertEquals("Test Seq", dto.name)
    }

    @Test
    fun `SequenceDefinitionService updateSequence - SEQUENCE_EDIT denied`()
    {
        val seq = makeSequence()
        val repo = mock<SequenceDefinitionRepository>()
        whenever(repo.findById(seq.id)).thenReturn(seq)

        val svc = makeSequenceService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateSequence(seq.id, UpdateSequenceRequest(name = "Updated"), AdminApprovalContext())
        }
    }

    // =========================================================================
    // VariableDefinitionService — PERSONAL ownership + ORG auth-service gate
    // =========================================================================

    private fun makeVariable(
        scope: VariableScope,
        createdBy: UUID = userId,
    ): VariableDefinition = VariableDefinition().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.key = "TEST_VAR"
    }

    private fun makeVariableService(
        authSvc: AuthorizationService = makeAuthService(),
        factory: AuthorizationContextFactory = makeFactory(),
        roleService: UserRoleService = makeRoleService(),
        repo: VariableDefinitionRepository = mock(),
    ): VariableDefinitionService = VariableDefinitionService(
        repository = repo,
        adminActionGuardService = mock(),
        authorizationService = authSvc,
        authorizationContextFactory = factory,
        userRoleService = roleService,
    )

    @Test
    fun `VariableDefinitionService updateVariable - PERSONAL non-owner denied`()
    {
        val variable = makeVariable(VariableScope.PERSONAL, createdBy = otherUserId)
        val repo = mock<VariableDefinitionRepository>()
        whenever(repo.findById(variable.id)).thenReturn(variable)

        val svc = makeVariableService(repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateVariable(variable.id, UpdateVariableRequest(), AdminApprovalContext())
        }
    }

    @Test
    fun `VariableDefinitionService updateVariable - PERSONAL owner passes auth gate`()
    {
        val variable = makeVariable(VariableScope.PERSONAL, createdBy = userId)
        val repo = mock<VariableDefinitionRepository>()
        whenever(repo.findById(variable.id)).thenReturn(variable)
        whenever(repo.update(any())).thenReturn(variable)

        val svc = makeVariableService(repo = repo)
        // Auth gate passes; any exception from deeper logic is not a 403.
        try
        {
            svc.updateVariable(variable.id, UpdateVariableRequest(), AdminApprovalContext())
        }
        catch (e: Exception)
        {
            assert(e !is ForbiddenException) { "Should not throw ForbiddenException; got $e" }
        }
    }

    @Test
    fun `VariableDefinitionService updateVariable - ORG VARIABLE_EDIT denied`()
    {
        val variable = makeVariable(VariableScope.ORG)
        val repo = mock<VariableDefinitionRepository>()
        whenever(repo.findById(variable.id)).thenReturn(variable)

        val svc = makeVariableService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateVariable(variable.id, UpdateVariableRequest(), AdminApprovalContext())
        }
    }

    @Test
    fun `VariableDefinitionService updateVariable - ORG APP_ADMIN bypasses check`()
    {
        val variable = makeVariable(VariableScope.ORG)
        val repo = mock<VariableDefinitionRepository>()
        whenever(repo.findById(variable.id)).thenReturn(variable)
        whenever(repo.update(any())).thenReturn(variable)

        val svc = makeVariableService(roleService = makeRoleService(appAdmin = true), repo = repo)
        try
        {
            svc.updateVariable(variable.id, UpdateVariableRequest(), AdminApprovalContext())
        }
        catch (e: Exception)
        {
            assert(e !is ForbiddenException) { "Should not throw ForbiddenException; got $e" }
        }
    }

    // =========================================================================
    // BlueprintDefinitionService — PERSONAL / ORG / APP gates
    // =========================================================================

    private fun makeBlueprint(
        scope: BlueprintScope,
        createdBy: UUID? = userId,
    ): BlueprintDefinition = BlueprintDefinition().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.name = "Test Blueprint"
        this.configJson = "{}"
    }

    private fun makeBlueprintService(
        authSvc: AuthorizationService = makeAuthService(),
        factory: AuthorizationContextFactory = makeFactory(),
        roleService: UserRoleService = makeRoleService(),
        repo: BlueprintDefinitionRepository = mock(),
        docDefaultRepo: BlueprintDocumentDefaultRepository = mock(),
        participantDefaultRepo: BlueprintParticipantDefaultRepository = mock(),
    ): BlueprintDefinitionService = BlueprintDefinitionService(
        repository = repo,
        documentDefaultRepository = docDefaultRepo,
        participantDefaultRepository = participantDefaultRepo,
        fieldDefaultRepository = mock(),
        documentLibraryRepository = mock<DocumentLibraryRepository>(),
        schemaDefinitionService = mock(),
        adminActionGuardService = mock(),
        authorizationService = authSvc,
        authorizationContextFactory = factory,
        userRoleService = roleService,
    )

    @Test
    fun `BlueprintDefinitionService getBlueprint - PERSONAL non-owner denied`()
    {
        val bp = makeBlueprint(BlueprintScope.PERSONAL, createdBy = otherUserId)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)

        val svc = makeBlueprintService(repo = repo)
        assertThrows<ForbiddenException> { svc.getBlueprint(bp.id) }
    }

    @Test
    fun `BlueprintDefinitionService getBlueprint - PERSONAL owner can access`()
    {
        val bp = makeBlueprint(BlueprintScope.PERSONAL, createdBy = userId)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)
        val docRepo = mock<BlueprintDocumentDefaultRepository>()
        whenever(docRepo.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        val partRepo = mock<BlueprintParticipantDefaultRepository>()
        whenever(partRepo.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())

        val svc = makeBlueprintService(repo = repo, docDefaultRepo = docRepo, participantDefaultRepo = partRepo)
        val dto = svc.getBlueprint(bp.id)
        assertEquals("Test Blueprint", dto.name)
    }

    @Test
    fun `BlueprintDefinitionService getBlueprint - ORG BLUEPRINT_VIEW denied`()
    {
        val bp = makeBlueprint(BlueprintScope.ORG)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)

        val svc = makeBlueprintService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> { svc.getBlueprint(bp.id) }
    }

    @Test
    fun `BlueprintDefinitionService getBlueprint - APP scope open to any authenticated user`()
    {
        val bp = makeBlueprint(BlueprintScope.APP)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)
        val docRepo = mock<BlueprintDocumentDefaultRepository>()
        whenever(docRepo.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())
        val partRepo = mock<BlueprintParticipantDefaultRepository>()
        whenever(partRepo.findAllByBlueprintDefinitionId(any())).thenReturn(emptyList())

        // No capabilities granted — APP reads are open to all authenticated callers
        val svc = makeBlueprintService(
            authSvc = makeAuthService(),
            repo = repo,
            docDefaultRepo = docRepo,
            participantDefaultRepo = partRepo,
        )
        val dto = svc.getBlueprint(bp.id)
        assertEquals("Test Blueprint", dto.name)
    }

    @Test
    fun `BlueprintDefinitionService updateBlueprint - APP-scoped forbidden clone instead`()
    {
        val bp = makeBlueprint(BlueprintScope.APP)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)

        val svc = makeBlueprintService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateBlueprint(bp.id, UpdateBlueprintRequest(), AdminApprovalContext())
        }
    }

    @Test
    fun `BlueprintDefinitionService updateBlueprint - ORG BLUEPRINT_EDIT denied`()
    {
        val bp = makeBlueprint(BlueprintScope.ORG)
        val repo = mock<BlueprintDefinitionRepository>()
        whenever(repo.findById(bp.id)).thenReturn(bp)

        val svc = makeBlueprintService(authSvc = makeAuthService(), repo = repo)
        assertThrows<ForbiddenException> {
            svc.updateBlueprint(bp.id, UpdateBlueprintRequest(), AdminApprovalContext())
        }
    }
}
