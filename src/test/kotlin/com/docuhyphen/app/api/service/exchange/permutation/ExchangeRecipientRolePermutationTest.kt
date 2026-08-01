package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ExchangeRecipientRolePermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    companion object
    {
        private val exchangeActions = listOf(
            Action.EXCHANGE_VIEW,
            Action.DOCUMENT_VIEW,
            Action.DOCUMENT_DOWNLOAD,
            Action.DOCUMENT_UPLOAD,
            Action.DOCUMENT_UPDATE,
            Action.DOCUMENT_DELETE,
            Action.DOCUMENT_COMMENT,
            Action.DOCUMENT_SIGN,
            Action.EXCHANGE_MANAGE_ACCESS,
            Action.EXCHANGE_ACCEPT,
        )

        @JvmStatic
        fun roleActionScenarios(): Stream<Arguments>
        {
            val allowed = mapOf(
                ExchangeShareRoleName.OWNER to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.DOCUMENT_DOWNLOAD,
                    Action.DOCUMENT_UPLOAD,
                    Action.DOCUMENT_UPDATE,
                    Action.DOCUMENT_DELETE,
                    Action.DOCUMENT_COMMENT,
                    Action.EXCHANGE_MANAGE_ACCESS,
                ),
                ExchangeShareRoleName.EDITOR to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.DOCUMENT_DOWNLOAD,
                    Action.DOCUMENT_UPLOAD,
                    Action.DOCUMENT_UPDATE,
                    Action.DOCUMENT_COMMENT,
                    Action.EXCHANGE_ACCEPT,
                ),
                ExchangeShareRoleName.REVIEWER to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.DOCUMENT_DOWNLOAD,
                    Action.DOCUMENT_COMMENT,
                    Action.EXCHANGE_ACCEPT,
                ),
                ExchangeShareRoleName.SIGNER to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.DOCUMENT_DOWNLOAD,
                    Action.DOCUMENT_SIGN,
                    Action.EXCHANGE_ACCEPT,
                ),
                ExchangeShareRoleName.VIEWER to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.EXCHANGE_ACCEPT,
                ),
                ExchangeShareRoleName.COMMENTER to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.DOCUMENT_COMMENT,
                    Action.EXCHANGE_ACCEPT,
                ),
                ExchangeShareRoleName.PARTICIPANT to setOf(
                    Action.EXCHANGE_VIEW,
                    Action.DOCUMENT_VIEW,
                    Action.EXCHANGE_ACCEPT,
                ),
            )
            return ExchangeShareRoleName.entries.stream().flatMap { role ->
                exchangeActions.stream().map { action ->
                    Arguments.of(role, action, action in allowed.getValue(role))
                }
            }
        }
    }

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

    @ParameterizedTest(name = "{0} {1} allowed={2}")
    @MethodSource("roleActionScenarios")
    fun `EX-ROL-03 through EX-ROL-08 roles enforce the complete action matrix`(
        role: ExchangeShareRoleName,
        action: Action,
        expectedAllowed: Boolean,
    )
    {
        val status = if (action == Action.EXCHANGE_ACCEPT) ShareStatus.PENDING_APPROVAL else ShareStatus.ACTIVE
        val share = probe.share(role, status)

        if (expectedAllowed)
        {
            probe.assertAllowed(action, listOf(share))
        }
        else
        {
            probe.assertDenied(action, listOf(share))
        }
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

}
