package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.service.auth.authz.Action
import org.junit.jupiter.api.Test

class ExchangeUiApiConsistencyPermutationTest
{
    private val probe = ExchangeAuthorizationProbe()

    @Test
    fun `view denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_VIEW, emptyList())
    }

    @Test
    fun `acceptance denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_ACCEPT, emptyList())
    }

    @Test
    fun `document mutation denial is enforced by the API`()
    {
        probe.assertDenied(Action.DOCUMENT_UPDATE, listOf(probe.share(ExchangeShareRoleName.VIEWER)))
    }

    @Test
    fun `document download denial is enforced by the API`()
    {
        probe.assertDenied(Action.DOCUMENT_DOWNLOAD, listOf(probe.share(ExchangeShareRoleName.VIEWER)))
    }

    @Test
    fun `access list denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_MANAGE_ACCESS, listOf(probe.share(ExchangeShareRoleName.EDITOR)))
    }

    @Test
    fun `access grant denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_MANAGE_ACCESS, listOf(probe.share(ExchangeShareRoleName.REVIEWER)))
    }

    @Test
    fun `access role change denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_MANAGE_ACCESS, listOf(probe.share(ExchangeShareRoleName.SIGNER)))
    }

    @Test
    fun `access revocation denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_MANAGE_ACCESS, listOf(probe.share(ExchangeShareRoleName.COMMENTER)))
    }

    @Test
    fun `lifecycle transition denial is enforced by the API`()
    {
        probe.assertDenied(Action.EXCHANGE_END, listOf(probe.share(ExchangeShareRoleName.EDITOR)))
    }
}
