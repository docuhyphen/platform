package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry.Companion.toResourceKind
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The runtime Information Request vocabulary in the central authorization stack.
 *
 * Rules verified:
 *  - The request aggregate and its Requirement occurrences are named resource types with
 *    matching resource kinds, so authorization can address them without a second model.
 *  - Every runtime request Action maps to a runtime request Capability.
 *  - None of those capabilities is granted by any existing role, so the vocabulary is
 *    default-deny until a resource-scoped role grant is added.
 *  - Existing Exchange and Document capability grants are untouched.
 *  - Every stable refusal code is uniquely named within one prefix.
 */
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

    // --- resource vocabulary ---

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

    // --- action to capability ---

    @Test
    fun `every runtime request action requires a runtime request capability`()
    {
        val requestActions = Action.entries.filter { it.name.startsWith("INFORMATION_REQUEST_") }
            .filterNot { it.name.startsWith("INFORMATION_REQUEST_TEMPLATE_") }

        assertTrue(requestActions.isNotEmpty()) { "the runtime request action vocabulary must exist" }
        requestActions.forEach { action ->
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
            Action.INFORMATION_REQUEST_REASSIGN_PARTY,
            Action.INFORMATION_REQUEST_EXPORT,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
            Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
            Action.INFORMATION_REQUEST_SUBMIT,
            Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
            Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
            Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
            Action.INFORMATION_REQUEST_EVIDENCE_MANAGE,
        )

        val declared = Action.entries.filter { it.name.startsWith("INFORMATION_REQUEST_") }
            .filterNot { it.name.startsWith("INFORMATION_REQUEST_TEMPLATE_") }
            .toSet()

        assertEquals(expected, declared)
    }

    @Test
    fun `responding attesting reviewing and evidence handling are separate capabilities`()
    {
        assertEquals(Capability.INFORMATION_REQUEST_RESPOND, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND.required)
        assertEquals(Capability.INFORMATION_REQUEST_ATTEST, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST.required)
        assertEquals(Capability.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW.required)
        assertEquals(Capability.INFORMATION_REQUEST_EVIDENCE_WRITE, Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD.required)
        assertEquals(Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE.required)
    }

    // --- default deny, narrowed to the Exchange owner's authoring grant ---

    /**
     * The Exchange OWNER Share role is the one deliberate, resource-scoped exception to default
     * deny: it is the only grant path that can authorize creating the very first runtime request
     * against an Exchange, since no Share can exist yet on a request that does not exist yet.
     * Every other runtime request capability, including issuance, editing, export, and every
     * respondent, attestor, or reviewer capability, is still granted by a request-scoped Share
     * only.
     */
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

    // --- stable refusal codes ---

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

