package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * A Schema is written for one kind of resource, and for as long as there was only one kind to write
 * it for, that kind was a literal wherever the question came up. These tests hold what a target is
 * once there is more than one: a code declared in one place, that an unknown value fails closed
 * against, and that a resource has to be named by before an adapter may govern it.
 */
class SchemaTargetRegistryTest
{
    private val registry = SchemaTargetRegistry()

    @Test
    fun `a schema may be written for an exchange or for an information request`()
    {
        assertEquals(
            setOf(ResourceType.EXCHANGE.name, "INFORMATION_REQUEST"),
            registry.supportedTargets(),
        )
    }

    @Test
    fun `a target nobody declared is refused`()
    {
        val refusal = assertThrows<FieldValidationException> { registry.requireDeclared("UNDECLARED_RESOURCE") }
        assertTrue(
            refusal.message.orEmpty().contains("UNDECLARED_RESOURCE"),
            "The refusal should name the target it refused: ${refusal.message}",
        )

        assertThrows<FieldValidationException> { registry.resolveRequestedTarget("UNDECLARED_RESOURCE") }
    }

    @Test
    fun `a caller naming no target still means the exchange it always meant`()
    {
        assertEquals(ResourceType.EXCHANGE.name, registry.defaultTarget())
        assertEquals(ResourceType.EXCHANGE.name, registry.resolveRequestedTarget(null))
        assertEquals(ResourceType.EXCHANGE.name, registry.resolveRequestedTarget("   "))
    }

    @Test
    fun `a named target is read as a code rather than as the caller happened to type it`()
    {
        assertEquals("INFORMATION_REQUEST", registry.resolveRequestedTarget("information_request"))
        assertEquals("INFORMATION_REQUEST", registry.resolveRequestedTarget("  Information_Request  "))
    }

    @Test
    fun `an adapter may only govern a resource some schema can be written for`()
    {
        val refusal = assertThrows<IllegalStateException> {
            indexAdapters(listOf(StubFieldResourceAdapter("UNDECLARED_RESOURCE")), registry)
        }
        assertTrue(
            refusal.message.orEmpty().contains("UNDECLARED_RESOURCE"),
            "The refusal should name the resource type it refused: ${refusal.message}",
        )
    }

    @Test
    fun `two adapters may not both claim the same resource`()
    {
        assertThrows<IllegalStateException> {
            indexAdapters(
                listOf(
                    StubFieldResourceAdapter(ResourceType.EXCHANGE.name),
                    StubFieldResourceAdapter(ResourceType.EXCHANGE.name),
                ),
                registry,
            )
        }
    }

    @Test
    fun `a declared target with no adapter installed governs nothing yet`()
    {
        val index = indexAdapters(listOf(StubFieldResourceAdapter(ResourceType.EXCHANGE.name)), registry)

        assertTrue(ResourceType.EXCHANGE.name in index)
        assertFalse(
            "INFORMATION_REQUEST" in index,
            "Declaring a target says a schema may name it, not that a resource exists to carry one",
        )
    }
}

/** An adapter that only answers which resource type it governs; nothing else is asked of it here. */
private class StubFieldResourceAdapter(override val resourceType: String) : FieldResourceAdapter
{
    override val bindingPolicy: FieldBindingPolicy = InternalCallerBindingPolicy

    override fun exists(resourceId: UUID): Boolean = false

    override fun ownerScope(resourceId: UUID): ScopeReference? = null

    override fun subscriptionContext(resourceId: UUID): SubscriptionContext? = null

    override fun authorizeViewFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ) = Unit

    override fun authorizeManageFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ) = Unit

    override fun authorizeManageSchema(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ) = Unit

    override fun valuesEditable(resourceId: UUID): Boolean = false
}
