package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ExchangeFieldBindingPolicyTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val ownerOrganizationId: UUID = UUID.randomUUID()
    private val ownerUserId: UUID = UUID.randomUUID()

    @Test
    fun `a recipient reaching an organization's Exchange sees only what is classified for everyone`()
    {
        val policy = policy(organizationOwned = true, callerIsOrgMember = false)
        val participant = FieldsAccessContext(
            PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext(),
        )

        assertEquals(
            FieldBindingDecision.Allow,
            policy.decide(access(participant, binding(FieldDataClassification.PUBLIC))),
        )
        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE),
            policy.decide(access(participant, binding(FieldDataClassification.INTERNAL))),
        )
    }

    @Test
    fun `a member of the owning organization sees every question`()
    {
        val policy = policy(organizationOwned = true, callerIsOrgMember = true)
        val member = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

        assertEquals(
            FieldBindingDecision.Allow,
            policy.decide(access(member, binding(FieldDataClassification.INTERNAL))),
        )
    }

    @Test
    fun `a registered user outside the owning organization is treated as a recipient`()
    {
        val policy = policy(organizationOwned = true, callerIsOrgMember = false)
        val outsider = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE),
            policy.decide(access(outsider, binding(FieldDataClassification.INTERNAL))),
        )
    }

    @Test
    fun `the owner of a personally held Exchange is the only caller inside it`()
    {
        val policy = policy(organizationOwned = false, callerIsOrgMember = false)
        val owner = FieldsAccessContext(PrincipalRef.user(ownerUserId), AuthorizationContext())
        val someoneElse = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

        assertEquals(
            FieldBindingDecision.Allow,
            policy.decide(access(owner, binding(FieldDataClassification.INTERNAL))),
        )
        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE),
            policy.decide(access(someoneElse, binding(FieldDataClassification.INTERNAL))),
        )
    }

    @Test
    fun `a link-borne caller is always outside the Exchange`()
    {
        val policy = policy(organizationOwned = true, callerIsOrgMember = true)
        val link = FieldsAccessContext(PrincipalRef.publicLink(UUID.randomUUID()), AuthorizationContext())

        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE),
            policy.decide(access(link, binding(FieldDataClassification.INTERNAL))),
        )
    }

    @Test
    fun `an Exchange still accepts answers only while it is a draft`()
    {
        assertTrue(adapter(ExchangeStatus.INITIATED).valuesEditable(exchangeId))
        assertFalse(adapter(ExchangeStatus.ACCEPTED_STARTED).valuesEditable(exchangeId))
        assertFalse(adapter(ExchangeStatus.ENDED).valuesEditable(exchangeId))
    }

    private fun exchange(status: ExchangeStatus, organizationOwned: Boolean = true) = Exchange().apply {
        id = exchangeId
        ownerUserId = this@ExchangeFieldBindingPolicyTest.ownerUserId
        ownerOrganizationId = if (organizationOwned) this@ExchangeFieldBindingPolicyTest.ownerOrganizationId else null
        this.status = status
        isDeleted = false
    }

    private fun policy(organizationOwned: Boolean, callerIsOrgMember: Boolean): ExchangeFieldBindingPolicy
    {
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId))
            .thenReturn(exchange(ExchangeStatus.INITIATED, organizationOwned))

        val memberships = mock<OrganizationMembershipService>()
        whenever(memberships.isMember(any(), any())).thenReturn(callerIsOrgMember)

        return ExchangeFieldBindingPolicy(exchangeRepository, memberships)
    }

    private fun adapter(status: ExchangeStatus): ExchangeFieldResourceAdapter
    {
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange(status))
        return ExchangeFieldResourceAdapter(
            exchangeRepository = exchangeRepository,
            authorizationService = mock<AuthorizationService>(),
            bindingPolicy = ExchangeFieldBindingPolicy(exchangeRepository, mock()),
        )
    }

    private fun binding(classification: FieldDataClassification) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = UUID.randomUUID()
        fieldContractId = UUID.randomUUID()
        visibility = classification
    }

    private fun access(caller: FieldsAccessContext, binding: SchemaFieldBinding) = FieldBindingAccess(
        resource = FieldsResourceRef("EXCHANGE", exchangeId),
        access = caller,
        valueSet = FieldValueSetRef.Root,
        binding = binding,
        operation = FieldValueOperation.READ,
    )
}
