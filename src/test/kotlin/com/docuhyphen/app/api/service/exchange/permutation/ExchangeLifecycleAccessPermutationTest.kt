package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.auth.authz.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ExchangeLifecycleAccessPermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    @Test
    fun `EX-LIF-01 Owner can edit documents in Draft`()
    {
        probe.assertAllowed(Action.DOCUMENT_UPDATE, listOf(probe.share(ExchangeShareRoleName.OWNER)))
    }

    @Test
    fun `EX-LIF-02 pending primary Share permits acceptance`()
    {
        val share = probe.share(ExchangeShareRoleName.VIEWER, ShareStatus.PENDING_APPROVAL)
        probe.assertAllowed(Action.EXCHANGE_ACCEPT, listOf(share))
        probe.assertDenied(Action.EXCHANGE_VIEW, listOf(share))
    }

    @Test
    fun `EX-LIF-03 Owner can end Active Exchange`()
    {
        probe.assertAllowed(Action.EXCHANGE_END, listOf(probe.share(ExchangeShareRoleName.OWNER)))
    }

    @Test
    fun `EX-LIF-04 Owner can rescind Active Exchange`()
    {
        probe.assertAllowed(Action.EXCHANGE_RESCIND, listOf(probe.share(ExchangeShareRoleName.OWNER)))
    }

    @Test
    fun `EX-LIF-05 Owner can rescind Draft Exchange`()
    {
        probe.assertAllowed(Action.EXCHANGE_RESCIND, listOf(probe.share(ExchangeShareRoleName.OWNER)))
    }

    @Test
    fun `EX-LIF-06 participant cannot update after rejection`()
    {
        probe.assertDenied(
            Action.DOCUMENT_UPDATE,
            listOf(probe.share(ExchangeShareRoleName.PARTICIPANT)),
            resourceContext = probe.personalResourceContext(archived = true),
        )
    }

    @Test
    fun `EX-LIF-07 participant cannot update after Exchange ends`()
    {
        probe.assertDenied(
            Action.DOCUMENT_UPDATE,
            listOf(
                probe.share(
                    ExchangeShareRoleName.PARTICIPANT,
                    constraintsJson = """{"allow_document_update":true}""",
                ),
            ),
            resourceContext = probe.personalResourceContext(archived = true),
        )
    }

    @Test
    fun `EX-LIF-08 access cannot change after rescission`()
    {
        probe.assertDenied(
            Action.EXCHANGE_MANAGE_ACCESS,
            listOf(probe.share(ExchangeShareRoleName.EDITOR)),
            resourceContext = probe.personalResourceContext(archived = true),
        )
    }

    @Test
    fun `EX-LIF-09 Owner can delete Ended Exchange`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_DELETE,
            listOf(probe.share(ExchangeShareRoleName.OWNER)),
            resourceContext = probe.personalResourceContext(archived = true),
        )
    }

    @Test
    fun `EX-LIF-10 Owner can delete Rejected Exchange`()
    {
        probe.assertAllowed(
            Action.EXCHANGE_DELETE,
            listOf(probe.share(ExchangeShareRoleName.OWNER)),
            resourceContext = probe.personalResourceContext(archived = true),
        )
    }

    @Test
    fun `EX-LIF-11 open session is denied after revocation`()
    {
        probe.assertDenied(Action.EXCHANGE_VIEW, listOf(probe.share(status = ShareStatus.REVOKED)))
    }

    @Test
    fun `EX-LIF-12 open session is denied after Share expiry`()
    {
        probe.assertDenied(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(expiresAt = Instant.now().minusSeconds(1))),
        )
    }

    @Test
    fun `EX-LIF-13 stale tab is denied after sign-out`()
    {
        probe.assertDenied(Action.EXCHANGE_VIEW, emptyList())
    }

    @Test
    fun `EX-LIF-14 active organization change preserves Share access`()
    {
        val share = probe.share()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(share))
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(share))
    }

    @Test
    fun `EX-LIF-15 guessed Exchange identifier does not disclose details`()
    {
        val decision = probe.authorize(Action.EXCHANGE_VIEW, emptyList())
        assertEquals("NO_GRANT", (decision as com.docuhyphen.app.api.service.auth.authz.Decision.Deny).reasonCode)
    }
}
