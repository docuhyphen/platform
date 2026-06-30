package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.SequenceDefinitionDto
import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.model.dto.toDto
import com.docuhyphen.app.api.model.entity.SequenceDefinition
import com.docuhyphen.app.api.model.entity.SequenceResetPeriod
import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
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
    private val adminActionGuardService: AdminActionGuardService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SequenceDefinitionService::class.java)
        private val KEY_REGEX = Regex("^[A-Z0-9_]{1,64}$")
    }

    fun listSequences(organizationId: UUID, isActive: Boolean? = null): List<SequenceDefinitionDto> =
        repository.findAllByOrganizationIdAndIsDeletedFalse(organizationId)
            .filter { seq -> isActive == null || seq.isActive == isActive }
            .map { it.toDto() }

    fun getSequence(id: UUID, callerOrgId: UUID?, isOrgAdmin: Boolean, isAppAdmin: Boolean): SequenceDefinitionDto
    {
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkReadAccess(seq, callerOrgId, isAppAdmin)
        return seq.toDto()
    }

    @Transactional
    fun createSequence(
        organizationId: UUID,
        request: CreateSequenceRequest,
        callerUserId: UUID,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        context: AdminApprovalContext,
    ): SequenceDefinitionDto
    {
        if (!isOrgAdmin && !isAppAdmin)
            throw ForbiddenException("Org admin role required to manage sequences")

        val normalizedKey = request.key.trim().uppercase()
        if (!KEY_REGEX.matches(normalizedKey))
            throw IllegalArgumentException("Sequence key must be 1–64 uppercase alphanumeric characters or underscores")

        if (repository.findByOrganizationIdAndKeyAndIsDeletedFalse(organizationId, normalizedKey) != null)
            throw IllegalArgumentException("A sequence with key '$normalizedKey' already exists in this organization")

        val resetPeriod = runCatching { SequenceResetPeriod.valueOf(request.resetPeriod.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid resetPeriod '${request.resetPeriod}'") }

        adminActionGuardService.enforce(
            action = "ORG_SEQUENCE_CREATE",
            actorId = callerUserId,
            context = context,
        )

        val seq = SequenceDefinition().apply {
            this.organizationId = organizationId
            this.name = request.name.trim()
            this.key = normalizedKey
            this.padWidth = request.padWidth.coerceAtLeast(0)
            this.prefix = request.prefix?.takeIf { it.isNotBlank() }
            this.suffix = request.suffix?.takeIf { it.isNotBlank() }
            this.resetPeriod = resetPeriod
            this.createdByAppUserId = callerUserId
        }
        return repository.save(seq).toDto()
    }

    @Transactional
    fun updateSequence(
        id: UUID,
        request: UpdateSequenceRequest,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        callerUserId: UUID,
        context: AdminApprovalContext,
    ): SequenceDefinitionDto
    {
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, callerOrgId, isOrgAdmin, isAppAdmin)
        adminActionGuardService.enforce(
            action = "ORG_SEQUENCE_UPDATE",
            actorId = callerUserId,
            context = context,
        )

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

    @Transactional
    fun deleteSequence(
        id: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        callerUserId: UUID,
        context: AdminApprovalContext,
    )
    {
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, callerOrgId, isOrgAdmin, isAppAdmin)
        adminActionGuardService.enforce(
            action = "ORG_SEQUENCE_DELETE",
            actorId = callerUserId,
            context = context,
        )
        seq.isDeleted = true
        seq.isActive = false
        repository.update(seq)
        logger.info("Sequence {} soft-deleted", id)
    }

    @Transactional
    fun resetCounter(
        id: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        callerUserId: UUID,
        context: AdminApprovalContext,
    ): SequenceDefinitionDto
    {
        val seq = repository.findById(id) ?: throw IllegalArgumentException("Sequence not found")
        checkWriteAccess(seq, callerOrgId, isOrgAdmin, isAppAdmin)
        adminActionGuardService.enforce(
            action = "ORG_SEQUENCE_RESET",
            actorId = callerUserId,
            context = context,
        )
        seq.currentValue = 0L
        seq.lastResetAt = Timestamp.from(Instant.now())
        return repository.update(seq).toDto()
    }

    private fun checkReadAccess(seq: SequenceDefinition, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        if (callerOrgId == null || seq.organizationId != callerOrgId)
            throw ForbiddenException("Access denied to sequence ${seq.id}")
    }

    private fun checkWriteAccess(
        seq: SequenceDefinition,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    )
    {
        if (isAppAdmin) return
        if (!isOrgAdmin || callerOrgId == null || seq.organizationId != callerOrgId)
            throw ForbiddenException("Org admin role required to modify sequence ${seq.id}")
    }
}
