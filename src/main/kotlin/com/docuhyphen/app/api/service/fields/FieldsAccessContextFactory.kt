package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class FieldsAccessContextFactory @Inject constructor(
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    fun current(): FieldsAccessContext
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")
        return FieldsAccessContext(principal, authorizationContextFactory.currentContext())
    }
}
