package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry.Companion.toResourceKind
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestAuthorizationVocabularyTest
{
    private val runtimeCapabilities = setOf(
        Capability.INFORMATION_REQUEST_CREATE,
        Capability.INFORMATION_REQUEST_READ,
        Capability.INFORMATION_REQUEST_WRITE,
        Capability.INFORMATION_REQUEST_ISSUE,
        Capability.INFORMATION_REQUEST_CANCEL,
        Capability.INFORMATION_REQUEST_ADMIN,
        Capability.INFORMATION_REQUEST_RESPOND,
        Capability.INFORMATION_REQUEST_ATTEST,
        Capability.INFORMATION_REQUEST_SUBMIT,
        Capability.INFORMATION_REQUEST_REVIEW,
        Capability.INFORMATION_REQUEST_EVIDENCE_READ,
        Capability.INFORMATION_REQUEST_EVIDENCE_WRITE,
        Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN,
        Capability.INFORMATION_REQUEST_EXPORT,
    )

    private val ownerScopeActions = setOf(
        Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE,
        Action.INFORMATION_REQUEST_MANAGE_PRIVACY,
        Action.INFORMATION_REQUEST_SEND_REMINDERS,
    )

    private val requestScopedActions = Action.entries
        .filter { it.name.startsWith("INFORMATION_REQUEST_") }
        .filterNot { it.name.startsWith("INFORMATION_REQUEST_TEMPLATE_") }
        .filterNot { it in ownerScopeActions }

    @Test
    fun `request aggregate and requirement occurrence are addressable resource types`()
    {
        val requestId = UUID.randomUUID()
        val requirementId = UUID.randomUUID()

        assertEquals(
            ResourceRef(ResourceType.INFORMATION_REQUEST, requestId),
            ResourceRef.informationRequest(requestId),
        )
        assertEquals(
            ResourceRef(ResourceType.INFORMATION_REQUEST_REQUIREMENT, requirementId),
            ResourceRef.informationRequestRequirement(requirementId),
        )
    }

    @Test
    fun `request resource types map to their own resource kinds`()
    {
        assertEquals(ResourceKind.INFORMATION_REQUEST, ResourceType.INFORMATION_REQUEST.toResourceKind())
        assertEquals(
            ResourceKind.INFORMATION_REQUEST_REQUIREMENT,
            ResourceType.INFORMATION_REQUEST_REQUIREMENT.toResourceKind(),
        )
    }

    @Test
    fun `every runtime request action requires a runtime request capability`()
    {
        assertTrue(requestScopedActions.isNotEmpty()) { "the runtime request action vocabulary must exist" }
        requestScopedActions.forEach { action ->
            assertTrue(action.required in runtimeCapabilities) {
                "$action must require a runtime Information Request capability, not ${action.required}"
            }
        }
    }

    @Test
    fun `the runtime request action vocabulary covers the whole request lifecycle`()
    {
        val expected = setOf(
            Action.INFORMATION_REQUEST_CREATE,
            Action.INFORMATION_REQUEST_VIEW,
            Action.INFORMATION_REQUEST_EDIT,
            Action.INFORMATION_REQUEST_ISSUE,
            Action.INFORMATION_REQUEST_CANCEL,
            Action.INFORMATION_REQUEST_SUPERSEDE,
            Action.INFORMATION_REQUEST_AMEND,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES,
            Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES,
            Action.INFORMATION_REQUEST_EXPORT,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
            Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
            Action.INFORMATION_REQUEST_SUBMIT,
            Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
            Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
            Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
            Action.INFORMATION_REQUEST_EVIDENCE_MANAGE,
            Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT,
            Action.INFORMATION_REQUEST_REVIEW,
            Action.INFORMATION_REQUEST_VIEW_REVIEWS,
            Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
            Action.INFORMATION_REQUEST_APPEAL_REVIEW,
            Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
            Action.INFORMATION_REQUEST_PROMOTE_FACT,
            Action.INFORMATION_REQUEST_REVOKE_FACT,
            Action.INFORMATION_REQUEST_RECORD_DECISION,
            Action.INFORMATION_REQUEST_CONFIGURE_COMPLETION,
            Action.INFORMATION_REQUEST_MANAGE_CLOCKS,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
        )

        assertEquals(expected, requestScopedActions.toSet())
    }

    @Test
    fun `responding attesting reviewing and evidence handling are separate capabilities`()
    {
        assertEquals(Capability.INFORMATION_REQUEST_RESPOND, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND.required)
        assertEquals(Capability.INFORMATION_REQUEST_ATTEST, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_EXPORT, Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT.required)
        assertEquals(Capability.INFORMATION_REQUEST_EVIDENCE_WRITE, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD.required)
        assertEquals(Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_VIEW_REVIEWS.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_MANAGE_REVIEWS.required)
        assertEquals(Capability.INFORMATION_REQUEST_SUBMIT, Action.INFORMATION_REQUEST_APPEAL_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_RESPOND, Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_PROMOTE_FACT.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_RECORD_DECISION.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_CONFIGURE_COMPLETION.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_MANAGE_CLOCKS.required)
        assertEquals(Capability.INFORMATION_REQUEST_ADMIN, Action.INFORMATION_REQUEST_VIEW_OPERATIONS.required)
    }

    @Test
    fun `owner scope request administration requires capabilities only organization owners and administrators hold`()
    {
        assertEquals(
            Capability.INFORMATION_REQUEST_OPERATIONS_READ,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE.required
        )
        assertEquals(Capability.INFORMATION_REQUEST_PRIVACY_MANAGE, Action.INFORMATION_REQUEST_MANAGE_PRIVACY.required)
        assertEquals(
            Capability.INFORMATION_REQUEST_OPERATIONS_MANAGE,
            Action.INFORMATION_REQUEST_SEND_REMINDERS.required
        )
        val ownerScope = ownerScopeActions.map { it.required }.toSet()

        OrganizationRoleName.entries.forEach { role ->
            val granted = RoleCapabilities.forOrganizationRole(role).intersect(ownerScope)
            if (role == OrganizationRoleName.ORG_OWNER || role == OrganizationRoleName.ORG_ADMIN) assertEquals(
                ownerScope,
                granted
            )
            else assertTrue(granted.isEmpty()) { "$role must not administer an owner's requests" }
        }
        val elsewhere = mutableSetOf<Capability>()
        AppRoleName.entries.forEach { elsewhere += RoleCapabilities.forAppRole(it) }
        ApplicationRoleName.entries.forEach { elsewhere += RoleCapabilities.forApplicationRole(it) }
        PrincipalGroupRoleName.entries.forEach { elsewhere += RoleCapabilities.forPrincipalGroupRole(it) }
        ExchangeShareRoleName.entries.forEach { elsewhere += RoleCapabilities.forExchangeShareRole(it) }
        assertTrue(elsewhere.intersect(ownerScope).isEmpty())
        requestScopedActions.forEach { action ->
            assertFalse(action.required in ownerScope) { "$action acts on one request and must not accept owner-scope administration" }
        }
    }

    private val ownerAuthoredCapabilities = setOf(
        Capability.INFORMATION_REQUEST_CREATE,
        Capability.INFORMATION_REQUEST_READ,
        Capability.INFORMATION_REQUEST_CANCEL,
        Capability.INFORMATION_REQUEST_ADMIN,
    )

    @Test
    fun `only the exchange owner role grants a runtime request capability, and only the authoring set`()
    {
        val assigned = mutableSetOf<Capability>()
        AppRoleName.entries.forEach { assigned += RoleCapabilities.forAppRole(it) }
        ApplicationRoleName.entries.forEach { assigned += RoleCapabilities.forApplicationRole(it) }
        OrganizationRoleName.entries.forEach { assigned += RoleCapabilities.forOrganizationRole(it) }
        PrincipalGroupRoleName.entries.forEach { assigned += RoleCapabilities.forPrincipalGroupRole(it) }
        ExchangeShareRoleName.entries
            .filterNot { it == ExchangeShareRoleName.OWNER }
            .forEach { assigned += RoleCapabilities.forExchangeShareRole(it) }

        val leaked = assigned.intersect(runtimeCapabilities)
        assertTrue(leaked.isEmpty()) {
            "runtime request capabilities must be granted by a request role or the Exchange owner only, " +
                    "but found $leaked"
        }

        val ownerGrant = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.OWNER)
            .intersect(runtimeCapabilities)
        assertEquals(ownerAuthoredCapabilities, ownerGrant)
    }

    @Test
    fun `existing Exchange share role capabilities are unchanged apart from the owner authoring grant`()
    {
        val owner = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.OWNER)
        assertTrue(owner.contains(Capability.EXCHANGE_ADMIN))
        assertTrue(owner.contains(Capability.DOCUMENT_WRITE))
        assertTrue(owner.containsAll(ownerAuthoredCapabilities))
        assertFalse(owner.contains(Capability.INFORMATION_REQUEST_ISSUE))
        assertFalse(owner.contains(Capability.INFORMATION_REQUEST_WRITE))
        assertFalse(owner.contains(Capability.INFORMATION_REQUEST_EXPORT))

        val viewer = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER)
        assertEquals(
            setOf(Capability.EXCHANGE_ACCEPT, Capability.EXCHANGE_READ, Capability.DOCUMENT_READ),
            viewer,
        )
    }

    @Test
    fun `the refusal catalog names the Template refusals routes emit and no code nothing emits`() {
        val codes = InformationRequestErrorCatalog.allCodes().toSet()

        listOf(
            InformationRequestErrorCatalog.TEMPLATE_INVALID,
            InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
            InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            InformationRequestTemplateVersionUnavailableException.PLATFORM_COPY_REQUIRED,
            InformationRequestTemplateVersionUnavailableException.OWNER_MISMATCH,
            InformationRequestTemplateVersionUnavailableException.SCHEMA_UNAVAILABLE,
            InformationRequestTemplateVersionUnavailableException.BLUEPRINT_UNAVAILABLE,
        ).forEach { assertTrue(it in codes) { "$it is emitted, so the catalog lists it" } }
        listOf(
            "INFORMATION_REQUEST_AUTHORIZATION_UNAVAILABLE",
            "INFORMATION_REQUEST_PRECONDITION_REQUIRED",
            "INFORMATION_REQUEST_PRECONDITION_FAILED",
            "INFORMATION_REQUEST_IDEMPOTENCY_CONFLICT",
            "INFORMATION_REQUEST_ENTITLEMENT_REQUIRED",
        ).forEach { assertFalse(it in codes) { "$it is never emitted" } }
    }

    @Test
    fun `every refusal code is unique and carries the request prefix`()
    {
        val codes = InformationRequestErrorCatalog.allCodes()

        assertTrue(codes.isNotEmpty())
        assertEquals(codes.size, codes.toSet().size) { "refusal codes must be unique" }
        codes.forEach { code ->
            assertTrue(code.startsWith("INFORMATION_REQUEST_")) { "$code must be namespaced" }
            assertEquals(code.uppercase(), code) { "$code must be a stable machine code" }
        }
    }
}

