package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExchangeRecipientRolePermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    @Test
    fun `EX-ROL-01 Auto role without write flags behaves as Viewer`()
    {
        assertEquals(
            RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER),
            ShareConstraints().adjustCapabilities(
                RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER),
            ),
        )
    }

    @Test
    fun `EX-ROL-02 Auto role with write flag behaves as Editor`()
    {
        val capabilities = ShareConstraints(allowDocumentUpload = true).adjustCapabilities(
            RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.VIEWER),
        )
        assertTrue(Capability.DOCUMENT_WRITE in capabilities)
        assertFalse(Capability.EXCHANGE_SHARE in capabilities)
    }

    @Test
    fun `EX-ROL-03 Editor receives only Editor capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.EDITOR,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_UPDATE, Action.DOCUMENT_COMMENT),
            denied = setOf(Action.DOCUMENT_DELETE, Action.DOCUMENT_SIGN, Action.EXCHANGE_MANAGE_ACCESS),
        )
    }

    @Test
    fun `EX-ROL-04 Reviewer receives only Reviewer capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.REVIEWER,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_COMMENT),
            denied = setOf(Action.DOCUMENT_UPDATE, Action.DOCUMENT_DELETE, Action.DOCUMENT_SIGN, Action.EXCHANGE_MANAGE_ACCESS),
        )
    }

    @Test
    fun `EX-ROL-05 Signer receives only Signer capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.SIGNER,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_SIGN),
            denied = setOf(Action.DOCUMENT_UPDATE, Action.DOCUMENT_DELETE, Action.DOCUMENT_COMMENT, Action.EXCHANGE_MANAGE_ACCESS),
        )
    }

    @Test
    fun `EX-ROL-06 Viewer receives only Viewer capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.VIEWER,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_VIEW),
            denied = setOf(Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_UPDATE, Action.DOCUMENT_COMMENT, Action.DOCUMENT_SIGN),
        )
    }

    @Test
    fun `EX-ROL-07 Commenter receives only Commenter capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.COMMENTER,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_VIEW, Action.DOCUMENT_COMMENT),
            denied = setOf(Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_UPDATE, Action.DOCUMENT_SIGN, Action.EXCHANGE_MANAGE_ACCESS),
        )
    }

    @Test
    fun `EX-ROL-08 Participant receives only Participant capabilities`()
    {
        assertRole(
            ExchangeShareRoleName.PARTICIPANT,
            allowed = setOf(Action.EXCHANGE_VIEW, Action.DOCUMENT_VIEW),
            denied = setOf(Action.DOCUMENT_DOWNLOAD, Action.DOCUMENT_UPDATE, Action.DOCUMENT_COMMENT, Action.DOCUMENT_SIGN),
        )
    }

    @Test
    fun `EX-ROL-09 Owner role cannot be assigned`()
    {
        val assignable = ExchangeShareRoleName.entries.filterNot { it == ExchangeShareRoleName.OWNER }
        assertFalse(ExchangeShareRoleName.OWNER in assignable)
        assertThrows(IllegalArgumentException::class.java) {
            require(ExchangeShareRoleName.OWNER in assignable)
        }
    }

    private fun assertRole(
        role: ExchangeShareRoleName,
        allowed: Set<Action>,
        denied: Set<Action>,
    )
    {
        val share = probe.share(role)
        allowed.forEach { probe.assertAllowed(it, listOf(share)) }
        denied.forEach { probe.assertDenied(it, listOf(share)) }
    }
}
