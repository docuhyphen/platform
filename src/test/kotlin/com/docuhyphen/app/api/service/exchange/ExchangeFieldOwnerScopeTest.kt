package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ExchangeFieldOwnerScopeTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val holdingOrganizationId: UUID = UUID.randomUUID()
    private val holderUserId: UUID = UUID.randomUUID()

    @Test
    fun `an Exchange held by an organization is governed by that organization`()
    {
        val adapter = adapterFor(exchange(ownerUserId = holderUserId, ownerOrganizationId = holdingOrganizationId))

        assertEquals(ScopeReference.Organization(holdingOrganizationId), adapter.ownerScope(exchangeId))
    }

    @Test
    fun `an Exchange one person holds alone is governed by that person`()
    {
        val adapter = adapterFor(exchange(ownerUserId = holderUserId, ownerOrganizationId = null))

        assertEquals(ScopeReference.Personal(holderUserId), adapter.ownerScope(exchangeId))
    }

    @Test
    fun `an Exchange recording no holder is governed by nobody`()
    {
        val adapter = adapterFor(exchange(ownerUserId = null, ownerOrganizationId = null))

        assertNull(adapter.ownerScope(exchangeId))
    }

    @Test
    fun `an Exchange that is not there is governed by nobody`()
    {
        val adapter = adapterFor(null)

        assertNull(adapter.ownerScope(exchangeId))
    }

    private fun exchange(ownerUserId: UUID?, ownerOrganizationId: UUID?) = Exchange().apply {
        id = exchangeId
        this.ownerUserId = ownerUserId
        this.ownerOrganizationId = ownerOrganizationId
        status = ExchangeStatus.INITIATED
        isDeleted = false
    }

    private fun adapterFor(exchange: Exchange?): ExchangeFieldResourceAdapter
    {
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        return ExchangeFieldResourceAdapter(
            exchangeRepository = exchangeRepository,
            authorizationService = mock<AuthorizationService>(),
            bindingPolicy = ExchangeFieldBindingPolicy(exchangeRepository, mock()),
        )
    }
}
