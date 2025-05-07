package com.dochyphen.app.api.service.auth

import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.AppUserRole.ORG_ADMIN
import com.dochyphen.app.api.model.entity.AppUserRole.ORG_MEMBER
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ServiceActionAuthorizationService
{
    fun validateAppUserPhoneNumberModification(appUser: AppUser)
    {
        with(appUser) {

            if(role == ORG_ADMIN || role == ORG_MEMBER)
            {
                return;
            }

            throw UnauthorizedException(
                "User with role $role cannot add a phone number addition"
            )
        }
    }
    fun validateAppUserEmailModification(appUser: AppUser)
    {
        with(appUser) {

            if(role == ORG_ADMIN || role == ORG_MEMBER)
            {
                return;
            }

            throw UnauthorizedException(
                "User with role $role cannot add a phone number addition"
            )
        }
    }
}