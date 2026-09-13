package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Turns the authenticated caller of the current request into the explicit [FieldsAccessContext] a
 * Fields command carries.
 *
 * The surface that received the request resolves the caller here, once, and passes the result into
 * the command. That keeps the resolution of who is asking at the edge, where the request lives, and
 * leaves the Fields engine with nothing to read for itself.
 */
@ApplicationScoped
class FieldsAccessContextFactory @Inject constructor(
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    /** @throws ForbiddenException when the request carries no principal to act for. */
    fun current(): FieldsAccessContext
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")
        return FieldsAccessContext(principal, authorizationContextFactory.currentContext())
    }
}
