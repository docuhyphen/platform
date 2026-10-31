package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.InformationRequestSubjectDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.InformationRequestSubjectDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestSubjectReference
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityExternalIdentifierRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
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
    private val partyAssignments: InformationRequestPartyAssignmentService,
    private val authorizationService: AuthorizationService,
    private val ownerScopeAccess: InformationRequestOwnerScopeAccess,
    private val gate: InformationRequestMutationGate,
)
{
    @Transactional
    fun assignSubject(command: AssignInformationRequestSubjectCommand): InformationRequestPartyAssignmentResult
    {
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            command.requestId,
            command.access
        )
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
        if (command.reference != null) requirePrivacyManagement(request, command.access)

        gate.lock(request.id)
        val subjectId = command.reference?.let { reference -> knownSubject(request, reference) }
            ?: existingOrNewSubject(request, command)
        return partyAssignments.assign(
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

    @Transactional
    fun assignKnownSubject(command: AssignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult {
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            command.requestId,
            command.access
        )
        val request = requestRepository.findById(command.requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        requirePrivacyManagement(request, command.access)
        return partyAssignments.assign(command)
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

    private fun requirePrivacyManagement(request: InformationRequest, access: RequestAccessContext) {
        val owner = InformationRequestOwnerRef(request.ownerType, ownerIdOf(request))
        if (!ownerScopeAccess.permits(
                access.principal,
                owner,
                Action.INFORMATION_REQUEST_MANAGE_PRIVACY,
                access.authorization
            )
        ) {
            throw ForbiddenException("Naming a known subject needs privacy management for the request's owner")
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
            CommandRequestFingerprint.canonical(
                listOf(
                    "information_request.subject",
                    request.id,
                    command.access.principal.kind,
                    command.access.principal.id,
                    command.idempotencyKey,
                    command.subjectKind,
                    command.reference?.let {
                        listOf(it.authority.trim(), it.identifierType.trim(), it.identifierValue.trim())
                    },
                ),
            ).toByteArray(StandardCharsets.UTF_8),
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
