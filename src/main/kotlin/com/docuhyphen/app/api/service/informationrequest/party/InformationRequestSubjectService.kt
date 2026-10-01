package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.InformationRequestSubjectDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestSubjectDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestSubjectReference
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityExternalIdentifierRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.util.*

@ApplicationScoped
class InformationRequestSubjectService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val subjectRepository: SubjectIdentityRefRepository,
    private val identifierRepository: SubjectIdentityExternalIdentifierRepository,
    private val partyService: InformationRequestPartyService,
    private val authorizationService: AuthorizationService,
    private val ownerScopeAccess: InformationRequestOwnerScopeAccess,
)
{
    @Transactional
    fun assignSubject(command: AssignInformationRequestSubjectCommand): InformationRequestPartyAssignmentResult
    {
        val request = requestRepository.findById(command.requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        val decision = authorizationService.authorize(
            command.access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(request.id),
            command.access.authorization,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage Information Request parties")

        val subjectId = command.reference?.let { reference -> knownSubject(request, reference) }
            ?: existingOrNewSubject(request, command)
        return partyService.assign(
            AssignInformationRequestPartyCommand(
                requestId = request.id,
                roleKey = InformationRequestShareRoleKey.SUBJECT,
                subjectIdentityRefId = subjectId,
                access = command.access,
                precondition = command.precondition,
                idempotencyKey = command.idempotencyKey,
            ),
        )
    }

    fun listForOwner(): List<InformationRequestSubjectDto>
    {
        val owner = ownerScopeAccess.currentOwner()
        ownerScopeAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val subjects = subjectRepository.findForOwner(subjectOwnerTypeOf(owner.ownerType), owner.ownerId)
        val identifiers =
            identifierRepository.findForSubjects(subjects.map { it.id }).groupBy { it.subjectIdentityRefId }
        return subjects.map { subject ->
            InformationRequestSubjectDtoMapper.toDto(
                subject,
                identifiers[subject.id].orEmpty()
            )
        }
    }

    private fun knownSubject(request: InformationRequest, reference: InformationRequestSubjectReference): UUID? =
        identifierRepository.findByTenantValue(
            subjectOwnerTypeOf(request.ownerType),
            ownerIdOf(request),
            reference.authority.trim(),
            reference.identifierType.trim(),
            reference.identifierValue.trim(),
        )?.subjectIdentityRefId

    private fun existingOrNewSubject(request: InformationRequest, command: AssignInformationRequestSubjectCommand): UUID
    {
        val subjectId = UUID.nameUUIDFromBytes(
            "information_request.subject|${request.id}|${command.idempotencyKey}".toByteArray(StandardCharsets.UTF_8),
        )
        if (subjectRepository.findById(subjectId) != null) return subjectId
        val subject = subjectRepository.save(
            SubjectIdentityRef().apply {
                id = subjectId
                ownerType = subjectOwnerTypeOf(request.ownerType)
                ownerOrganizationId = request.ownerOrganizationId
                ownerUserId = request.ownerUserId.takeIf { request.ownerType == InformationRequestOwnerType.USER }
                subjectKind = command.subjectKind
            },
        )
        command.reference?.let { reference ->
            identifierRepository.save(
                SubjectIdentityExternalIdentifier().apply {
                    subjectIdentityRefId = subject.id
                    ownerType = subject.ownerType
                    ownerId = ownerIdOf(request)
                    authority = requireText(reference.authority, "authority")
                    identifierType = requireText(reference.identifierType, "identifier type")
                    identifierValue = requireText(reference.identifierValue, "identifier value")
                    authorizedByPrincipalKind = command.access.principal.kind
                    authorizedByPrincipalId = command.access.principal.id
                },
            )
        }
        return subject.id
    }

    private fun requireText(value: String, name: String): String =
        value.trim().ifBlank { throw IllegalArgumentException("A subject reference states its $name") }

    private fun subjectOwnerTypeOf(ownerType: InformationRequestOwnerType): SubjectIdentityOwnerType =
        when (ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> SubjectIdentityOwnerType.ORGANIZATION
            InformationRequestOwnerType.USER -> SubjectIdentityOwnerType.USER
        }

    private fun ownerIdOf(request: InformationRequest): UUID =
        requireNotNull(request.ownerOrganizationId ?: request.ownerUserId) { "Information Request has no owner" }
}
