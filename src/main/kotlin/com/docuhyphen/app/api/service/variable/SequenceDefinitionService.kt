package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.SequenceDefinitionDto
import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.model.dto.toDto
import com.docuhyphen.app.api.model.entity.SequenceDefinition
import com.docuhyphen.app.api.model.entity.SequenceResetPeriod
import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.interceptor.EnforceAdminAction
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SequenceDefinitionService @Inject constructor(
    private val repository: SequenceDefinitionRepository,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SequenceDefinitionService::class.java)
        private val KEY_REGEX = Regex("^[A-Z0-9_]{1,64}$")
    }

    fun listSequences(isActive: Boolean? = null): List<SequenceDefinitionDto>
    {
        val activeOrgId = currentContext().activeOrgId ?: return emptyList()
        return repository.findAllByOrganizationIdAndIsDeletedFalse(activeOrgId)
            .filter { seq -> isActive == null || seq.isActive == isActive }
            .map { it.toDto() }
    }

    fun getSequence(id: UUID): SequenceDefinitionDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkReadAccess(seq, principal, context)
        return seq.toDto()
    }

    @EnforceAdminAction("ORG_SEQUENCE_CREATE")
    @Transactional
    fun createSequence(request: CreateSequenceRequest, context: AdminApprovalContext): SequenceDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val activeOrgId = authContext.activeOrgId
            ?: throw ForbiddenException("Organization context required to manage sequences")
        val isOrgAdmin = userRoleService.isOrgAdminIn(principal.id, activeOrgId)

        if (!isOrgAdmin)
            throw ForbiddenException("Org admin role required to manage sequences")

        val normalizedKey = request.key.trim().uppercase()
        if (!KEY_REGEX.matches(normalizedKey))
            throw IllegalArgumentException("Sequence key must be 1–64 uppercase alphanumeric characters or underscores")

        if (repository.findByOrganizationIdAndKeyAndIsDeletedFalse(activeOrgId, normalizedKey) != null)
            throw IllegalArgumentException("A sequence with key '$normalizedKey' already exists in this organization")

        val resetPeriod = runCatching { SequenceResetPeriod.valueOf(request.resetPeriod.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid resetPeriod '${request.resetPeriod}'") }

        val seq = SequenceDefinition().apply {
            this.organizationId = activeOrgId
            this.name = request.name.trim()
            this.key = normalizedKey
            this.padWidth = request.padWidth.coerceAtLeast(0)
            this.prefix = request.prefix?.takeIf { it.isNotBlank() }
            this.suffix = request.suffix?.takeIf { it.isNotBlank() }
            this.resetPeriod = resetPeriod
            this.createdByAppUserId = principal.id
        }
        return repository.save(seq).toDto()
    }

    @EnforceAdminAction("ORG_SEQUENCE_UPDATE")
    @Transactional
    fun updateSequence(id: UUID, request: UpdateSequenceRequest, context: AdminApprovalContext): SequenceDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, principal, authContext)

        request.name?.trim()?.let { if (it.isNotBlank()) seq.name = it }
        request.padWidth?.let { seq.padWidth = it.coerceAtLeast(0) }
        request.prefix?.let { seq.prefix = it.takeIf { v -> v.isNotBlank() } }
        request.suffix?.let { seq.suffix = it.takeIf { v -> v.isNotBlank() } }
        request.resetPeriod?.let {
            seq.resetPeriod = runCatching { SequenceResetPeriod.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid resetPeriod '$it'") }
        }
        request.isActive?.let { seq.isActive = it }

        return repository.update(seq).toDto()
    }

    @EnforceAdminAction("ORG_SEQUENCE_DELETE")
    @Transactional
    fun deleteSequence(id: UUID, context: AdminApprovalContext)
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, principal, authContext)
        seq.isDeleted = true
        seq.isActive = false
        repository.update(seq)
        logger.info("Sequence {} soft-deleted", id)
    }

    @EnforceAdminAction("ORG_SEQUENCE_RESET")
    @Transactional
    fun resetCounter(id: UUID, context: AdminApprovalContext): SequenceDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, principal, authContext)
        seq.currentValue = 0L
        seq.lastResetAt = Timestamp.from(Instant.now())
        return repository.update(seq).toDto()
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkReadAccess(seq: SequenceDefinition, principal: PrincipalRef, context: AuthorizationContext)
    {
        val decision = authorizationService.authorize(
            principal, Action.SEQUENCE_VIEW, ResourceRef.sequence(seq.id), context,
        )
        if (decision is Decision.Deny)
            throw ForbiddenException("Access denied to sequence ${seq.id}")
    }

    private fun checkWriteAccess(seq: SequenceDefinition, principal: PrincipalRef, context: AuthorizationContext)
    {
        val decision = authorizationService.authorize(
            principal, Action.SEQUENCE_EDIT, ResourceRef.sequence(seq.id), context,
        )
        if (decision is Decision.Deny)
            throw ForbiddenException("Org admin role required to modify sequence ${seq.id}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun currentPrincipal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")

    private fun currentContext(): AuthorizationContext =
        authorizationContextFactory.currentContext()
}
